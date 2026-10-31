package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftView;
import be.dontletit.draft.api.SeatTicket;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** Everything a player needs survives a restart of the service. */
class RestartTest extends AdapterFixture {

    @Test
    void aRunningDraft_isRebuiltFromItsHistory() {
        SeatTicket ticket = solo("ROCHESTER");
        pickFirst(ticket);
        DraftView before = adapter.view(ticket.draftId(), ticket.token());

        restart();

        assertEquals(before, adapter.view(ticket.draftId(), ticket.token()));
        assertEquals(2, pickFirst(ticket).moves());
    }

    @Test
    void theHistoryIsStoredAsItHappens() {
        SeatTicket ticket = solo("CLASSIC");
        int stored = repository.loadEvents(ticket.draftId()).size();

        pickFirst(ticket);

        assertTrue(repository.loadEvents(ticket.draftId()).size() > stored);
    }

    @Test
    void aLobby_survivesToo() {
        SeatTicket host = lobby("WINSTON", false);

        restart();

        assertEquals(1, adapter.join(host.draftId(), "Guest").seat());
        assertEquals("RUNNING", adapter.start(host.draftId(), host.token()).status());
    }

    @Test
    void timersAreRearmed_withAFullClock() {
        SeatTicket ticket = timedSolo("CLASSIC");
        timer.advance(Duration.ofSeconds(70));

        restart();

        DraftView view = adapter.view(ticket.draftId(), ticket.token());
        assertEquals(timer.now().plusSeconds(75), view.timer().deadline());
        timer.advance(Duration.ofSeconds(75));
        assertEquals(1, adapter.view(ticket.draftId(), ticket.token()).moves());
    }

    @Test
    void aFinishedDraft_staysFinished() {
        SeatTicket ticket = solo("WINSTON");
        DraftView view = ticket.view();
        while (!view.finished()) view = adapter.act(ticket.draftId(), ticket.token(), new be.dontletit.draft.api.DraftAction("TAKE_PILE", null, null));

        restart();

        assertEquals("FINISHED", adapter.view(ticket.draftId(), ticket.token()).status());
    }
}
