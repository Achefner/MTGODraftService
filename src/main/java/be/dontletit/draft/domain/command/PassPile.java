package be.dontletit.draft.domain.command;

/** Winston: leave the pile the seat is looking at, adding a card from the stack to it. */
public record PassPile(int seat) implements DraftCommand {}
