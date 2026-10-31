package be.dontletit.draft.stubs;

import be.dontletit.draft.spi.TimerSpiPort;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A clock that only moves when the test says so; due alarms run in deadline order. */
public class ManualTimer implements TimerSpiPort {

    private Instant now = Instant.parse("2026-11-01T10:00:00Z");
    private final List<Alarm> alarms = new ArrayList<>();

    private static final class Alarm {
        final Instant at;
        final Runnable task;
        boolean cancelled;

        Alarm(Instant at, Runnable task) {
            this.at = at;
            this.task = task;
        }
    }

    @Override
    public synchronized Instant now() {
        return now;
    }

    @Override
    public synchronized Cancellable schedule(Duration delay, Runnable task) {
        Alarm alarm = new Alarm(now.plus(delay), task);
        alarms.add(alarm);
        return () -> alarm.cancelled = true;
    }

    /** Moves the clock forward, running every alarm that falls due on the way. */
    public void advance(Duration duration) {
        Instant target = now.plus(duration);
        while (true) {
            Alarm next;
            synchronized (this) {
                next = alarms.stream().filter(a -> !a.cancelled && !a.at.isAfter(target))
                        .min(Comparator.comparing(a -> a.at)).orElse(null);
                if (next == null) {
                    now = target;
                    return;
                }
                alarms.remove(next);
                now = next.at;
            }
            next.task.run();
        }
    }

    public synchronized long pendingAlarms() {
        return alarms.stream().filter(a -> !a.cancelled).count();
    }
}
