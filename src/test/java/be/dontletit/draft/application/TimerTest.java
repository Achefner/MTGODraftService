package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftAction;
import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.SeatTicket;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** When time runs out, a random legal move is made for the seat. */
class TimerTest extends AdapterFixture {

    @Test
    void timedDraft_showsTheDeadline() {
        SeatTicket ticket = timedSolo("CLASSIC");

        assertEquals(75, ticket.view().timer().seconds());
        assertEquals(timer.now().plusSeconds(75), ticket.view().timer().deadline());
        assertEquals(75_000, ticket.view().timer().remainingMillis());

        timer.advance(Duration.ofSeconds(30));
        assertEquals(45_000, adapter.view(ticket.draftId(), ticket.token()).timer().remainingMillis(),
                "what is left when the view is made, so a browser with another clock still counts right");
    }

    @Test
    void whenTimeRunsOut_aCardIsPickedForTheSeat_andTheNextPickHasLessTime() {
        SeatTicket ticket = timedSolo("CLASSIC");
        Recorder updates = new Recorder();
        adapter.subscribe(ticket.draftId(), ticket.token(), null, updates::onUpdate);

        timer.advance(Duration.ofSeconds(74));
        assertEquals(0, adapter.view(ticket.draftId(), ticket.token()).moves());

        timer.advance(Duration.ofSeconds(1));

        DraftView view = adapter.view(ticket.draftId(), ticket.token());
        assertEquals(1, view.moves());
        assertEquals(1, view.picks().size());
        assertEquals(70, view.timer().seconds());
        assertEquals(1, updates.last().view().moves(), "the seat is told");
    }

    @Test
    void pickingInTime_replacesTheTimer() {
        SeatTicket ticket = timedSolo("CLASSIC");
        timer.advance(Duration.ofSeconds(60));
        pickFirst(ticket);

        timer.advance(Duration.ofSeconds(20));
        assertEquals(1, adapter.view(ticket.draftId(), ticket.token()).moves(), "the first timer was cancelled");

        timer.advance(Duration.ofSeconds(50));
        assertEquals(2, adapter.view(ticket.draftId(), ticket.token()).moves(), "the second one, 70 seconds, ran out");
    }

    @Test
    void untimedDraft_neverPicksForTheSeat() {
        SeatTicket ticket = solo("CLASSIC");
        timer.advance(Duration.ofHours(1));
        assertEquals(0, adapter.view(ticket.draftId(), ticket.token()).moves());
        assertEquals(0, timer.pendingAlarms());
    }

    @Test
    void onlyASeatThatCanActHasAClock() {
        SeatTicket host = lobby("ROCHESTER", true);
        SeatTicket guest = adapter.join(host.draftId(), "Guest");
        assertNull(host.view().timer(), "no clock in the lobby");
        adapter.start(host.draftId(), host.token());

        assertEquals(40, adapter.view(host.draftId(), host.token()).timer().seconds());
        assertNull(adapter.view(host.draftId(), guest.token()).timer());

        timer.advance(Duration.ofSeconds(40));

        assertNull(adapter.view(host.draftId(), host.token()).timer());
        DraftView guestView = adapter.view(host.draftId(), guest.token());
        assertTrue(guestView.yourTurn());
        assertEquals(20, guestView.timer().seconds());
    }

    @Test
    void winston_runningOutOfTime_makesALegalMove() {
        SeatTicket ticket = timedSolo("WINSTON");
        int stackBefore = ticket.view().winston().stackSize();

        timer.advance(Duration.ofSeconds(30));

        DraftView view = adapter.view(ticket.draftId(), ticket.token());
        assertEquals(1, view.moves());
        assertTrue(view.winston().stackSize() < stackBefore);
    }

    @Test
    void theClockStopsWhenTheDraftEnds() {
        SeatTicket ticket = timedSolo("WINSTON");
        DraftView view = ticket.view();
        while (!view.finished()) view = adapter.act(ticket.draftId(), ticket.token(), new DraftAction("TAKE_PILE", null, null));

        assertNull(view.timer());
        assertEquals(0, timer.pendingAlarms());
    }
}
