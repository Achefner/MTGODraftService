package be.dontletit.draft.domain;

/** A card as the draft sees it: its name and the picture to show. */
public record DraftCard(String name, String imageUrl) {}
