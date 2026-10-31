package be.dontletit.draft.spi;

import java.time.Duration;
import java.time.Instant;

/** The clock and the alarm the pick timers run on. */
public interface TimerSpiPort {

    Instant now();

    /** Runs the task once after the delay, unless cancelled first. */
    Cancellable schedule(Duration delay, Runnable task);

    @FunctionalInterface
    interface Cancellable {
        void cancel();
    }
}
