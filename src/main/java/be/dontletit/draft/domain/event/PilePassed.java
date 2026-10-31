package be.dontletit.draft.domain.event;

/** Winston: a seat passed pile {@code pile} (0-based); the top card of the stack, if any, went onto it. */
public record PilePassed(int seat, int pile) implements DraftEvent {}
