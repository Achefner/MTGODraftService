package be.dontletit.draft.domain.event;

import be.dontletit.draft.domain.DraftCard;

import java.util.List;

/** Rochester: {@code opener} lays booster {@code packNumber} of the round face up on the table. */
public record PackOpened(int round, int packNumber, int opener, List<DraftCard> cards) implements DraftEvent {
    public PackOpened {
        cards = List.copyOf(cards);
    }
}
