package be.dontletit.draft.domain.command;

/** Winston: take every card of the pile the seat is looking at. */
public record TakePile(int seat) implements DraftCommand {}
