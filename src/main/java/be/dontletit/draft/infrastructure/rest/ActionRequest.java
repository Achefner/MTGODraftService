package be.dontletit.draft.infrastructure.rest;

/**
 * `{"type": "PICK", "cardName": "...", "expectedMoves": 3}`, `{"type": "TAKE_PILE"}` or
 * `{"type": "PASS_PILE"}`. `expectedMoves` is optional: the `moves` of the view the player acted on.
 */
public record ActionRequest(String type, String cardName, Integer expectedMoves) {}
