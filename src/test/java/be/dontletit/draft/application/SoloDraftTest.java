package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftAction;
import be.dontletit.draft.api.DraftEventView;
import be.dontletit.draft.api.DraftFormatView;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.OpenTableRequest;
import be.dontletit.draft.api.SavedDeckView;
import be.dontletit.draft.api.SeatAccessDeniedException;
import be.dontletit.draft.api.SeatTicket;
import be.dontletit.draft.api.SeatView;
import be.dontletit.draft.spi.CubeInfo;
import be.dontletit.draft.stubs.CardPoolSpiStub;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/** One person against bots, the draft starting at once. */
class SoloDraftTest extends AdapterFixture {

    @Test
    void listFormats_classicRochesterWinston() {
        List<DraftFormatView> formats = adapter.listFormats();

        assertEquals(List.of("CLASSIC", "ROCHESTER", "WINSTON"), formats.stream().map(DraftFormatView::name).toList());
        assertEquals(2, formats.get(2).seats());
        assertEquals(90, formats.get(2).cardsNeeded());
        assertFalse(formats.get(0).description().isBlank());
    }

    @Test
    void solo_classicByDefault_seatsThePlayerAndBots() {
        SeatTicket ticket = adapter.openTable(new OpenTableRequest("random", null, null, "Amir", false, true));
        DraftView view = ticket.view();

        assertEquals(List.of(360), pool.randomRequests);
        assertEquals(0, ticket.seat());
        assertTrue(ticket.token().length() >= 32);
        assertEquals(ticket.draftId(), view.id());
        assertEquals("random", view.source());
        assertEquals("CLASSIC", view.format());
        assertEquals("RUNNING", view.status());
        assertTrue(view.host());
        assertTrue(view.yourTurn());
        assertEquals(0, view.moves());
        assertNull(view.timer());
        assertEquals(1, view.round());
        assertEquals(1, view.pickNumber());
        assertEquals("LEFT", view.direction());
        assertEquals(15, view.pack().size());
        assertEquals(new SeatView(0, true, "Amir", "HUMAN", false, 0), view.seats().get(0));
        assertEquals("BOT", view.seats().get(5).kind());
    }

    @Test
    void openTable_badInput_rejected() {
        assertThrows(IllegalArgumentException.class, () -> adapter.openTable(new OpenTableRequest("random", null, "sealed", "A", false, true)));
        assertThrows(IllegalArgumentException.class, () -> adapter.openTable(new OpenTableRequest(null, null, null, "A", false, true)));
        assertThrows(IllegalArgumentException.class, () -> adapter.openTable(new OpenTableRequest("binder", null, null, "A", false, true)));
        assertThrows(IllegalArgumentException.class, () -> adapter.openTable(new OpenTableRequest("cube", " ", null, "A", false, true)));
        assertThrows(NoSuchElementException.class, () -> adapter.openTable(new OpenTableRequest("cube", "nope", null, "A", false, true)));
        assertThrows(IllegalArgumentException.class, () -> adapter.openTable(null));
    }

    @Test
    void openTable_blankName_becomesPlayerAndTheSeatNumber() {
        SeatTicket ticket = adapter.openTable(new OpenTableRequest("random", null, null, "  ", false, true));
        assertEquals("Player 1", ticket.view().seats().get(0).name());
    }

    @Test
    void cube_usesTheCollection_andChecksItsSizeForTheFormat() {
        pool.collections.put("cube-1", CardPoolSpiStub.cards("Cube", 100));

        SeatTicket winston = adapter.openTable(new OpenTableRequest("cube", "cube-1", "WINSTON", "A", false, true));

        assertEquals("cube:cube-1", winston.view().source());
        assertTrue(winston.view().pack().get(0).name().startsWith("Cube "));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> adapter.openTable(new OpenTableRequest("cube", "cube-1", "CLASSIC", "A", false, true)));
        assertTrue(e.getMessage().contains("360"));
        assertTrue(e.getMessage().contains("100"));
    }

    @Test
    void classicPick_advancesTheDraft_andHidesTheBotsPicks() {
        SeatTicket ticket = solo("CLASSIC");
        String choice = ticket.view().pack().get(4).name();

        DraftView after = adapter.act(ticket.draftId(), ticket.token(), new DraftAction("PICK", choice, 0));

        assertEquals(2, after.pickNumber());
        assertEquals(1, after.moves());
        assertEquals(14, after.pack().size());
        assertEquals(List.of(choice), names(after.picks()));
        assertEquals(4, after.seats().get(3).pickCount(), "bots pick as soon as a pack reaches them");
        assertTrue(after.log().stream().filter(e -> e.type().equals("CARD_PICKED")).allMatch(e -> e.seat() == 0));
        assertEquals(after, adapter.view(ticket.draftId(), ticket.token()));
    }

    @Test
    void rochester_botsPickUntilItIsThePlayersTurn() {
        SeatTicket ticket = solo("ROCHESTER");
        assertEquals(0, ticket.view().activeSeat());

        DraftView after = pickFirst(ticket);

        assertTrue(after.yourTurn());
        assertEquals(2, after.packNumber());
        assertEquals(1, after.opener());
        assertEquals(8, after.pickNumber());
        List<DraftEventView> botPicks = after.log().stream().filter(e -> e.type().equals("CARD_PICKED") && e.seat() != 0).toList();
        assertTrue(botPicks.stream().allMatch(e -> e.cards().size() == 1), "face-up picks show the card");
    }

    @Test
    void winston_takeAndPassPiles_untilTheEnd() {
        SeatTicket ticket = solo("winston");
        assertEquals(List.of(90), pool.randomRequests);
        assertEquals(2, ticket.view().seats().size());
        assertTrue(ticket.view().yourTurn());

        DraftView passed = adapter.act(ticket.draftId(), ticket.token(), new DraftAction("PASS_PILE", null, null));
        assertEquals(1, passed.winston().currentPile());

        DraftView view = passed;
        while (!view.finished()) view = adapter.act(ticket.draftId(), ticket.token(), new DraftAction("take_pile", null, null));

        assertEquals("FINISHED", view.status());
        assertFalse(view.yourTurn());
        assertEquals("DRAFT_FINISHED", view.log().get(view.log().size() - 1).type());
        assertThrows(IllegalStateException.class, () -> adapter.act(ticket.draftId(), ticket.token(), new DraftAction("TAKE_PILE", null, null)));
    }

    @Test
    void act_wrongActionForTheFormat_orUnknownAction_rejected() {
        SeatTicket winston = solo("WINSTON");
        SeatTicket classic = solo("CLASSIC");

        assertThrows(IllegalArgumentException.class, () -> adapter.act(winston.draftId(), winston.token(), DraftAction.pick("Random 1")));
        assertThrows(IllegalArgumentException.class, () -> adapter.act(classic.draftId(), classic.token(), new DraftAction("TAKE_PILE", null, null)));
        assertThrows(IllegalArgumentException.class, () -> adapter.act(classic.draftId(), classic.token(), new DraftAction("SHUFFLE", null, null)));
        assertThrows(IllegalArgumentException.class, () -> adapter.act(classic.draftId(), classic.token(), null));
        assertThrows(IllegalArgumentException.class, () -> adapter.act(classic.draftId(), classic.token(), DraftAction.pick("Not In Pack")));
    }

    @Test
    void expectedMoves_refusesARepeatedOrOutdatedAction() {
        SeatTicket ticket = solo("CLASSIC");
        String first = ticket.view().pack().get(0).name();
        DraftView after = adapter.act(ticket.draftId(), ticket.token(), new DraftAction("PICK", first, 0));

        String next = after.pack().get(0).name();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> adapter.act(ticket.draftId(), ticket.token(), new DraftAction("PICK", next, 0)));
        assertTrue(e.getMessage().contains("out of date"), e.getMessage());
        assertEquals(1, adapter.view(ticket.draftId(), ticket.token()).picks().size());

        assertEquals(2, adapter.act(ticket.draftId(), ticket.token(), new DraftAction("PICK", next, 1)).moves());
    }

    @Test
    void seatTokens_areChecked() {
        SeatTicket ticket = solo("CLASSIC");
        SeatTicket other = solo("CLASSIC");

        assertThrows(SeatAccessDeniedException.class, () -> adapter.view(ticket.draftId(), "wrong"));
        assertThrows(SeatAccessDeniedException.class, () -> adapter.view(ticket.draftId(), null));
        assertThrows(SeatAccessDeniedException.class, () -> adapter.view(ticket.draftId(), other.token()), "a token only opens its own draft");
        assertThrows(SeatAccessDeniedException.class, () -> adapter.act(ticket.draftId(), "wrong", DraftAction.pick("x")));
        assertThrows(NoSuchElementException.class, () -> adapter.view("nope", ticket.token()));
        assertThrows(NoSuchElementException.class, () -> adapter.preview("nope"));
    }

    @Test
    void listCubes_passesThrough() {
        pool.cubes.add(new CubeInfo("cube-1", "Power LSV Cube", 540));
        assertEquals("Power LSV Cube", adapter.listCubes().get(0).name());
        assertEquals(540, adapter.listCubes().get(0).cardCount());
    }

    @Test
    void saveDeck_storesTheSeatsPicksAsADeckCollection() {
        SeatTicket ticket = solo("CLASSIC");
        assertThrows(IllegalStateException.class, () -> adapter.saveDeck(ticket.draftId(), ticket.token(), "Empty"));
        DraftView current = null;
        for (int i = 0; i < 3; i++) current = pickFirst(ticket);

        SavedDeckView saved = adapter.saveDeck(ticket.draftId(), ticket.token(), " My draft deck ");

        assertEquals(new SavedDeckView("deck-1", "My draft deck", 3), saved);
        assertEquals(names(current.picks()), pool.savedDeckCards);
        assertThrows(IllegalArgumentException.class, () -> adapter.saveDeck(ticket.draftId(), ticket.token(), " "));
    }

    @Test
    void aFailedWrite_leavesTheDraftAsItWasStored() {
        SeatTicket ticket = solo("CLASSIC");
        repository.failAppends = true;

        assertThrows(IllegalStateException.class, () -> pickFirst(ticket));

        repository.failAppends = false;
        DraftView view = adapter.view(ticket.draftId(), ticket.token());
        assertEquals(0, view.moves(), "the pick was not stored, so it did not happen");
        assertEquals(15, view.pack().size());
        assertEquals(1, pickFirst(ticket).moves());
    }
}
