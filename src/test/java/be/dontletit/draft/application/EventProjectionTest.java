package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftCardView;
import be.dontletit.draft.api.DraftEventView;
import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.domain.DraftFormat;
import be.dontletit.draft.domain.PassDirection;
import be.dontletit.draft.domain.event.BoosterDraftStarted;
import be.dontletit.draft.domain.event.CardPicked;
import be.dontletit.draft.domain.event.DraftFinished;
import be.dontletit.draft.domain.event.PackOpened;
import be.dontletit.draft.domain.event.PackPassed;
import be.dontletit.draft.domain.event.PilePassed;
import be.dontletit.draft.domain.event.PileTaken;
import be.dontletit.draft.domain.event.RoundStarted;
import be.dontletit.draft.domain.event.StackCardTaken;
import be.dontletit.draft.domain.event.WinstonDraftStarted;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EventProjectionTest {

    static final DraftCard BOLT = new DraftCard("Lightning Bolt", "https://img/bolt.jpg");
    static final DraftCard OKO = new DraftCard("Oko, Thief of Crowns", "https://img/oko.jpg");
    static final DraftCardView BOLT_VIEW = new DraftCardView("Lightning Bolt", "https://img/bolt.jpg");
    static final DraftCardView OKO_VIEW = new DraftCardView("Oko, Thief of Crowns", "https://img/oko.jpg");

    static DraftEventView seen(Object event, int viewer) {
        return EventProjection.forSeat((be.dontletit.draft.domain.event.DraftEvent) event, viewer).orElseThrow();
    }

    static DraftEventView event(String type, Integer seat, Integer pile, Integer cardCount, List<DraftCardView> cards) {
        return new DraftEventView(type, seat, null, null, null, pile, cardCount, cards);
    }

    @Test
    void starts_neverRevealTheCards() {
        assertEquals(event("DRAFT_STARTED", null, null, null, List.of()),
                seen(new BoosterDraftStarted("d", DraftFormat.CLASSIC, List.of(List.of(BOLT))), 0));
        assertEquals(event("DRAFT_STARTED", 1, null, null, List.of()),
                seen(new WinstonDraftStarted("d", List.of(BOLT, OKO), 1), 0));
    }

    @Test
    void roundsAndOpenedPacks_arePublic() {
        assertEquals(new DraftEventView("ROUND_STARTED", null, 2, "RIGHT", null, null, null, List.of()),
                seen(new RoundStarted(2, PassDirection.RIGHT), 0));
        assertEquals(new DraftEventView("PACK_OPENED", 3, 1, null, 4, null, 2, List.of()),
                seen(new PackOpened(1, 4, 3, List.of(BOLT, OKO)), 0));
        assertEquals(event("DRAFT_FINISHED", null, null, null, List.of()), seen(new DraftFinished(), 0));
    }

    @Test
    void picks_ownAndFaceUpOnesAreShown_otherHiddenPicksAreLeftOut() {
        assertEquals(event("CARD_PICKED", 0, null, 1, List.of(BOLT_VIEW)), seen(new CardPicked(0, BOLT, false), 0));
        assertEquals(event("CARD_PICKED", 5, null, 1, List.of(BOLT_VIEW)), seen(new CardPicked(5, BOLT, true), 0));
        assertEquals(Optional.empty(), EventProjection.forSeat(new CardPicked(5, BOLT, false), 0));
        assertEquals(Optional.empty(), EventProjection.forSeat(new PackPassed(0, 1, 14), 0));
    }

    @Test
    void winstonPiles_onlyTheTakerSeesTheCards() {
        PileTaken taken = new PileTaken(1, 2, List.of(BOLT, OKO));
        assertEquals(event("PILE_TAKEN", 1, 3, 2, List.of(BOLT_VIEW, OKO_VIEW)), seen(taken, 1));
        assertEquals(event("PILE_TAKEN", 1, 3, 2, List.of()), seen(taken, 0));

        assertEquals(event("PILE_PASSED", 1, 1, null, List.of()), seen(new PilePassed(1, 0), 0));

        StackCardTaken top = new StackCardTaken(1, OKO);
        assertEquals(event("STACK_CARD_TAKEN", 1, null, 1, List.of(OKO_VIEW)), seen(top, 1));
        assertEquals(event("STACK_CARD_TAKEN", 1, null, 1, List.of()), seen(top, 0));
    }
}
