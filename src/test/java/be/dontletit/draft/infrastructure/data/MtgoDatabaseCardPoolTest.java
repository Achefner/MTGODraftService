package be.dontletit.draft.infrastructure.data;

import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.spi.CubeInfo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class MtgoDatabaseCardPoolTest {

    @InjectMock
    @RestClient
    MtgoDatabaseClient database;

    @Inject
    MtgoDatabaseCardPool pool;

    @Test
    void randomCards_mapsNameAndImage() {
        when(database.randomCards(2)).thenReturn(List.of(new CardDto("Black Lotus", "https://img/lotus.jpg"), new CardDto("Sol Ring", null)));

        List<DraftCard> cards = pool.randomCards(2);

        assertEquals(List.of(new DraftCard("Black Lotus", "https://img/lotus.jpg"), new DraftCard("Sol Ring", null)), cards);
    }

    @Test
    void collectionCards_found_andNotFound() {
        when(database.collectionCards("c1")).thenReturn(List.of(new CardDto("Black Lotus", "https://img/lotus.jpg")));
        when(database.collectionCards("nope")).thenThrow(new WebApplicationException(404));

        assertEquals(1, pool.collectionCards("c1").orElseThrow().size());
        assertTrue(pool.collectionCards("nope").isEmpty());
    }

    @Test
    void listCubes_keepsOnlyCubeCollections() {
        when(database.collections()).thenReturn(List.of(
                new CollectionSummaryDto("c1", "Power LSV Cube", "CUBE", 540),
                new CollectionSummaryDto("d1", "Burn", "DECK", 60)));

        assertEquals(List.of(new CubeInfo("c1", "Power LSV Cube", 540)), pool.listCubes());
    }

    @Test
    void saveDeck_postsADeckCollectionAndReturnsItsId() {
        when(database.createCollection(org.mockito.ArgumentMatchers.any())).thenReturn(new CollectionCreatedDto("deck-9"));

        String id = pool.saveDeck("My deck", List.of("Black Lotus", "Sol Ring"));

        assertEquals("deck-9", id);
        ArgumentCaptor<CreateCollectionDto> body = ArgumentCaptor.forClass(CreateCollectionDto.class);
        verify(database).createCollection(body.capture());
        assertEquals("My deck", body.getValue().name());
        assertEquals("DECK", body.getValue().type());
        assertEquals(List.of(new CollectionEntryDto("Black Lotus", 1), new CollectionEntryDto("Sol Ring", 1)), body.getValue().entries());
    }
}
