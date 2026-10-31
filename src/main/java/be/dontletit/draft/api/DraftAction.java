package be.dontletit.draft.api;

/**
 * What the player does: {@code PICK} a card (Classic, Rochester, needs {@code cardName}),
 * {@code TAKE_PILE} or {@code PASS_PILE} (Winston). {@code expectedMoves} is the seat's
 * {@link DraftView#moves()} the client saw; when given, an outdated or repeated action is refused.
 */
public record DraftAction(String type, String cardName, Integer expectedMoves) {

    public static DraftAction pick(String cardName) {
        return new DraftAction("PICK", cardName, null);
    }
}
