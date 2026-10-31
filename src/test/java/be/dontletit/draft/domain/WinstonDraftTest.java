package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.PassPile;
import be.dontletit.draft.domain.command.PickCard;
import be.dontletit.draft.domain.command.TakePile;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.domain.event.DraftFinished;
import be.dontletit.draft.domain.event.PilePassed;
import be.dontletit.draft.domain.event.PileTaken;
import be.dontletit.draft.domain.event.StackCardTaken;
import be.dontletit.draft.domain.event.WinstonDraftStarted;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static be.dontletit.draft.domain.DraftFixtures.playToTheEnd;
import static be.dontletit.draft.domain.DraftFixtures.pool;
import static org.junit.jupiter.api.Assertions.*;

class WinstonDraftTest {

    static WinstonDraft start() {
        return (WinstonDraft) DraftFixtures.start(DraftFormat.WINSTON, 42L);
    }

    static WinstonDraftStarted started(WinstonDraft draft) {
        return (WinstonDraftStarted) draft.history().get(0);
    }

    static void take(WinstonDraft draft) {
        draft.handle(new TakePile(draft.activeSeat()));
    }

    static void pass(WinstonDraft draft) {
        draft.handle(new PassPile(draft.activeSeat()));
    }

    static void takeUntilStackHas(WinstonDraft draft, int size) {
        while (draft.stackSize() > size) take(draft);
    }

    @Test
    void format_isATwoSeatDraftOfNinetyCards() {
        assertEquals(2, DraftFormat.WINSTON.seats());
        assertEquals(90, DraftFormat.WINSTON.cardsNeeded());
    }

    @Test
    void start_needsNinetyCards() {
        assertThrows(IllegalArgumentException.class, () -> Draft.start(DraftFormat.WINSTON, "d1", pool(89), new Random(1L)));
    }

    @Test
    void start_dealsOneCardToEachOfThreePiles() {
        WinstonDraft draft = start();
        WinstonDraftStarted started = started(draft);

        assertEquals(1, draft.history().size());
        assertEquals(90, started.stack().size());
        assertEquals(List.of(1, 1, 1), draft.pileSizes());
        assertEquals(87, draft.stackSize());
        for (int pile = 0; pile < 3; pile++) assertEquals(List.of(started.stack().get(pile)), draft.pile(pile));
        assertEquals(0, draft.currentPile());
        assertEquals(started.firstSeat(), draft.activeSeat());
    }

    @Test
    void theFirstSeatIsChosenAtRandom() {
        Set<Integer> firstSeats = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) firstSeats.add(((WinstonDraft) DraftFixtures.start(DraftFormat.WINSTON, seed)).activeSeat());
        assertEquals(Set.of(0, 1), firstSeats);
    }

    @Test
    void onlyTheActiveSeatActs_takingOrPassing() {
        WinstonDraft draft = start();
        int active = draft.activeSeat();
        int other = 1 - active;

        assertTrue(draft.canAct(active));
        assertFalse(draft.canAct(other));
        assertEquals(List.of(new TakePile(active), new PassPile(active)), draft.legalCommands(active));
        assertTrue(draft.legalCommands(other).isEmpty());
        assertThrows(IllegalStateException.class, () -> draft.handle(new TakePile(other)));
        assertThrows(IllegalArgumentException.class, () -> draft.handle(new PickCard(active, "Card 1")));
    }

    @Test
    void takingAPile_refillsItFromTheStackAndEndsTheTurn() {
        WinstonDraft draft = start();
        int seat = draft.activeSeat();
        List<DraftCard> pile = List.copyOf(draft.pile(0));
        DraftCard nextOnStack = started(draft).stack().get(3);

        List<DraftEvent> events = draft.handle(new TakePile(seat));

        assertEquals(List.of(new PileTaken(seat, 0, pile)), events);
        assertEquals(pile, draft.picksOf(seat));
        assertEquals(List.of(nextOnStack), draft.pile(0));
        assertEquals(86, draft.stackSize());
        assertEquals(1 - seat, draft.activeSeat());
        assertEquals(0, draft.currentPile());
    }

    @Test
    void passingAPile_addsTheTopOfTheStackToIt_andShowsTheNextPile() {
        WinstonDraft draft = start();
        int seat = draft.activeSeat();

        List<DraftEvent> events = draft.handle(new PassPile(seat));

        assertEquals(List.of(new PilePassed(seat, 0)), events);
        assertEquals(List.of(2, 1, 1), draft.pileSizes());
        assertEquals(86, draft.stackSize());
        assertEquals(1, draft.currentPile());
        assertEquals(seat, draft.activeSeat(), "still the same turn");

        take(draft);
        assertEquals(1, draft.picksOf(seat).size());
        assertEquals(List.of(2, 1, 1), draft.pileSizes(), "pile 2 was refilled");
    }

    @Test
    void passingTheThirdPile_takesTheTopCardOfTheStack() {
        WinstonDraft draft = start();
        int seat = draft.activeSeat();
        pass(draft);
        pass(draft);
        DraftCard top = started(draft).stack().get(6);

        List<DraftEvent> events = draft.handle(new PassPile(seat));

        assertEquals(List.of(new PilePassed(seat, 2), new StackCardTaken(seat, top)), events);
        assertEquals(List.of(top), draft.picksOf(seat));
        assertEquals(List.of(2, 2, 2), draft.pileSizes());
        assertEquals(83, draft.stackSize());
        assertEquals(1 - seat, draft.activeSeat());
        assertEquals(0, draft.currentPile());
    }

    @Test
    void lastPileWithOneCardLeftOnTheStack_mustBeTaken() {
        WinstonDraft draft = start();
        takeUntilStackHas(draft, 3);
        pass(draft);
        pass(draft);
        assertEquals(1, draft.stackSize());
        int seat = draft.activeSeat();

        assertEquals(List.of(new TakePile(seat)), draft.legalCommands(seat));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> draft.handle(new PassPile(seat)));
        assertTrue(e.getMessage().contains("take"), e.getMessage());
    }

    @Test
    void emptyStack_passingAddsNothing_andEmptyPilesAreSkipped() {
        WinstonDraft draft = start();
        takeUntilStackHas(draft, 0);
        List<Integer> sizes = draft.pileSizes();

        pass(draft);
        assertEquals(sizes, draft.pileSizes());
        assertEquals(1, draft.currentPile());
        take(draft);
        assertEquals(0, draft.pileSizes().get(1), "no card left to refill it");

        take(draft);
        assertEquals(0, draft.pileSizes().get(0));
        assertEquals(2, draft.currentPile(), "piles 0 and 1 are empty, the turn starts at pile 2");
        assertEquals(List.of(new TakePile(draft.activeSeat())), draft.legalCommands(draft.activeSeat()), "nothing left to pass to");
    }

    @Test
    void theDraftEndsWhenEveryCardIsTaken() {
        WinstonDraft draft = start();
        playToTheEnd(draft);

        assertTrue(draft.finished());
        assertEquals(new DraftFinished(), draft.history().get(draft.history().size() - 1));
        assertEquals(0, draft.stackSize());
        assertEquals(List.of(0, 0, 0), draft.pileSizes());
        Set<String> all = new HashSet<>();
        for (int seat = 0; seat < 2; seat++) {
            for (DraftCard card : draft.picksOf(seat)) assertTrue(all.add(card.name()));
            assertFalse(draft.canAct(seat));
        }
        assertEquals(90, all.size());
        assertThrows(IllegalStateException.class, () -> draft.handle(new TakePile(0)));
    }

    @Test
    void randomPlay_alsoDraftsEveryCard() {
        for (long seed = 0; seed < 10; seed++) {
            WinstonDraft draft = (WinstonDraft) DraftFixtures.start(DraftFormat.WINSTON, seed);
            new RandomBot(new Random(seed)).playUntilBlocked(draft, Set.of());
            assertTrue(draft.finished());
            assertEquals(90, draft.picksOf(0).size() + draft.picksOf(1).size());
        }
    }

    @Test
    void replayingTheHistory_rebuildsTheSameDraft() {
        WinstonDraft draft = start();
        RandomBot bot = new RandomBot(new Random(3L));
        for (int i = 0; i < 40; i++) draft.handle(bot.choose(draft, draft.activeSeat()));

        WinstonDraft replayed = (WinstonDraft) Draft.replay(draft.history());

        assertEquals(draft.history(), replayed.history());
        assertEquals(draft.pileSizes(), replayed.pileSizes());
        assertEquals(draft.stackSize(), replayed.stackSize());
        assertEquals(draft.activeSeat(), replayed.activeSeat());
        assertEquals(draft.currentPile(), replayed.currentPile());
        for (int seat = 0; seat < 2; seat++) assertEquals(draft.picksOf(seat), replayed.picksOf(seat));
    }
}
