package be.dontletit.draft.domain.command;

/** Classic and Rochester: take one card from the pack in front of the seat (or on the table). */
public record PickCard(int seat, String cardName) implements DraftCommand {}
