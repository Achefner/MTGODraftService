package be.dontletit.draft.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class DraftTest {

    static List<DraftCard> pool(int size) {
        return IntStream.range(0, size).mapToObj(i -> new DraftCard("Card " + i, "https://img/" + i + ".jpg")).toList();
    }

    static Draft start(int poolSize, long seed) {
        return Draft.start("d1", pool(poolSize), new Random(seed));
    }

    static Draft start() {
        return start(Draft.CARDS_NEEDED, 42L);
    }

    @Test
    void constants_matchABoosterDraft() {
        assertEquals(8, Draft.SEATS);
        assertEquals(3, Draft.ROUNDS);
        assertEquals(15, Draft.PACK_SIZE);
        assertEquals(360, Draft.CARDS_NEEDED);
        assertEquals(0, Draft.HUMAN_SEAT);
    }

    @Test
    void start_needsEnoughCards() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> start(359, 1L));
        assertTrue(e.getMessage().contains("360"));
    }

    @Test
    void start_dealsFifteenDistinctCardsToEverySeat() {
        Draft draft = start();

        Set<String> seen = new HashSet<>();
        for (int seat = 0; seat < Draft.SEATS; seat++) {
            List<DraftCard> pack = draft.packAt(seat);
            assertEquals(15, pack.size());
            for (DraftCard card : pack) assertTrue(seen.add(card.name()), "dealt twice: " + card.name());
        }
        assertEquals(120, seen.size());
    }

    @Test
    void start_withABiggerPoolUsesASubsetOfIt() {
        Draft draft = start(540, 7L);
        assertEquals(15, draft.currentPack().size());
    }

    @Test
    void start_isDeterministicForASeed() {
        assertEquals(start(360, 5L).currentPack(), start(360, 5L).currentPack());
        assertNotEquals(start(360, 5L).currentPack(), start(360, 6L).currentPack());
    }

    @Test
    void initialState() {
        Draft draft = start();
        assertEquals("d1", draft.id());
        assertEquals(1, draft.round());
        assertEquals(1, draft.pickNumber());
        assertEquals(PassDirection.LEFT, draft.direction());
        assertEquals(15, draft.currentPack().size());
        assertTrue(draft.picks().isEmpty());
        assertFalse(draft.finished());
    }

    @Test
    void pick_takesTheCardAndReceivesTheRightNeighboursPackWhenPassingLeft() {
        Draft draft = start();
        List<DraftCard> myPack = new ArrayList<>(draft.currentPack());
        List<DraftCard> rightNeighbourPack = new ArrayList<>(draft.packAt(Draft.SEATS - 1));
        DraftCard choice = myPack.get(3);

        draft.pick(choice.name());

        assertEquals(List.of(choice), draft.picks());
        assertEquals(2, draft.pickNumber());
        List<DraftCard> received = draft.currentPack();
        assertEquals(14, received.size());
        assertTrue(rightNeighbourPack.containsAll(received), "pack 7 should have been passed to seat 0");
        // seat 1 now holds what was my pack, minus my pick
        List<DraftCard> passedOn = draft.packAt(1);
        assertEquals(14, passedOn.size());
        assertFalse(passedOn.contains(choice));
        assertTrue(myPack.containsAll(passedOn));
    }

    @Test
    void pick_everyBotTakesExactlyOneCard() {
        Draft draft = start();
        draft.pick(draft.currentPack().get(0).name());

        int remaining = IntStream.range(0, Draft.SEATS).map(seat -> draft.packAt(seat).size()).sum();
        assertEquals(120 - 8, remaining);
        for (int seat = 1; seat < Draft.SEATS; seat++) assertEquals(1, draft.picksOf(seat).size());
    }

    @Test
    void pick_unknownCard_rejectedAndNothingChanges() {
        Draft draft = start();
        List<DraftCard> before = draft.currentPack();

        assertThrows(IllegalArgumentException.class, () -> draft.pick("Not In Pack"));
        assertThrows(IllegalArgumentException.class, () -> draft.pick(null));

        assertEquals(before, draft.currentPack());
        assertEquals(1, draft.pickNumber());
        assertTrue(draft.picks().isEmpty());
    }

    @Test
    void pick_isCaseInsensitive() {
        Draft draft = start();
        DraftCard choice = draft.currentPack().get(0);
        draft.pick(choice.name().toUpperCase());
        assertEquals(List.of(choice), draft.picks());
    }

    static void pickFirst(Draft draft, int times) {
        for (int i = 0; i < times; i++) draft.pick(draft.currentPack().get(0).name());
    }

    @Test
    void afterFifteenPicks_secondRoundOpensNewPacksPassingRight() {
        Draft draft = start();
        pickFirst(draft, 14);
        assertEquals(1, draft.currentPack().size());
        assertEquals(15, draft.pickNumber());

        pickFirst(draft, 1);

        assertEquals(2, draft.round());
        assertEquals(1, draft.pickNumber());
        assertEquals(PassDirection.RIGHT, draft.direction());
        assertEquals(15, draft.currentPack().size());
        assertEquals(15, draft.picks().size());
        for (int seat = 0; seat < Draft.SEATS; seat++) assertEquals(15, draft.packAt(seat).size());
    }

    @Test
    void secondRound_packsGoToTheLeftNeighbour() {
        Draft draft = start();
        pickFirst(draft, 15);
        List<DraftCard> leftNeighbourPack = new ArrayList<>(draft.packAt(1));

        pickFirst(draft, 1);

        assertEquals(14, draft.currentPack().size());
        assertTrue(leftNeighbourPack.containsAll(draft.currentPack()), "pack from seat 1 should come to seat 0 when passing right");
    }

    @Test
    void thirdRound_passesLeftAgain() {
        Draft draft = start();
        pickFirst(draft, 30);
        assertEquals(3, draft.round());
        assertEquals(PassDirection.LEFT, draft.direction());
    }

    @Test
    void afterFortyFivePicks_theDraftIsFinished() {
        Draft draft = start();
        pickFirst(draft, 45);

        assertTrue(draft.finished());
        assertEquals(45, draft.picks().size());
        assertEquals(45, new HashSet<>(draft.picks()).size());
        for (int seat = 1; seat < Draft.SEATS; seat++) assertEquals(45, draft.picksOf(seat).size());
        assertTrue(draft.currentPack().isEmpty());
        assertEquals(3, draft.round());
        assertThrows(IllegalStateException.class, () -> draft.pick("anything"));
    }

    @Test
    void everyCardEndsUpPickedExactlyOnce() {
        Draft draft = start();
        pickFirst(draft, 45);

        Set<String> all = new HashSet<>();
        for (int seat = 0; seat < Draft.SEATS; seat++) for (DraftCard c : draft.picksOf(seat)) assertTrue(all.add(c.name()));
        assertEquals(360, all.size());
    }

    @Test
    void packsAreUnmodifiableViews() {
        Draft draft = start();
        assertThrows(UnsupportedOperationException.class, () -> draft.currentPack().clear());
        assertThrows(UnsupportedOperationException.class, () -> draft.picks().add(new DraftCard("x", null)));
    }
}
