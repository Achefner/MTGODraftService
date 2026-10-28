package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.SavedDeckView;
import be.dontletit.draft.infrastructure.data.InMemoryDraftStore;
import be.dontletit.draft.spi.CubeInfo;
import be.dontletit.draft.stubs.CardPoolSpiStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DraftApiAdapterTest {

    CardPoolSpiStub pool;
    InMemoryDraftStore store;
    DraftApiAdapter adapter;

    @BeforeEach
    void setUp() {
        pool = new CardPoolSpiStub();
        store = new InMemoryDraftStore();
        adapter = new DraftApiAdapter(pool, store, () -> new Random(1L));
    }

    @Test
    void startRandomDraft_draws360RandomCardsAndOpensTheFirstPack() {
        DraftView view = adapter.startRandomDraft();

        assertEquals(List.of(360), pool.randomRequests);
        assertNotNull(view.id());
        assertEquals("random", view.source());
        assertEquals(1, view.round());
        assertEquals(1, view.pickNumber());
        assertEquals("LEFT", view.direction());
        assertEquals(15, view.pack().size());
        assertEquals("https://img/Random0.jpg", pool.randomCards(1).get(0).imageUrl());
        assertTrue(view.picks().isEmpty());
        assertFalse(view.finished());
        assertTrue(store.find(view.id()).isPresent());
    }

    @Test
    void startCubeDraft_usesTheCollectionCards() {
        pool.collections.put("cube-1", CardPoolSpiStub.cards("Cube", 540));

        DraftView view = adapter.startCubeDraft("cube-1");

        assertEquals("cube:cube-1", view.source());
        assertEquals(15, view.pack().size());
        assertTrue(view.pack().get(0).name().startsWith("Cube "));
        assertTrue(pool.randomRequests.isEmpty());
    }

    @Test
    void startCubeDraft_unknownCollection_notFound() {
        assertThrows(NoSuchElementException.class, () -> adapter.startCubeDraft("nope"));
    }

    @Test
    void startCubeDraft_tooSmall_rejectedWithCounts() {
        pool.collections.put("small", CardPoolSpiStub.cards("Cube", 100));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> adapter.startCubeDraft("small"));

        assertTrue(e.getMessage().contains("360"));
        assertTrue(e.getMessage().contains("100"));
    }

    @Test
    void pick_advancesTheDraft() {
        DraftView start = adapter.startRandomDraft();
        String choice = start.pack().get(4).name();

        DraftView after = adapter.pick(start.id(), choice).orElseThrow();

        assertEquals(2, after.pickNumber());
        assertEquals(14, after.pack().size());
        assertEquals(List.of(choice), after.picks().stream().map(c -> c.name()).toList());
        assertEquals(after, adapter.getDraft(start.id()).orElseThrow());
    }

    @Test
    void pick_unknownDraft_empty_andBadCard_rejected() {
        assertTrue(adapter.pick("nope", "x").isEmpty());
        DraftView start = adapter.startRandomDraft();
        assertThrows(IllegalArgumentException.class, () -> adapter.pick(start.id(), "Not In Pack"));
    }

    @Test
    void getDraft_unknown_isEmpty() {
        assertTrue(adapter.getDraft("nope").isEmpty());
    }

    @Test
    void listCubes_passesThrough() {
        pool.cubes.add(new CubeInfo("cube-1", "Power LSV Cube", 540));
        assertEquals(1, adapter.listCubes().size());
        assertEquals("Power LSV Cube", adapter.listCubes().get(0).name());
        assertEquals(540, adapter.listCubes().get(0).cardCount());
    }

    @Test
    void saveDeck_storesTheHumanPicksAsADeckCollection() {
        DraftView start = adapter.startRandomDraft();
        DraftView current = start;
        for (int i = 0; i < 3; i++) current = adapter.pick(start.id(), current.pack().get(0).name()).orElseThrow();

        SavedDeckView saved = adapter.saveDeck(start.id(), "My draft deck").orElseThrow();

        assertEquals("deck-1", saved.collectionId());
        assertEquals("My draft deck", saved.name());
        assertEquals(3, saved.cardCount());
        assertEquals("My draft deck", pool.savedDeckName);
        assertEquals(current.picks().stream().map(c -> c.name()).toList(), pool.savedDeckCards);
    }

    @Test
    void saveDeck_blankNameOrNoPicks_rejected_unknownDraft_empty() {
        DraftView start = adapter.startRandomDraft();
        assertThrows(IllegalArgumentException.class, () -> adapter.saveDeck(start.id(), " "));
        assertThrows(IllegalStateException.class, () -> adapter.saveDeck(start.id(), "Empty"));
        assertTrue(adapter.saveDeck("nope", "x").isEmpty());
    }
}
