package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftAction;
import be.dontletit.draft.api.DraftCardView;
import be.dontletit.draft.api.DraftEventView;
import be.dontletit.draft.api.DraftFormatView;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.SavedDeckView;
import be.dontletit.draft.api.SeatView;
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

    static List<String> names(List<DraftCardView> cards) {
        return cards.stream().map(DraftCardView::name).toList();
    }

    @Test
    void listFormats_classicRochesterWinston() {
        List<DraftFormatView> formats = adapter.listFormats();

        assertEquals(List.of("CLASSIC", "ROCHESTER", "WINSTON"), formats.stream().map(DraftFormatView::name).toList());
        assertEquals("Winston", formats.get(2).displayName());
        assertEquals(2, formats.get(2).seats());
        assertEquals(90, formats.get(2).cardsNeeded());
        assertEquals(360, formats.get(1).cardsNeeded());
        assertFalse(formats.get(0).description().isBlank());
    }

    @Test
    void startRandomDraft_classicByDefault_draws360CardsAndOpensTheFirstPack() {
        DraftView view = adapter.startRandomDraft(null);

        assertEquals(List.of(360), pool.randomRequests);
        assertNotNull(view.id());
        assertEquals("random", view.source());
        assertEquals("CLASSIC", view.format());
        assertEquals(0, view.seat());
        assertTrue(view.yourTurn());
        assertEquals(1, view.round());
        assertEquals(1, view.pickNumber());
        assertEquals("LEFT", view.direction());
        assertEquals(15, view.pack().size());
        assertTrue(view.picks().isEmpty());
        assertFalse(view.finished());
        assertNull(view.winston());
        assertNull(view.activeSeat());
        assertEquals(8, view.seats().size());
        assertEquals(new SeatView(0, true, 0), view.seats().get(0));
        assertTrue(store.find(view.id()).isPresent());
    }

    @Test
    void startRandomDraft_unknownFormat_rejectedWithTheKnownOnes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> adapter.startRandomDraft("sealed"));
        assertTrue(e.getMessage().contains("WINSTON"), e.getMessage());
        assertTrue(pool.randomRequests.isEmpty());
    }

    @Test
    void classicPick_advancesTheDraft_andHidesTheBotsPicks() {
        DraftView start = adapter.startRandomDraft("CLASSIC");
        String choice = start.pack().get(4).name();

        DraftView after = adapter.pick(start.id(), choice).orElseThrow();

        assertEquals(2, after.pickNumber());
        assertEquals(14, after.pack().size());
        assertEquals(List.of(choice), names(after.picks()));
        assertTrue(after.yourTurn());
        assertEquals(4, after.seats().get(3).pickCount(), "bots pick as soon as a pack reaches them: seat 3 had the packs of seats 3, 2, 1 and 0");
        assertTrue(after.log().stream().filter(e -> e.type().equals("CARD_PICKED")).allMatch(e -> e.seat() == 0));
        assertEquals(after, adapter.getDraft(start.id()).orElseThrow());
    }

    @Test
    void rochester_botsPickUntilItIsThePlayersTurn_andTheirPicksAreVisible() {
        DraftView start = adapter.startRandomDraft("rochester");
        assertEquals("ROCHESTER", start.format());
        assertEquals(15, start.pack().size());
        assertEquals(1, start.packNumber());
        assertEquals(0, start.opener());
        assertEquals(0, start.activeSeat());
        assertTrue(start.yourTurn());

        DraftView after = adapter.pick(start.id(), start.pack().get(0).name()).orElseThrow();

        assertEquals(0, after.activeSeat());
        assertTrue(after.yourTurn());
        assertEquals(2, after.packNumber(), "the bots finished the first booster, seat 1 opened the second");
        assertEquals(1, after.opener());
        assertEquals(8, after.pickNumber());
        assertEquals(8, after.pack().size());
        List<DraftEventView> botPicks = after.log().stream().filter(e -> e.type().equals("CARD_PICKED") && e.seat() != 0).toList();
        assertFalse(botPicks.isEmpty());
        assertTrue(botPicks.stream().allMatch(e -> e.cards().size() == 1), "face-up picks show the card");
        assertTrue(after.log().stream().anyMatch(e -> e.type().equals("PACK_OPENED") && e.seat() == 1));
    }

    @Test
    void winston_takeAndPassPiles() {
        DraftView start = adapter.startRandomDraft("WINSTON");
        assertEquals(List.of(90), pool.randomRequests);
        assertEquals("WINSTON", start.format());
        assertTrue(start.yourTurn());
        assertNull(start.round());
        assertEquals(2, start.seats().size());
        assertEquals(0, start.winston().currentPile());
        assertEquals(start.winston().pileSizes().get(0), start.pack().size(), "the player sees the pile they look at");

        DraftView passed = adapter.act(start.id(), new DraftAction("PASS_PILE", null)).orElseThrow();
        assertEquals(1, passed.winston().currentPile());
        assertTrue(passed.picks().isEmpty());

        DraftView taken = adapter.act(start.id(), new DraftAction("take_pile", null)).orElseThrow();
        assertFalse(taken.picks().isEmpty());
        assertTrue(taken.yourTurn(), "the opponent played its turn");
        assertTrue(taken.log().stream().anyMatch(e -> e.seat() != null && e.seat() == 1 && e.type().startsWith("PILE_")
                || e.type().equals("STACK_CARD_TAKEN")));
    }

    @Test
    void act_wrongActionForTheFormat_orUnknownAction_rejected() {
        DraftView winston = adapter.startRandomDraft("WINSTON");
        DraftView classic = adapter.startRandomDraft("CLASSIC");

        assertThrows(IllegalArgumentException.class, () -> adapter.pick(winston.id(), "Random 1"));
        assertThrows(IllegalArgumentException.class, () -> adapter.act(classic.id(), new DraftAction("TAKE_PILE", null)));
        assertThrows(IllegalArgumentException.class, () -> adapter.act(classic.id(), new DraftAction("SHUFFLE", null)));
        assertThrows(IllegalArgumentException.class, () -> adapter.act(classic.id(), null));
    }

    @Test
    void act_untilTheEnd_finishesTheDraft() {
        DraftView view = adapter.startRandomDraft("WINSTON");
        while (!view.finished()) view = adapter.act(view.id(), new DraftAction("TAKE_PILE", null)).orElseThrow();

        assertFalse(view.yourTurn());
        assertTrue(view.pack().isEmpty());
        assertEquals("DRAFT_FINISHED", view.log().get(view.log().size() - 1).type());
        String id = view.id();
        assertThrows(IllegalStateException.class, () -> adapter.act(id, new DraftAction("TAKE_PILE", null)));
    }

    @Test
    void startCubeDraft_usesTheCollectionCards_andChecksTheSizeForTheFormat() {
        pool.collections.put("cube-1", CardPoolSpiStub.cards("Cube", 100));

        DraftView view = adapter.startCubeDraft("WINSTON", "cube-1");

        assertEquals("cube:cube-1", view.source());
        assertTrue(view.pack().get(0).name().startsWith("Cube "));
        assertTrue(pool.randomRequests.isEmpty());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> adapter.startCubeDraft("CLASSIC", "cube-1"));
        assertTrue(e.getMessage().contains("360"));
        assertTrue(e.getMessage().contains("100"));
    }

    @Test
    void startCubeDraft_unknownCollection_notFound() {
        assertThrows(NoSuchElementException.class, () -> adapter.startCubeDraft(null, "nope"));
    }

    @Test
    void unknownDraft_isEmpty() {
        assertTrue(adapter.getDraft("nope").isEmpty());
        assertTrue(adapter.pick("nope", "x").isEmpty());
        assertTrue(adapter.saveDeck("nope", "x").isEmpty());
    }

    @Test
    void pick_badCard_rejected() {
        DraftView start = adapter.startRandomDraft(null);
        assertThrows(IllegalArgumentException.class, () -> adapter.pick(start.id(), "Not In Pack"));
    }

    @Test
    void listCubes_passesThrough() {
        pool.cubes.add(new CubeInfo("cube-1", "Power LSV Cube", 540));
        assertEquals(1, adapter.listCubes().size());
        assertEquals("Power LSV Cube", adapter.listCubes().get(0).name());
        assertEquals(540, adapter.listCubes().get(0).cardCount());
    }

    @Test
    void saveDeck_storesThePlayersPicksAsADeckCollection() {
        DraftView start = adapter.startRandomDraft(null);
        DraftView current = start;
        for (int i = 0; i < 3; i++) current = adapter.pick(start.id(), current.pack().get(0).name()).orElseThrow();

        SavedDeckView saved = adapter.saveDeck(start.id(), "My draft deck").orElseThrow();

        assertEquals("deck-1", saved.collectionId());
        assertEquals("My draft deck", saved.name());
        assertEquals(3, saved.cardCount());
        assertEquals("My draft deck", pool.savedDeckName);
        assertEquals(names(current.picks()), pool.savedDeckCards);
    }

    @Test
    void saveDeck_blankNameOrNoPicks_rejected() {
        DraftView start = adapter.startRandomDraft(null);
        assertThrows(IllegalArgumentException.class, () -> adapter.saveDeck(start.id(), " "));
        assertThrows(IllegalStateException.class, () -> adapter.saveDeck(start.id(), "Empty"));
    }
}
