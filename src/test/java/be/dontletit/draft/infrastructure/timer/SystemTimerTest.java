package be.dontletit.draft.infrastructure.timer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SystemTimerTest {

    final SystemTimer timer = new SystemTimer();

    @AfterEach
    void stop() {
        timer.shutdown();
    }

    @Test
    void now_isTheWallClock() {
        assertTrue(Duration.between(timer.now(), Instant.now()).abs().toSeconds() < 2);
    }

    @Test
    void schedule_runsTheTaskAfterTheDelay() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);
        timer.schedule(Duration.ofMillis(50), ran::countDown);
        assertTrue(ran.await(2, TimeUnit.SECONDS));
    }

    @Test
    void cancel_preventsTheTask() throws InterruptedException {
        AtomicBoolean ran = new AtomicBoolean();
        timer.schedule(Duration.ofMillis(100), () -> ran.set(true)).cancel();
        Thread.sleep(300);
        assertFalse(ran.get());
    }

    @Test
    void aFailingTask_doesNotStopTheTimer() throws InterruptedException {
        timer.schedule(Duration.ofMillis(10), () -> { throw new IllegalStateException("boom"); });
        CountDownLatch ran = new CountDownLatch(1);
        timer.schedule(Duration.ofMillis(50), ran::countDown);
        assertTrue(ran.await(2, TimeUnit.SECONDS));
    }
}
