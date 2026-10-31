package be.dontletit.draft.domain;

import be.dontletit.draft.domain.command.PassPile;
import be.dontletit.draft.domain.command.PickCard;
import be.dontletit.draft.domain.command.TakePile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A seat's move count tells which decision it is on, so a stale or repeated command can be refused. */
class MoveCountTest {

    @Test
    void classic_countsTheSeatsPicks() {
        ClassicDraft draft = (ClassicDraft) DraftFixtures.start(DraftFormat.CLASSIC, 1L);
        assertEquals(0, draft.moveCount(0));
        draft.handle(new PickCard(0, draft.packFor(0).get(0).name()));
        draft.handle(new PickCard(3, draft.packFor(3).get(0).name()));
        assertEquals(1, draft.moveCount(0));
        assertEquals(1, draft.moveCount(3));
        assertEquals(0, draft.moveCount(5));
    }

    @Test
    void rochester_countsTheSeatsPicks() {
        RochesterDraft draft = (RochesterDraft) DraftFixtures.start(DraftFormat.ROCHESTER, 1L);
        draft.handle(new PickCard(0, draft.table().get(0).name()));
        assertEquals(1, draft.moveCount(0));
        assertEquals(0, draft.moveCount(1));
    }

    @Test
    void winston_countsTakesAndPasses_notTheStackCardThatComesWithTheLastPass() {
        WinstonDraft draft = (WinstonDraft) DraftFixtures.start(DraftFormat.WINSTON, 1L);
        int seat = draft.activeSeat();
        draft.handle(new PassPile(seat));
        draft.handle(new PassPile(seat));
        draft.handle(new PassPile(seat));
        assertEquals(3, draft.moveCount(seat));
        draft.handle(new TakePile(1 - seat));
        assertEquals(1, draft.moveCount(1 - seat));
    }
}
