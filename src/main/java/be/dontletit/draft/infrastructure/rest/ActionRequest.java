package be.dontletit.draft.infrastructure.rest;

/** `{"type": "PICK", "cardName": "..."}`, `{"type": "TAKE_PILE"}` or `{"type": "PASS_PILE"}`. */
public record ActionRequest(String type, String cardName) {}
