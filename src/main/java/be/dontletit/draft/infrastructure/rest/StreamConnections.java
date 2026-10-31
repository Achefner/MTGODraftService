package be.dontletit.draft.infrastructure.rest;

import be.dontletit.draft.api.DraftUpdate;
import be.dontletit.draft.api.Subscription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Open server-sent event streams. Each update goes out as an "update" event whose id is the draft
 * version, so a browser that reconnects sends it back as Last-Event-ID. A comment line goes out
 * every few seconds: it keeps idle connections open and reveals the dead ones, which are closed.
 */
@ApplicationScoped
public class StreamConnections {

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "draft.stream.heartbeat-seconds")
    int heartbeatSeconds;

    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private ScheduledExecutorService heartbeat;

    /** One open stream of one seat. */
    public final class Connection {
        private final SseEventSink sink;
        private final Sse sse;
        private volatile Subscription subscription;
        private volatile boolean closed;

        Connection(SseEventSink sink, Sse sse) {
            this.sink = sink;
            this.sse = sse;
        }

        /** Called by the draft service; throwing tells it the connection is gone. */
        public void send(DraftUpdate update) {
            if (closed || sink.isClosed()) throw new IllegalStateException("The stream is closed");
            String json;
            try {
                json = mapper.writeValueAsString(update);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Cannot write the update", e);
            }
            sink.send(sse.newEventBuilder().id(Long.toString(update.version())).name("update")
                            .mediaType(MediaType.APPLICATION_JSON_TYPE).data(String.class, json).build())
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) close();
                    });
        }

        void attach(Subscription subscription) {
            this.subscription = subscription;
            if (closed) subscription.close();
        }

        void ping() {
            if (sink.isClosed()) {
                close();
                return;
            }
            sink.send(sse.newEventBuilder().comment("ping").build()).whenComplete((ignored, failure) -> {
                if (failure != null) close();
            });
        }

        void close() {
            if (closed) return;
            closed = true;
            connections.remove(this);
            Subscription current = subscription;
            if (current != null) current.close();
            if (!sink.isClosed()) sink.close();
        }
    }

    public Connection open(SseEventSink sink, Sse sse) {
        startHeartbeat();
        Connection connection = new Connection(sink, sse);
        connections.add(connection);
        return connection;
    }

    private synchronized void startHeartbeat() {
        if (heartbeat != null) return;
        heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "draft-stream-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeat.scheduleAtFixedRate(() -> connections.forEach(connection -> {
            try {
                connection.ping();
            } catch (RuntimeException e) {
                connection.close();
            }
        }), heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    synchronized void stop() {
        if (heartbeat != null) heartbeat.shutdownNow();
        connections.forEach(Connection::close);
    }
}
