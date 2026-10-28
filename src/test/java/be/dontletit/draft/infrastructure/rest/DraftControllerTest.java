package be.dontletit.draft.infrastructure.rest;

import be.dontletit.draft.infrastructure.data.CardDto;
import be.dontletit.draft.infrastructure.data.CollectionCreatedDto;
import be.dontletit.draft.infrastructure.data.CollectionSummaryDto;
import be.dontletit.draft.infrastructure.data.MtgoDatabaseClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
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

    private String startRandomDraft() {
        return given().contentType("application/json").body("{\"source\": \"random\"}")
                .when().post("/drafts")
                .then().statusCode(201)
                .header("Location", containsString("/drafts/"))
                .body("source", equalTo("random"))
                .body("round", equalTo(1))
                .body("pickNumber", equalTo(1))
                .body("direction", equalTo("LEFT"))
                .body("finished", equalTo(false))
                .body("pack.size()", equalTo(15))
                .body("pack[0].name", startsWith("Random "))
                .body("pack[0].imageUrl", startsWith("https://img/"))
                .body("picks.size()", equalTo(0))
                .extract().path("id");
    }

    @Test
    void startRandomDraft_pick_andGet() {
        String id = startRandomDraft();
        String choice = given().when().get("/drafts/" + id).then().statusCode(200).extract().path("pack[2].name");

        given().contentType("application/json").body("{\"cardName\": \"" + choice + "\"}")
                .when().post("/drafts/" + id + "/picks")
                .then().statusCode(200)
                .body("pickNumber", equalTo(2))
                .body("pack.size()", equalTo(14))
                .body("picks[0].name", equalTo(choice));

        given().when().get("/drafts/" + id).then().statusCode(200).body("picks.size()", equalTo(1));
    }

    @Test
    void pick_cardNotInPack_is400_andUnknownDraft_is404() {
        String id = startRandomDraft();
        given().contentType("application/json").body("{\"cardName\": \"Not Here\"}")
                .when().post("/drafts/" + id + "/picks")
                .then().statusCode(400).body("error", containsString("Not Here"));
        given().contentType("application/json").body("{\"cardName\": \"x\"}")
                .when().post("/drafts/nope/picks")
                .then().statusCode(404);
        given().when().get("/drafts/nope").then().statusCode(404);
    }

    @Test
    void startCubeDraft_fromACollection_andErrors() {
        when(database.collectionCards("cube-1")).thenReturn(cards("Cube", 360));
        when(database.collectionCards("small")).thenReturn(cards("Cube", 40));
        when(database.collectionCards("nope")).thenThrow(new WebApplicationException(404));

        given().contentType("application/json").body("{\"source\": \"cube\", \"collectionId\": \"cube-1\"}")
                .when().post("/drafts")
                .then().statusCode(201)
                .body("source", equalTo("cube:cube-1"))
                .body("pack[0].name", startsWith("Cube "));
        given().contentType("application/json").body("{\"source\": \"cube\", \"collectionId\": \"small\"}")
                .when().post("/drafts")
                .then().statusCode(400).body("error", containsString("360"));
        given().contentType("application/json").body("{\"source\": \"cube\", \"collectionId\": \"nope\"}")
                .when().post("/drafts")
                .then().statusCode(404);
        given().contentType("application/json").body("{\"source\": \"binder\"}")
                .when().post("/drafts")
                .then().statusCode(400);
    }

    @Test
    void sources_listTheCubes() {
        when(database.collections()).thenReturn(List.of(new CollectionSummaryDto("c1", "Power LSV Cube", "CUBE", 540), new CollectionSummaryDto("d1", "Burn", "DECK", 60)));

        given().when().get("/drafts/sources")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo("c1"))
                .body("[0].name", equalTo("Power LSV Cube"))
                .body("[0].cardCount", equalTo(540));
    }

    @Test
    void saveDeck_createsACollectionFromThePicks() {
        when(database.createCollection(any())).thenReturn(new CollectionCreatedDto("deck-1"));
        String id = startRandomDraft();
        String choice = given().when().get("/drafts/" + id).then().extract().path("pack[0].name");
        given().contentType("application/json").body("{\"cardName\": \"" + choice + "\"}").when().post("/drafts/" + id + "/picks").then().statusCode(200);

        given().contentType("application/json").body("{\"name\": \"Draft deck\"}")
                .when().post("/drafts/" + id + "/deck")
                .then().statusCode(201)
                .body("collectionId", equalTo("deck-1"))
                .body("name", equalTo("Draft deck"))
                .body("cardCount", equalTo(1));

        given().contentType("application/json").body("{\"name\": \" \"}")
                .when().post("/drafts/" + id + "/deck")
                .then().statusCode(400);
    }

    @Test
    void upstreamUnreachable_is502() {
        when(database.randomCards(anyInt())).thenThrow(new ProcessingException("connection refused"));
        given().contentType("application/json").body("{\"source\": \"random\"}")
                .when().post("/drafts")
                .then().statusCode(502);
    }
}
