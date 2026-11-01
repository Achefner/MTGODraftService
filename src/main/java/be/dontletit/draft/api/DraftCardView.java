package be.dontletit.draft.api;

/** A card: its name, its picture and its mana value (null when unknown). */
public record DraftCardView(String name, String imageUrl, Integer manaValue) {}
