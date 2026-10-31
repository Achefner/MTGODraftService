package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftAction;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.OpenTableRequest;
import be.dontletit.draft.api.SeatAccessDeniedException;
import be.dontletit.draft.api.SeatTicket;
import be.dontletit.draft.api.SeatView;
import be.dontletit.draft.api.TablePreview;
import be.dontletit.draft.stubs.CardPoolSpiStub;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Several people at one table: lobby, joining, starting, playing side by side. */
class TableTest extends AdapterFixture {

    @Test
    void openTable_withoutStarting_isALobbyWithOpenSeats() {
        SeatTicket host = lobby("CLASSIC", false);
        DraftView view = host.view();

        assertEquals("LOBBY", view.status());
        assertEquals(0, view.version());
        assertTrue(view.host());
        assertFalse(view.yourTurn());
        assertTrue(view.pack().isEmpty());
        assertNull(view.round());
        assertEquals(8, view.seats().size());
        assertEquals(new SeatView(0, true, "Host", "HUMAN", false, 0), view.seats().get(0));
        assertEquals(new SeatView(1, false, null, "OPEN", false, 0), view.seats().get(1));
        assertTrue(pool.randomRequests.isEmpty(), "cards are drawn when the draft starts");
    }

    @Test
    void preview_showsTheTableWithoutAToken() {
        SeatTicket host = lobby("WINSTON", true);

        TablePreview preview = adapter.preview(host.draftId());

        assertEquals(host.draftId(), preview.id());
        assertEquals("WINSTON", preview.format());
        assertEquals("random", preview.source());
        assertEquals("LOBBY", preview.status());
        assertTrue(preview.timed());
        assertEquals("Host", preview.seats().get(0).name());
        assertFalse(preview.seats().get(0).you());
        assertEquals("OPEN", preview.seats().get(1).kind());
    }

    @Test
    void join_takesTheNextOpenSeat_untilTheTableIsFull() {
        SeatTicket host = lobby("WINSTON", false);

        SeatTicket guest = adapter.join(host.draftId(), "Guest");

        assertEquals(1, guest.seat());
        assertNotEquals(host.token(), guest.token());
        assertFalse(guest.view().host());
        assertEquals("Guest", guest.view().seats().get(1).name());
        assertTrue(guest.view().seats().get(1).you());
        IllegalStateException full = assertThrows(IllegalStateException.class, () -> adapter.join(host.draftId(), "Third"));
        assertTrue(full.getMessage().contains("full"), full.getMessage());
    }

    @Test
    void onlyTheHostStarts_andOnlyOnce_andNobodyPlaysBefore() {
        SeatTicket host = lobby("CLASSIC", false);
        SeatTicket guest = adapter.join(host.draftId(), "Guest");

        assertThrows(IllegalStateException.class, () -> adapter.act(host.draftId(), host.token(), DraftAction.pick("Random 1")));
        assertThrows(SeatAccessDeniedException.class, () -> adapter.start(host.draftId(), guest.token()));

        DraftView started = adapter.start(host.draftId(), host.token());

        assertEquals("RUNNING", started.status());
        assertEquals(15, started.pack().size());
        assertThrows(IllegalStateException.class, () -> adapter.start(host.draftId(), host.token()));
        assertThrows(IllegalStateException.class, () -> adapter.join(host.draftId(), "Late"));
    }

    @Test
    void start_turnsTheOpenSeatsIntoBots_andTheHumansPlayAtTheirOwnPace() {
        SeatTicket host = lobby("CLASSIC", false);
        SeatTicket guest = adapter.join(host.draftId(), "Guest");
        adapter.start(host.draftId(), host.token());

        DraftView guestView = adapter.view(host.draftId(), guest.token());
        assertTrue(guestView.yourTurn());
        assertEquals("HUMAN", guestView.seats().get(1).kind());
        assertEquals("BOT", guestView.seats().get(2).kind());

        DraftView afterGuest = pickFirst(guest);

        assertFalse(afterGuest.yourTurn(), "passing left, the guest's next pack comes from the host, who has not picked yet");
        DraftView hostView = adapter.view(host.draftId(), host.token());
        assertEquals(0, hostView.picks().size());
        assertEquals(1, hostView.seats().get(1).pickCount());
        assertTrue(hostView.yourTurn());

        pickFirst(host);

        assertTrue(adapter.view(host.draftId(), guest.token()).yourTurn());
        assertEquals(2, pickFirst(guest).moves());
    }

    @Test
    void rochester_waitsForTheHumanWhoseTurnItIs() {
        SeatTicket host = lobby("ROCHESTER", false);
        SeatTicket guest = adapter.join(host.draftId(), "Guest");
        adapter.start(host.draftId(), host.token());

        DraftView afterHost = pickFirst(host);

        assertFalse(afterHost.yourTurn());
        assertEquals(1, afterHost.activeSeat(), "the guest at seat 1 picks next");
        assertThrows(IllegalStateException.class, () -> pickFirst(host));
        DraftView afterGuest = pickFirst(guest);
        assertEquals(1, afterGuest.seats().get(1).pickCount());
        assertTrue(afterGuest.yourTurn(), "the order snakes back: seat 1 also takes the booster's last card");
        assertEquals(15, afterGuest.pickNumber());
    }

    @Test
    void cubeTable_drawsTheCubeWhenTheDraftStarts() {
        pool.collections.put("cube-1", CardPoolSpiStub.cards("Cube", 400));
        SeatTicket host = adapter.openTable(new OpenTableRequest("cube", "cube-1", "CLASSIC", "Host", false, false));

        DraftView started = adapter.start(host.draftId(), host.token());

        assertTrue(started.pack().get(0).name().startsWith("Cube "));
    }
}
