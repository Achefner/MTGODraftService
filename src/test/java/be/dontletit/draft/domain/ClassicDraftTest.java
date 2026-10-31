package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.PassPile;
import be.dontletit.draft.domain.command.PickCard;
import be.dontletit.draft.domain.event.BoosterDraftStarted;
import be.dontletit.draft.domain.event.CardPicked;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.domain.event.DraftFinished;
import be.dontletit.draft.domain.event.PackPassed;
import be.dontletit.draft.domain.event.RoundStarted;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static be.dontletit.draft.domain.DraftFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassicDraftTest {

    static ClassicDraft start() {
        return (ClassicDraft) DraftFixtures.start(DraftFormat.CLASSIC, 42L);
    }

    /** Seat 0 picks its first card, then the other seats play until they wait for seat 0. */
    static void pickFirstAndLetOthersPlay(ClassicDraft draft, int times) {
        for (int i = 0; i < times; i++) {
            draft.handle(new PickCard(0, draft.packFor(0).get(0).name()));
            othersPlayUntilBlocked(draft, 0);
        }
    }

    @Test
    void format_isAnEightSeatBoosterDraftOf360Cards() {
        assertEquals(8, DraftFormat.CLASSIC.seats());
        assertEquals(360, DraftFormat.CLASSIC.cardsNeeded());
        assertEquals(3, ClassicDraft.ROUNDS);
        assertEquals(15, ClassicDraft.PACK_SIZE);
    }

    @Test
    void start_needsEnoughCards() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Draft.start(DraftFormat.CLASSIC, "d1", pool(359), new Random(1L)));
        assertTrue(e.getMessage().contains("360"));
    }

    @Test
    void start_dealsFifteenDistinctCardsToEverySeat() {
        ClassicDraft draft = start();

        Set<String> seen = new HashSet<>();
        for (int seat = 0; seat < 8; seat++) {
            List<DraftCard> pack = draft.packFor(seat);
            assertEquals(15, pack.size());
            for (DraftCard card : pack) assertTrue(seen.add(card.name()), "dealt twice: " + card.name());
        }
        assertEquals(120, seen.size());
    }

    @Test
    void start_withABiggerPoolUsesASubsetOfIt() {
        Draft draft = Draft.start(DraftFormat.CLASSIC, "d1", pool(540), new Random(7L));
        assertEquals(15, ((ClassicDraft) draft).packFor(0).size());
    }

    @Test
    void start_isDeterministicForASeed() {
        ClassicDraft a = (ClassicDraft) DraftFixtures.start(DraftFormat.CLASSIC, 5L);
        ClassicDraft b = (ClassicDraft) DraftFixtures.start(DraftFormat.CLASSIC, 5L);
        ClassicDraft c = (ClassicDraft) DraftFixtures.start(DraftFormat.CLASSIC, 6L);
        assertEquals(a.packFor(0), b.packFor(0));
        assertNotEquals(a.packFor(0), c.packFor(0));
    }

    @Test
    void start_recordsTheBoostersAndTheFirstRound() {
        ClassicDraft draft = start();

        List<DraftEvent> history = draft.history();
        assertEquals(2, history.size());
        BoosterDraftStarted started = assertInstanceOf(BoosterDraftStarted.class, history.get(0));
        assertEquals("d1", started.draftId());
        assertEquals(DraftFormat.CLASSIC, started.format());
        assertEquals(24, started.packs().size());
        assertEquals(new RoundStarted(1, PassDirection.LEFT), history.get(1));
    }

    @Test
    void initialState() {
        ClassicDraft draft = start();
        assertEquals("d1", draft.id());
        assertEquals(DraftFormat.CLASSIC, draft.format());
        assertEquals(1, draft.round());
        assertEquals(1, draft.pickNumber(0));
        assertEquals(PassDirection.LEFT, draft.direction());
        assertTrue(draft.picksOf(0).isEmpty());
        assertFalse(draft.finished());
        for (int seat = 0; seat < 8; seat++) assertTrue(draft.canAct(seat), "everyone picks at the same time");
    }

    @Test
    void legalCommands_areOnePickPerCardInTheSeatsPack() {
        ClassicDraft draft = start();
        List<String> names = draft.packFor(3).stream().map(DraftCard::name).toList();

        assertEquals(names, draft.legalCommands(3).stream().map(c -> ((PickCard) c).cardName()).toList());
        assertTrue(draft.legalCommands(3).stream().allMatch(c -> c.seat() == 3));
    }

    @Test
    void pick_takesTheCardAndPassesTheRestToTheLeft() {
        ClassicDraft draft = start();
        List<DraftCard> myPack = new ArrayList<>(draft.packFor(0));
        DraftCard choice = myPack.get(3);

        List<DraftEvent> events = draft.handle(new PickCard(0, choice.name()));

        assertEquals(List.of(new CardPicked(0, choice, false), new PackPassed(0, 1, 14)), events);
        assertEquals(List.of(choice), draft.picksOf(0));
        assertFalse(draft.canAct(0), "seat 0 waits for the pack of seat 7");
        assertEquals(2, draft.queuedPacks(1), "seat 1 now has its own pack and seat 0's");
        assertEquals(15, draft.packFor(1).size(), "a seat picks from the oldest pack in its queue first");
    }

    @Test
    void pick_receivesTheRightNeighboursPackWhenPassingLeft() {
        ClassicDraft draft = start();
        List<DraftCard> rightNeighbourPack = new ArrayList<>(draft.packFor(7));

        pickFirstAndLetOthersPlay(draft, 1);

        assertEquals(2, draft.pickNumber(0));
        List<DraftCard> received = draft.packFor(0);
        assertEquals(14, received.size());
        assertTrue(rightNeighbourPack.containsAll(received), "pack 7 should have been passed to seat 0");
    }

    @Test
    void pick_unknownCard_rejectedAndNothingChanges() {
        ClassicDraft draft = start();
        List<DraftCard> before = draft.packFor(0);

        assertThrows(IllegalArgumentException.class, () -> draft.handle(new PickCard(0, "Not In Pack")));
        assertThrows(IllegalArgumentException.class, () -> draft.handle(new PickCard(0, null)));

        assertEquals(before, draft.packFor(0));
        assertTrue(draft.picksOf(0).isEmpty());
        assertEquals(2, draft.history().size());
    }

    @Test
    void pick_isCaseInsensitive() {
        ClassicDraft draft = start();
        DraftCard choice = draft.packFor(0).get(0);
        draft.handle(new PickCard(0, choice.name().toUpperCase()));
        assertEquals(List.of(choice), draft.picksOf(0));
    }

    @Test
    void aSeatWithoutAPack_cannotPick() {
        ClassicDraft draft = start();
        draft.handle(new PickCard(0, draft.packFor(0).get(0).name()));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> draft.handle(new PickCard(0, "Card 1")));
        assertTrue(e.getMessage().contains("seat 0"), e.getMessage());
        assertTrue(draft.legalCommands(0).isEmpty());
    }

    @Test
    void unknownSeatOrForeignCommand_rejected() {
        ClassicDraft draft = start();
        assertThrows(IllegalArgumentException.class, () -> draft.handle(new PickCard(8, "Card 1")));
        assertThrows(IllegalArgumentException.class, () -> draft.handle(new PickCard(-1, "Card 1")));
        assertThrows(IllegalArgumentException.class, () -> draft.handle(new PassPile(0)));
    }

    @Test
    void afterFifteenPicks_secondRoundOpensNewPacksPassingRight() {
        ClassicDraft draft = start();
        pickFirstAndLetOthersPlay(draft, 14);
        assertEquals(1, draft.packFor(0).size());
        assertEquals(15, draft.pickNumber(0));

        pickFirstAndLetOthersPlay(draft, 1);

        assertEquals(2, draft.round());
        assertEquals(1, draft.pickNumber(0));
        assertEquals(PassDirection.RIGHT, draft.direction());
        assertEquals(15, draft.packFor(0).size());
        assertEquals(15, draft.picksOf(0).size());
        assertTrue(draft.history().contains(new RoundStarted(2, PassDirection.RIGHT)));
    }

    @Test
    void secondRound_packsGoToTheRight() {
        ClassicDraft draft = start();
        pickFirstAndLetOthersPlay(draft, 14);
        draft.handle(new PickCard(0, draft.packFor(0).get(0).name()));
        assertEquals(2, draft.round(), "seat 0's last pick of round 1 opens round 2");
        List<DraftCard> leftNeighbourPack = new ArrayList<>(draft.packFor(1));

        List<DraftEvent> events = draft.handle(new PickCard(0, draft.packFor(0).get(0).name()));
        assertEquals(new PackPassed(0, 7, 14), events.get(1));
        othersPlayUntilBlocked(draft, 0);

        assertEquals(14, draft.packFor(0).size());
        assertTrue(leftNeighbourPack.containsAll(draft.packFor(0)), "pack from seat 1 should come to seat 0 when passing right");
    }

    @Test
    void thirdRound_passesLeftAgain() {
        ClassicDraft draft = start();
        pickFirstAndLetOthersPlay(draft, 30);
        assertEquals(3, draft.round());
        assertEquals(PassDirection.LEFT, draft.direction());
    }

    @Test
    void afterFortyFivePicks_theDraftIsFinished() {
        ClassicDraft draft = start();
        pickFirstAndLetOthersPlay(draft, 45);

        assertTrue(draft.finished());
        assertEquals(new DraftFinished(), draft.history().get(draft.history().size() - 1));
        for (int seat = 0; seat < 8; seat++) assertEquals(45, draft.picksOf(seat).size());
        assertTrue(draft.packFor(0).isEmpty());
        assertEquals(3, draft.round());
        assertThrows(IllegalStateException.class, () -> draft.handle(new PickCard(0, "anything")));
        for (int seat = 0; seat < 8; seat++) assertFalse(draft.canAct(seat));
    }

    @Test
    void everyCardEndsUpPickedExactlyOnce() {
        ClassicDraft draft = start();
        playToTheEnd(draft);

        Set<String> all = new HashSet<>();
        for (int seat = 0; seat < 8; seat++) for (DraftCard c : draft.picksOf(seat)) assertTrue(all.add(c.name()));
        assertEquals(360, all.size());
    }

    @Test
    void picksAndPacksAreUnmodifiableViews() {
        ClassicDraft draft = start();
        assertThrows(UnsupportedOperationException.class, () -> draft.packFor(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> draft.picksOf(0).add(new DraftCard("x", null)));
        assertThrows(UnsupportedOperationException.class, () -> draft.history().clear());
    }

    @Test
    void replayingTheHistory_rebuildsTheSameDraft() {
        ClassicDraft draft = start();
        pickFirstAndLetOthersPlay(draft, 20);

        ClassicDraft replayed = (ClassicDraft) Draft.replay(draft.history());

        assertEquals(draft.history(), replayed.history());
        assertEquals(draft.round(), replayed.round());
        for (int seat = 0; seat < 8; seat++) {
            assertEquals(draft.picksOf(seat), replayed.picksOf(seat));
            assertEquals(draft.packFor(seat), replayed.packFor(seat));
            assertEquals(draft.queuedPacks(seat), replayed.queuedPacks(seat));
        }
    }
}
