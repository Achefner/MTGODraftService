package be.dontletit.draft.api;

/** A draft format the client can offer. {@code name} is what a start request expects. */
public record DraftFormatView(String name, String displayName, int seats, int cardsNeeded, String description) {}
