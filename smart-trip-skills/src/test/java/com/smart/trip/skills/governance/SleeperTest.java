package com.smart.trip.skills.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link Sleeper} 单测（C-005 增量 B 阶段 ③ 补齐）。
 */
class SleeperTest {

    @Test
    void realSleeper_sleepsApproximatelyRequestedDuration() {
        Sleeper real = Sleeper.real();
        long start = System.nanoTime();
        real.sleep(50);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed >= 40, "should sleep at least ~50ms, got " + elapsed + "ms");
    }

    @Test
    void realSleeper_interruptedResetsFlag() {
        Sleeper real = Sleeper.real();
        Thread.currentThread().interrupt();
        real.sleep(5_000);
        assertTrue(Thread.interrupted(), "interrupt flag should be restored after InterruptedException");
    }
}
