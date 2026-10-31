package be.dontletit.draft.api;

/** A taken seat. The token is shown only once: it is the key to the seat. */
public record SeatTicket(String draftId, int seat, String token, DraftView view) {}
