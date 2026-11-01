package be.dontletit.draft.application;

import be.dontletit.draft.api.DraftCardView;
import be.dontletit.draft.api.DraftEventView;
import be.dontletit.draft.domain.DraftCard;
import be.dontletit.draft.domain.event.BoosterDraftStarted;
import be.dontletit.draft.domain.event.CardPicked;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.domain.event.DraftFinished;
import be.dontletit.draft.domain.event.PackOpened;
import be.dontletit.draft.domain.event.PackPassed;
import be.dontletit.draft.domain.event.PilePassed;
import be.dontletit.draft.domain.event.PileTaken;
import be.dontletit.draft.domain.event.RoundStarted;
import be.dontletit.draft.domain.event.StackCardTaken;
import be.dontletit.draft.domain.event.WinstonDraftStarted;

import java.util.List;
import java.util.Optional;

/**
 * What one seat may see of a draft event. Hidden information never leaves: the dealt cards,
 * other seats' Classic picks and the contents of a Winston pile or stack card taken by someone else.
 * Events that mean nothing to the seat are left out.
 */
public final class EventProjection {

    private EventProjection() {}

    public static Optional<DraftEventView> forSeat(DraftEvent event, int viewer) {
        return Optional.ofNullable(switch (event) {
            case BoosterDraftStarted ignored -> simple("DRAFT_STARTED", null, null, null, List.of());
            case WinstonDraftStarted started -> simple("DRAFT_STARTED", started.firstSeat(), null, null, List.of());
            case RoundStarted started -> new DraftEventView("ROUND_STARTED", null, started.round(), started.direction().name(), null, null, null, List.of());
            case PackOpened opened -> new DraftEventView("PACK_OPENED", opened.opener(), opened.round(), null, opened.packNumber(), null, opened.cards().size(), List.of());
            case CardPicked picked -> picked.seenByAll() || picked.seat() == viewer
                    ? simple("CARD_PICKED", picked.seat(), null, 1, cards(List.of(picked.card())))
                    : null;
            case PackPassed ignored -> null;
            case PileTaken taken -> simple("PILE_TAKEN", taken.seat(), taken.pile() + 1, taken.cards().size(),
                    taken.seat() == viewer ? cards(taken.cards()) : List.of());
            case PilePassed passed -> simple("PILE_PASSED", passed.seat(), passed.pile() + 1, null, List.of());
            case StackCardTaken taken -> simple("STACK_CARD_TAKEN", taken.seat(), null, 1,
                    taken.seat() == viewer ? cards(List.of(taken.card())) : List.of());
            case DraftFinished ignored -> simple("DRAFT_FINISHED", null, null, null, List.of());
        });
    }

    private static DraftEventView simple(String type, Integer seat, Integer pile, Integer cardCount, List<DraftCardView> cards) {
        return new DraftEventView(type, seat, null, null, null, pile, cardCount, cards);
    }

    static List<DraftCardView> cards(List<DraftCard> cards) {
        return cards.stream().map(card -> new DraftCardView(card.name(), card.imageUrl(), card.manaValue())).toList();
    }
}
