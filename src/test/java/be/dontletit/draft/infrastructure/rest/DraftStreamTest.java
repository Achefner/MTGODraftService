package be.dontletit.draft.infrastructure.rest;

import be.dontletit.draft.infrastructure.data.CardDto;
import be.dontletit.draft.infrastructure.data.MtgoDatabaseClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/** The live stream of a seat: server-sent events, one "update" per change, comments as heartbeat. */
@QuarkusTest
class DraftStreamTest {

    @InjectMock
    @RestClient
    MtgoDatabaseClient database;

    @TestHTTPResource("/")
    URL base;

    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void upstream() {
        when(database.randomCards(anyInt())).thenAnswer(inv -> IntStream.range(0, (int) inv.getArgument(0))
                .mapToObj(i -> new CardDto("Random " + i, "https://img/" + i + ".jpg")).toList());
    }

    /** An open stream: every server-sent event (or comment) arrives as one block of lines. */
    final class Stream implements AutoCloseable {
        final int status;
        final InputStream body;
        final BlockingQueue<String> blocks = new LinkedBlockingQueue<>();

        Stream(String path, String lastEventId) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path.substring(1))).header("Accept", "text/event-stream");
            if (lastEventId != null) request.header("Last-Event-ID", lastEventId);
            HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            status = response.statusCode();
            body = response.body();
            Thread reader = new Thread(() -> {
                try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    StringBuilder block = new StringBuilder();
                    String line;
                    while ((line = lines.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (!block.isEmpty()) blocks.add(block.toString());
                            block.setLength(0);
                        } else {
                            block.append(line).append('\n');
                        }
                    }
                } catch (Exception closed) {
                    // the test closed the stream
                }
            });
            reader.setDaemon(true);
            reader.start();
        }

        /** The next "update" event, skipping heartbeats: its id and its JSON data. */
        Update nextUpdate() throws InterruptedException {
            while (true) {
                String block = blocks.poll(10, TimeUnit.SECONDS);
                assertNotNull(block, "no update within 10 seconds");
                if (block.startsWith(":")) continue;
                String id = null;
                StringBuilder data = new StringBuilder();
                for (String line : block.split("\n")) {
                    if (line.startsWith("id:")) id = line.substring(3).trim();
                    if (line.startsWith("data:")) data.append(line.substring(5).trim());
                }
                assertTrue(block.contains("event: update") || block.contains("event:update"), block);
                return new Update(id, new JsonPath(data.toString()));
            }
        }

        boolean sawHeartbeatWithin(Duration wait) throws InterruptedException {
            long until = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < until) {
                String block = blocks.poll(100, TimeUnit.MILLISECONDS);
                if (block != null && block.startsWith(":")) return true;
            }
            return false;
        }

        @Override
        public void close() throws Exception {
            body.close();
        }
    }

    record Update(String id, JsonPath json) {}

    static JsonPath open(String body) {
        return given().contentType("application/json").body(body).when().post("/drafts").then().statusCode(201).extract().jsonPath();
    }

    @Test
    void theStreamStartsWithTheView_thenSendsAnUpdateAfterEveryChange() throws Exception {
        JsonPath ticket = open("{\"source\": \"random\", \"format\": \"ROCHESTER\", \"timed\": false}");
        String id = ticket.getString("draftId");
        String token = ticket.getString("token");

        try (Stream stream = new Stream("/drafts/" + id + "/stream?token=" + token, null)) {
            assertEquals(200, stream.status);
            Update first = stream.nextUpdate();
            assertEquals(15, first.json().getList("view.pack").size());
            assertTrue(first.json().getBoolean("view.seats[0].connected"));
            long version = Long.parseLong(first.id());
            assertEquals(version, first.json().getLong("version"));

            given().contentType("application/json").header(DraftController.TOKEN_HEADER, token)
                    .body("{\"type\": \"PICK\", \"cardName\": \"" + ticket.getString("view.pack[0].name") + "\"}")
                    .when().post("/drafts/" + id + "/actions").then().statusCode(200);

            Update next = stream.nextUpdate();
            assertTrue(Long.parseLong(next.id()) > version);
            assertEquals("CARD_PICKED", next.json().getString("events[0].type"));
            assertEquals(1, next.json().getInt("view.moves"));
        }
    }

    @Test
    void reconnectingWithLastEventId_sendsWhatWasMissed() throws Exception {
        JsonPath ticket = open("{\"source\": \"random\", \"format\": \"ROCHESTER\", \"timed\": false}");
        String id = ticket.getString("draftId");
        String token = ticket.getString("token");
        String seen;
        try (Stream stream = new Stream("/drafts/" + id + "/stream?token=" + token, null)) {
            seen = stream.nextUpdate().id();
        }
        given().contentType("application/json").header(DraftController.TOKEN_HEADER, token)
                .body("{\"type\": \"PICK\", \"cardName\": \"" + ticket.getString("view.pack[0].name") + "\"}")
                .when().post("/drafts/" + id + "/actions").then().statusCode(200);

        try (Stream stream = new Stream("/drafts/" + id + "/stream?token=" + token, seen)) {
            List<String> types = stream.nextUpdate().json().getList("events.type");
            assertEquals("CARD_PICKED", types.get(0));
            assertTrue(types.size() > 10, "the bots' picks since then are included");
        }
    }

    @Test
    void heartbeats_keepTheStreamAlive_andAClosedStreamShowsAsDisconnected() throws Exception {
        JsonPath ticket = open("{\"source\": \"random\", \"timed\": false}");
        String id = ticket.getString("draftId");
        String token = ticket.getString("token");

        try (Stream stream = new Stream("/drafts/" + id + "/stream?token=" + token, null)) {
            stream.nextUpdate();
            assertTrue(stream.sawHeartbeatWithin(Duration.ofSeconds(3)));
        }

        long until = System.currentTimeMillis() + 10_000;
        boolean connected = true;
        while (connected && System.currentTimeMillis() < until) {
            Thread.sleep(200);
            connected = given().header(DraftController.TOKEN_HEADER, token).when().get("/drafts/" + id)
                    .then().statusCode(200).extract().path("seats[0].connected");
        }
        assertFalse(connected, "the heartbeat finds the closed connection");
    }

    @Test
    void aWrongToken_isRefusedBeforeStreaming() throws Exception {
        JsonPath ticket = open("{\"source\": \"random\", \"timed\": false}");
        try (Stream stream = new Stream("/drafts/" + ticket.getString("draftId") + "/stream?token=wrong", null)) {
            assertEquals(403, stream.status);
        }
        try (Stream stream = new Stream("/drafts/nope/stream?token=x", null)) {
            assertEquals(404, stream.status);
        }
    }
}
