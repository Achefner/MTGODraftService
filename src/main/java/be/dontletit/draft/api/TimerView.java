package be.dontletit.draft.api;

import java.time.Instant;

/**
 * The seat's pick clock: when time runs out, how long the decision was given, and how much was
 * left when the view was made (count down from this one: it does not depend on the browser's clock).
 */
public record TimerView(Instant deadline, int seconds, long remainingMillis) {}
