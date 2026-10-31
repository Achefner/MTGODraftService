package be.dontletit.draft.api;

/**
 * A seat at the table. {@code kind} is HUMAN, BOT or OPEN (not taken yet); {@code name} is the
 * player's name for humans; {@code connected} tells whether a human is following the draft right now.
 */
public record SeatView(int seat, boolean you, String name, String kind, boolean connected, int pickCount) {}
