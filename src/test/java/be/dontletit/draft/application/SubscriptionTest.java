package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftUpdate;
import be.dontletit.draft.api.SeatAccessDeniedException;
import be.dontletit.draft.api.SeatTicket;
import be.dontletit.draft.api.Subscription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Following a draft live, and coming back after losing the connection. */
class SubscriptionTest extends AdapterFixture {

    @Test
    void subscribe_firstSendsTheCurrentView() {
        SeatTicket ticket = solo("CLASSIC");
        Recorder recorder = new Recorder();

        adapter.subscribe(ticket.draftId(), ticket.token(), null, recorder::onUpdate);

        assertEquals(1, recorder.updates.size());
        DraftUpdate first = recorder.last();
        assertTrue(first.events().isEmpty(), "a fresh connection gets the view, its log tells the past");
        assertEquals(15, first.view().pack().size());
        assertTrue(first.view().seats().get(0).connected());
        assertEquals(repository.loadEvents(ticket.draftId()).size(), first.version());
        assertEquals(first.version(), first.view().version(), "the view carries its version, to drop late updates");
    }

    @Test
    void everySeatHearsAboutEveryChange_withTheEventsItMaySee() {
        SeatTicket host = lobby("ROCHESTER", false);
        Recorder hostUpdates = new Recorder();
        adapter.subscribe(host.draftId(), host.token(), null, hostUpdates::onUpdate);

        SeatTicket guest = adapter.join(host.draftId(), "Guest");
        assertEquals("Guest", hostUpdates.last().view().seats().get(1).name(), "the lobby updates when someone joins");

        Recorder guestUpdates = new Recorder();
        adapter.subscribe(host.draftId(), guest.token(), null, guestUpdates::onUpdate);
        assertTrue(hostUpdates.last().view().seats().get(1).connected());

        adapter.start(host.draftId(), host.token());
        assertEquals("RUNNING", guestUpdates.last().view().status());

        long before = guestUpdates.last().version();
        pickFirst(host);

        DraftUpdate update = guestUpdates.last();
        assertTrue(update.version() > before);
        assertEquals("CARD_PICKED", update.events().get(0).type());
        assertEquals(0, update.events().get(0).seat());
        assertTrue(update.view().yourTurn());
    }

    @Test
    void reconnecting_withTheLastVersionSeen_getsWhatWasMissed() {
        SeatTicket ticket = solo("ROCHESTER");
        Recorder first = new Recorder();
        Subscription subscription = adapter.subscribe(ticket.draftId(), ticket.token(), null, first::onUpdate);
        long seen = first.last().version();
        subscription.close();

        pickFirst(ticket);
        Recorder second = new Recorder();
        adapter.subscribe(ticket.draftId(), ticket.token(), seen, second::onUpdate);

        DraftUpdate catchUp = second.last();
        assertEquals("CARD_PICKED", catchUp.events().get(0).type());
        assertTrue(catchUp.events().size() > 10, "the bots' picks since then are included");
        assertEquals(adapter.view(ticket.draftId(), ticket.token()), catchUp.view());
    }

    @Test
    void aSeatShowsAsDisconnected_whenItsLastSubscriptionCloses_andTheOthersAreTold() {
        SeatTicket host = lobby("WINSTON", false);
        SeatTicket guest = adapter.join(host.draftId(), "Guest");
        Recorder hostUpdates = new Recorder();
        adapter.subscribe(host.draftId(), host.token(), null, hostUpdates::onUpdate);
        Subscription tab1 = adapter.subscribe(host.draftId(), guest.token(), null, update -> {});
        Subscription tab2 = adapter.subscribe(host.draftId(), guest.token(), null, update -> {});

        tab1.close();
        assertTrue(hostUpdates.last().view().seats().get(1).connected(), "another tab is still open");
        tab2.close();
        tab2.close();

        assertFalse(hostUpdates.last().view().seats().get(1).connected());
    }

    @Test
    void aListenerThatFails_isDropped() {
        SeatTicket ticket = solo("CLASSIC");
        int[] calls = {0};
        adapter.subscribe(ticket.draftId(), ticket.token(), null, update -> {
            if (++calls[0] > 1) throw new IllegalStateException("connection closed");
        });

        pickFirst(ticket);
        pickFirst(ticket);

        assertEquals(2, calls[0]);
        assertFalse(adapter.view(ticket.draftId(), ticket.token()).seats().get(0).connected());
    }

    @Test
    void subscribe_needsTheSeatToken() {
        SeatTicket ticket = solo("CLASSIC");
        assertThrows(SeatAccessDeniedException.class, () -> adapter.subscribe(ticket.draftId(), "wrong", null, update -> {}));
    }
}
