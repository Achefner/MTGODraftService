package be.dontletit.draft.domain.event;

import be.dontletit.draft.domain.DraftCard;

/** A seat took a card. {@code seenByAll} is true in Rochester, where picks are made from the table. */
public record CardPicked(int seat, DraftCard card, boolean seenByAll) implements DraftEvent {}
