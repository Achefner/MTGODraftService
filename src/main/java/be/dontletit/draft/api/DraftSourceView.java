package be.dontletit.draft.api;

/** A cube collection a draft can be started from. */
public record DraftSourceView(String id, String name, int cardCount) {}
