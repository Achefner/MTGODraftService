package be.dontletit.draft.infrastructure.timer;

import be.dontletit.draft.spi.TimerSpiPort;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Pick clocks on the wall clock, run by one background thread. */
@ApplicationScoped
public class SystemTimer implements TimerSpiPort {

    private static final Logger LOG = Logger.getLogger(SystemTimer.class);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "draft-timer");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public Cancellable schedule(Duration delay, Runnable task) {
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.warn("A pick timer task failed", e);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
