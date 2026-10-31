package be.dontletit.draft.api;

/** A seat at the table; {@code you} marks the player's own seat. */
public record SeatView(int seat, boolean you, int pickCount) {}
