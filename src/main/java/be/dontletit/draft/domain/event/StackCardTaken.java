package be.dontletit.draft.domain.event;

import be.dontletit.draft.domain.DraftCard;

/** Winston: after passing the last pile, a seat took the top card of the stack. */
public record StackCardTaken(int seat, DraftCard card) implements DraftEvent {}
