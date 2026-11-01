package be.dontletit.draft.domain;

/**
 * A card as the draft sees it: its name, the picture to show and its mana value, used to sort
 * picks. The mana value is null when unknown (cards stored before it was recorded).
 */
public record DraftCard(String name, String imageUrl, Integer manaValue) {

    /** A card whose mana value is unknown. */
    public DraftCard(String name, String imageUrl) {
        this(name, imageUrl, null);
    }
}
