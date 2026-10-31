package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.PickCard;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class PickTimerTest {

    static final PickTimer TIMER = PickTimer.DEFAULT;

    @Test
    void defaults_shrinkWithThePackLikeMagicOnline() {
        assertEquals(new PickTimer(5, 10, 40, 20, 30), TIMER);
    }

    @Test
    void classic_fiveSecondsPerCardInThePack_atLeastTen() {
        ClassicDraft draft = (ClassicDraft) DraftFixtures.start(DraftFormat.CLASSIC, 1L);
        assertEquals(Duration.ofSeconds(75), TIMER.timeFor(draft, 0));

        draft.handle(new PickCard(0, draft.packFor(0).get(0).name()));
        new RandomBot(new java.util.Random(1L)).playUntilBlocked(draft, java.util.Set.of(0));
        assertEquals(14, draft.packFor(0).size());
        assertEquals(Duration.ofSeconds(70), TIMER.timeFor(draft, 0));

        assertEquals(Duration.ofSeconds(10), new PickTimer(5, 10, 40, 20, 30).forPackSize(1));
        assertEquals(Duration.ofSeconds(10), new PickTimer(5, 10, 40, 20, 30).forPackSize(2));
    }

    @Test
    void rochester_moreTimeForTheFirstPickOfABoosterToReadIt() {
        RochesterDraft draft = (RochesterDraft) DraftFixtures.start(DraftFormat.ROCHESTER, 1L);
        assertEquals(Duration.ofSeconds(40), TIMER.timeFor(draft, 0));
        draft.handle(new PickCard(0, draft.table().get(0).name()));
        assertEquals(Duration.ofSeconds(20), TIMER.timeFor(draft, 1));
    }

    @Test
    void winston_sameTimeForEveryMove() {
        WinstonDraft draft = (WinstonDraft) DraftFixtures.start(DraftFormat.WINSTON, 1L);
        assertEquals(Duration.ofSeconds(30), TIMER.timeFor(draft, draft.activeSeat()));
    }

    @Test
    void settingsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new PickTimer(0, 10, 40, 20, 30));
        assertThrows(IllegalArgumentException.class, () -> new PickTimer(5, 10, 40, 20, -1));
    }
}
