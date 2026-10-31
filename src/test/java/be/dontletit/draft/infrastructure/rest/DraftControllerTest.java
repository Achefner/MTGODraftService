package be.dontletit.draft.infrastructure.rest;

import be.dontletit.draft.infrastructure.data.CardDto;
import be.dontletit.draft.infrastructure.data.CollectionCreatedDto;
import be.dontletit.draft.infrastructure.data.CollectionSummaryDto;
import be.dontletit.draft.infrastructure.data.MtgoDatabaseClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@QuarkusTest
class DraftControllerTest {

    static final String TOKEN = DraftController.TOKEN_HEADER;

    @InjectMock
    @RestClient
    MtgoDatabaseClient database;

    static List<CardDto> cards(String prefix, int n) {
        return IntStream.range(0, n).mapToObj(i -> new CardDto(prefix + " " + i, "https://img/" + i + ".jpg")).toList();
    }

    @BeforeEach
    void upstream() {
        when(database.randomCards(anyInt())).thenAnswer(inv -> cards("Random", inv.getArgument(0)));
    }

    static JsonPath open(String body) {
        return given().contentType("application/json").body(body)
                .when().post("/drafts")
                .then().statusCode(201)
                .header("Location", containsString("/drafts/"))
                .extract().jsonPath();
    }

    @Test
    void solo_openPlayAndGet_withTheSeatToken() {
        JsonPath ticket = open("{\"source\": \"random\", \"playerName\": \"Amir\"}");
        String id = ticket.getString("draftId");
        String token = ticket.getString("token");
        org.junit.jupiter.api.Assertions.assertEquals(0, ticket.getInt("seat"));
        org.junit.jupiter.api.Assertions.assertEquals("RUNNING", ticket.getString("view.status"));
        org.junit.jupiter.api.Assertions.assertEquals(75, ticket.getInt("view.timer.seconds"), "timed by default");
        String choice = ticket.getString("view.pack[2].name");

        given().contentType("application/json").header(TOKEN, token).body("{\"type\": \"PICK\", \"cardName\": \"" + choice + "\", \"expectedMoves\": 0}")
                .when().post("/drafts/" + id + "/actions")
                .then().statusCode(200)
                .body("moves", equalTo(1))
                .body("pickNumber", equalTo(2))
                .body("picks[0].name", equalTo(choice));

        given().header(TOKEN, token).when().get("/drafts/" + id)
                .then().statusCode(200)
                .body("picks.size()", equalTo(1))
                .body("seats[0].name", equalTo("Amir"))
                .body("seats[1].kind", equalTo("BOT"));
    }

    @Test
    void tokens_missingOrWrongAre403_unknownDraftIs404_staleActionIs409() {
        JsonPath ticket = open("{\"source\": \"random\", \"timed\": false}");
        String id = ticket.getString("draftId");
        String token = ticket.getString("token");

        given().when().get("/drafts/" + id).then().statusCode(403).body("error", containsString("token"));
        given().header(TOKEN, "wrong").when().get("/drafts/" + id).then().statusCode(403);
        given().header(TOKEN, token).when().get("/drafts/nope").then().statusCode(404);
        given().contentType("application/json").header(TOKEN, token).body("{\"type\": \"PICK\", \"cardName\": \"Not Here\"}")
                .when().post("/drafts/" + id + "/actions")
                .then().statusCode(400).body("error", containsString("Not Here"));
        given().contentType("application/json").header(TOKEN, token).body("{\"type\": \"PICK\", \"cardName\": \"x\", \"expectedMoves\": 4}")
                .when().post("/drafts/" + id + "/actions")
                .then().statusCode(409).body("error", containsString("out of date"));
    }

    @Test
    void lobby_previewJoinAndStart() {
        JsonPath host = open("{\"source\": \"random\", \"format\": \"WINSTON\", \"playerName\": \"Host\", \"startNow\": false}");
        String id = host.getString("draftId");
        org.junit.jupiter.api.Assertions.assertEquals("LOBBY", host.getString("view.status"));

        given().when().get("/drafts/" + id + "/table")
                .then().statusCode(200)
                .body("format", equalTo("WINSTON"))
                .body("status", equalTo("LOBBY"))
                .body("seats[0].name", equalTo("Host"))
                .body("seats[1].kind", equalTo("OPEN"));

        String guestToken = given().contentType("application/json").body("{\"playerName\": \"Guest\"}")
                .when().post("/drafts/" + id + "/seats")
                .then().statusCode(201)
                .body("seat", equalTo(1))
                .extract().path("token");
        given().contentType("application/json").body("{\"playerName\": \"Third\"}")
                .when().post("/drafts/" + id + "/seats")
                .then().statusCode(409).body("error", containsString("full"));

        given().header(TOKEN, guestToken).when().post("/drafts/" + id + "/start").then().statusCode(403);
        given().header(TOKEN, host.getString("token")).when().post("/drafts/" + id + "/start")
                .then().statusCode(200).body("status", equalTo("RUNNING"));
        given().header(TOKEN, guestToken).when().get("/drafts/" + id)
                .then().statusCode(200).body("seats[1].you", equalTo(true));
    }

    @Test
    void formats_andCubeSources() {
        when(database.collections()).thenReturn(List.of(new CollectionSummaryDto("c1", "Power LSV Cube", "CUBE", 540), new CollectionSummaryDto("d1", "Burn", "DECK", 60)));

        given().when().get("/drafts/formats")
                .then().statusCode(200)
                .body("name", contains("CLASSIC", "ROCHESTER", "WINSTON"))
                .body("[2].cardsNeeded", equalTo(90));
        given().when().get("/drafts/sources")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].name", equalTo("Power LSV Cube"));
    }

    @Test
    void cubeTables_andTheirErrors() {
        when(database.collectionCards("cube-1")).thenReturn(cards("Cube", 360));
        when(database.collectionCards("small")).thenReturn(cards("Cube", 40));
        when(database.collectionCards("nope")).thenThrow(new WebApplicationException(404));

        open("{\"source\": \"cube\", \"collectionId\": \"cube-1\", \"format\": \"ROCHESTER\"}");
        given().contentType("application/json").body("{\"source\": \"cube\", \"collectionId\": \"small\"}")
                .when().post("/drafts").then().statusCode(400).body("error", containsString("360"));
        given().contentType("application/json").body("{\"source\": \"cube\", \"collectionId\": \"nope\"}")
                .when().post("/drafts").then().statusCode(404);
        given().contentType("application/json").body("{\"source\": \"random\", \"format\": \"sealed\"}")
                .when().post("/drafts").then().statusCode(400).body("error", containsString("WINSTON"));
    }

    @Test
    void winstonActions_andAFinishedDraftIs409() {
        JsonPath ticket = open("{\"source\": \"random\", \"format\": \"winston\", \"timed\": false}");
        String id = ticket.getString("draftId");
        String token = ticket.getString("token");

        given().contentType("application/json").header(TOKEN, token).body("{\"type\": \"SHUFFLE\"}")
                .when().post("/drafts/" + id + "/actions").then().statusCode(400);
        boolean finished = false;
        while (!finished) {
            finished = given().contentType("application/json").header(TOKEN, token).body("{\"type\": \"TAKE_PILE\"}")
                    .when().post("/drafts/" + id + "/actions")
                    .then().statusCode(200).extract().path("finished");
        }
        given().contentType("application/json").header(TOKEN, token).body("{\"type\": \"TAKE_PILE\"}")
                .when().post("/drafts/" + id + "/actions").then().statusCode(409);
    }

    @Test
    void saveDeck_createsACollectionFromThePicks() {
        when(database.createCollection(any())).thenReturn(new CollectionCreatedDto("deck-1"));
        JsonPath ticket = open("{\"source\": \"random\"}");
        String id = ticket.getString("draftId");
        String token = ticket.getString("token");
        given().contentType("application/json").header(TOKEN, token).body("{\"type\": \"PICK\", \"cardName\": \"" + ticket.getString("view.pack[0].name") + "\"}")
                .when().post("/drafts/" + id + "/actions").then().statusCode(200);

        given().contentType("application/json").header(TOKEN, token).body("{\"name\": \"Draft deck\"}")
                .when().post("/drafts/" + id + "/deck")
                .then().statusCode(201)
                .body("collectionId", equalTo("deck-1"))
                .body("cardCount", equalTo(1));
    }

    @Test
    void upstreamUnreachable_is502() {
        when(database.randomCards(anyInt())).thenThrow(new ProcessingException("connection refused"));
        given().contentType("application/json").body("{\"source\": \"random\"}")
                .when().post("/drafts").then().statusCode(502);
    }
}
