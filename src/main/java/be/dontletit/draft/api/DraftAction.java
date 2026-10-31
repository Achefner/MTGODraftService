package be.dontletit.draft.api;

/**
 * What the player does: {@code PICK} a card (Classic, Rochester, needs {@code cardName}),
 * {@code TAKE_PILE} or {@code PASS_PILE} (Winston).
 */
public record DraftAction(String type, String cardName) {

    public static DraftAction pick(String cardName) {
        return new DraftAction("PICK", cardName);
    }
}
