package be.dontletit.draft.domain.event;

/** Classic: a seat passed the rest of its pack to a neighbour. */
public record PackPassed(int fromSeat, int toSeat, int cardsLeft) implements DraftEvent {}
