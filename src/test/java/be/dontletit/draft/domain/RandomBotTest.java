package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.PickCard;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RandomBotTest {

    @Test
    void choose_returnsOneOfTheSeatsLegalCommands_atRandom() {
        Draft draft = DraftFixtures.start(DraftFormat.CLASSIC, 1L);
        RandomBot bot = new RandomBot(new Random(9L));

        Set<String> chosen = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            PickCard pick = (PickCard) bot.choose(draft, 4);
            assertEquals(4, pick.seat());
            assertTrue(draft.legalCommands(4).contains(pick));
            chosen.add(pick.cardName());
        }
        assertTrue(chosen.size() > 1, "not always the same card");
    }

    @Test
    void choose_whenTheSeatCannotAct_fails() {
        Draft draft = DraftFixtures.start(DraftFormat.ROCHESTER, 1L);
        assertThrows(IllegalStateException.class, () -> new RandomBot(new Random(1L)).choose(draft, 3));
    }

    @Test
    void playUntilBlocked_inClassic_everyBotPicksUntilTheyWaitForTheHuman() {
        ClassicDraft draft = (ClassicDraft) DraftFixtures.start(DraftFormat.CLASSIC, 1L);

        new RandomBot(new Random(2L)).playUntilBlocked(draft, 0);

        for (int seat = 1; seat < 8; seat++) assertFalse(draft.canAct(seat));
        assertTrue(draft.canAct(0));
        assertEquals(0, draft.picksOf(0).size());
        assertEquals(8, draft.queuedPacks(0), "every pack has gone round the table to seat 0");
    }

    @Test
    void playUntilBlocked_inRochester_playsUntilItIsTheHumansTurn() {
        RochesterDraft draft = (RochesterDraft) DraftFixtures.start(DraftFormat.ROCHESTER, 1L);
        draft.handle(new PickCard(0, draft.table().get(0).name()));

        new RandomBot(new Random(2L)).playUntilBlocked(draft, 0);

        assertEquals(0, draft.activeSeat());
        assertEquals(2, draft.packNumber(), "seat 0 picks only once from the booster it opened");
        assertEquals(8, draft.pickInPack(), "booster 2 is opened by seat 1: seats 1 to 7 pick, then seat 0");
    }
}
