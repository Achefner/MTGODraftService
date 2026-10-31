package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.PickCard;
import be.dontletit.draft.domain.command.TakePile;
import be.dontletit.draft.domain.event.BoosterDraftStarted;
import be.dontletit.draft.domain.event.CardPicked;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.domain.event.DraftFinished;
import be.dontletit.draft.domain.event.PackOpened;
import be.dontletit.draft.domain.event.RoundStarted;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static be.dontletit.draft.domain.DraftFixtures.playToTheEnd;
import static org.junit.jupiter.api.Assertions.*;

class RochesterDraftTest {

    static RochesterDraft start() {
        return (RochesterDraft) DraftFixtures.start(DraftFormat.ROCHESTER, 42L);
    }

    /** The active seat takes the first card on the table; returns who picked. */
    static int pickFirst(RochesterDraft draft) {
        int seat = draft.activeSeat();
        draft.handle(new PickCard(seat, draft.table().get(0).name()));
        return seat;
    }

    static List<Integer> pickOrderOfOnePack(RochesterDraft draft) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 15; i++) order.add(pickFirst(draft));
        return order;
    }

    @Test
    void format_isAnEightSeatDraftOf360Cards() {
        assertEquals(8, DraftFormat.ROCHESTER.seats());
        assertEquals(360, DraftFormat.ROCHESTER.cardsNeeded());
    }

    @Test
    void start_laysTheFirstBoosterOfSeatZeroOnTheTable() {
        RochesterDraft draft = start();

        List<DraftEvent> history = draft.history();
        BoosterDraftStarted started = assertInstanceOf(BoosterDraftStarted.class, history.get(0));
        assertEquals(DraftFormat.ROCHESTER, started.format());
        assertEquals(24, started.packs().size());
        assertEquals(new RoundStarted(1, PassDirection.LEFT), history.get(1));
        assertEquals(new PackOpened(1, 1, 0, started.packs().get(0)), history.get(2));

        assertEquals(1, draft.round());
        assertEquals(1, draft.packNumber());
        assertEquals(1, draft.pickInPack());
        assertEquals(0, draft.opener());
        assertEquals(0, draft.activeSeat());
        assertEquals(PassDirection.LEFT, draft.direction());
        assertEquals(started.packs().get(0), draft.table());
    }

    @Test
    void onlyTheActiveSeatCanPick() {
        RochesterDraft draft = start();

        assertTrue(draft.canAct(0));
        for (int seat = 1; seat < 8; seat++) {
            assertFalse(draft.canAct(seat));
            assertTrue(draft.legalCommands(seat).isEmpty());
        }
        assertEquals(15, draft.legalCommands(0).size());
        assertThrows(IllegalStateException.class, () -> draft.handle(new PickCard(3, draft.table().get(0).name())));
    }

    @Test
    void pick_isSeenByAllAndPassesTheTurnToTheLeft() {
        RochesterDraft draft = start();
        DraftCard choice = draft.table().get(4);

        List<DraftEvent> events = draft.handle(new PickCard(0, choice.name()));

        assertEquals(List.of(new CardPicked(0, choice, true)), events);
        assertEquals(List.of(choice), draft.picksOf(0));
        assertEquals(14, draft.table().size());
        assertFalse(draft.table().contains(choice));
        assertEquals(1, draft.activeSeat());
        assertEquals(2, draft.pickInPack());
    }

    @Test
    void pick_unknownCardOrForeignCommand_rejected() {
        RochesterDraft draft = start();
        assertThrows(IllegalArgumentException.class, () -> draft.handle(new PickCard(0, "Not On The Table")));
        assertThrows(IllegalArgumentException.class, () -> draft.handle(new TakePile(0)));
        assertEquals(3, draft.history().size());
    }

    @Test
    void pickOrder_snakes_theLastSeatPicksTwiceThenItGoesBack() {
        RochesterDraft draft = start();
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 7, 6, 5, 4, 3, 2, 1), pickOrderOfOnePack(draft));
    }

    @Test
    void emptyTable_theNextSeatOpensItsBooster() {
        RochesterDraft draft = start();
        BoosterDraftStarted started = (BoosterDraftStarted) draft.history().get(0);
        pickOrderOfOnePack(draft);

        assertEquals(new PackOpened(1, 2, 1, started.packs().get(1)), draft.history().get(draft.history().size() - 1));
        assertEquals(2, draft.packNumber());
        assertEquals(1, draft.opener());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 0, 0, 7, 6, 5, 4, 3, 2), pickOrderOfOnePack(draft));
    }

    @Test
    void afterEightBoosters_everySeatHasFifteenCards_andRoundTwoGoesRight() {
        RochesterDraft draft = start();
        BoosterDraftStarted started = (BoosterDraftStarted) draft.history().get(0);
        for (int pack = 0; pack < 8; pack++) pickOrderOfOnePack(draft);

        for (int seat = 0; seat < 8; seat++) assertEquals(15, draft.picksOf(seat).size());
        assertEquals(2, draft.round());
        assertEquals(PassDirection.RIGHT, draft.direction());
        assertTrue(draft.history().contains(new RoundStarted(2, PassDirection.RIGHT)));
        assertEquals(new PackOpened(2, 1, 0, started.packs().get(8)), draft.history().get(draft.history().size() - 1));
        assertEquals(List.of(0, 7, 6, 5, 4, 3, 2, 1, 1, 2, 3, 4, 5, 6, 7), pickOrderOfOnePack(draft));
        assertEquals(7, draft.opener(), "openers move right in round 2");
        assertEquals(started.packs().get(8 + 7), draft.table());
    }

    @Test
    void roundThree_goesLeftAgain() {
        RochesterDraft draft = start();
        for (int pack = 0; pack < 16; pack++) pickOrderOfOnePack(draft);
        assertEquals(3, draft.round());
        assertEquals(PassDirection.LEFT, draft.direction());
        assertEquals(0, draft.opener());
    }

    @Test
    void afterTwentyFourBoosters_theDraftIsFinished() {
        RochesterDraft draft = start();
        playToTheEnd(draft);

        assertTrue(draft.finished());
        assertEquals(new DraftFinished(), draft.history().get(draft.history().size() - 1));
        assertTrue(draft.table().isEmpty());
        Set<String> all = new HashSet<>();
        for (int seat = 0; seat < 8; seat++) {
            assertEquals(45, draft.picksOf(seat).size());
            for (DraftCard card : draft.picksOf(seat)) assertTrue(all.add(card.name()));
            assertFalse(draft.canAct(seat));
        }
        assertEquals(360, all.size());
        assertThrows(IllegalStateException.class, () -> draft.handle(new PickCard(0, "x")));
    }

    @Test
    void replayingTheHistory_rebuildsTheSameDraft() {
        RochesterDraft draft = start();
        for (int i = 0; i < 50; i++) pickFirst(draft);

        RochesterDraft replayed = (RochesterDraft) Draft.replay(draft.history());

        assertEquals(draft.history(), replayed.history());
        assertEquals(draft.table(), replayed.table());
        assertEquals(draft.activeSeat(), replayed.activeSeat());
        assertEquals(draft.packNumber(), replayed.packNumber());
        for (int seat = 0; seat < 8; seat++) assertEquals(draft.picksOf(seat), replayed.picksOf(seat));
    }
}
