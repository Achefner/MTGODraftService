package be.dontletit.draft.domain.event;

import be.dontletit.draft.domain.DraftCard;

import java.util.List;

/** Winston: a seat took pile {@code pile} (0-based); only the taker knows its cards. */
public record PileTaken(int seat, int pile, List<DraftCard> cards) implements DraftEvent {
    public PileTaken {
        cards = List.copyOf(cards);
    }
}
