package com.smart.trip.agent.route.mcp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VendorHealth} 熔断状态机单测（C-007 增量 B / AC-9c）。
 */
class VendorHealthTest {

    private final AtomicLong clock = new AtomicLong(0L);

    private VendorHealth create() {
        return new VendorHealth(VendorId.BAIDU,
                VendorHealth.DEFAULT_FAILURE_THRESHOLD,
                VendorHealth.DEFAULT_COOLDOWN_MILLIS,
                clock::get);
    }

    @Test
    void initial_state_is_closed() {
        VendorHealth h = create();

        assertEquals(VendorHealth.State.CLOSED, h.state());
        assertTrue(h.isAvailable());
    }

    @Test
    void single_failure_keeps_closed() {
        VendorHealth h = create();

        h.recordFailure();

        assertEquals(VendorHealth.State.CLOSED, h.state());
        assertTrue(h.isAvailable());
    }

    @Test
    void consecutive_failures_trigger_open() {
        VendorHealth h = create();

        for (int i = 0; i < VendorHealth.DEFAULT_FAILURE_THRESHOLD; i++) {
            h.recordFailure();
        }

        assertEquals(VendorHealth.State.OPEN, h.state());
        assertFalse(h.isAvailable());
    }

    @Test
    void open_becomes_half_open_after_cooldown() {
        VendorHealth h = create();
        for (int i = 0; i < VendorHealth.DEFAULT_FAILURE_THRESHOLD; i++) {
            h.recordFailure();
        }

        clock.set(VendorHealth.DEFAULT_COOLDOWN_MILLIS);
        assertTrue(h.isAvailable());
        assertEquals(VendorHealth.State.HALF_OPEN, h.state());
    }

    @Test
    void half_open_probe_success_closes() {
        VendorHealth h = create();
        for (int i = 0; i < VendorHealth.DEFAULT_FAILURE_THRESHOLD; i++) {
            h.recordFailure();
        }
        clock.set(VendorHealth.DEFAULT_COOLDOWN_MILLIS);
        h.isAvailable();

        h.recordSuccess();

        assertEquals(VendorHealth.State.CLOSED, h.state());
    }

    @Test
    void half_open_probe_failure_reopens() {
        VendorHealth h = create();
        for (int i = 0; i < VendorHealth.DEFAULT_FAILURE_THRESHOLD; i++) {
            h.recordFailure();
        }
        clock.set(VendorHealth.DEFAULT_COOLDOWN_MILLIS);
        h.isAvailable();

        h.recordFailure();

        assertEquals(VendorHealth.State.OPEN, h.state());
        assertFalse(h.isAvailable());
    }

    @Test
    void success_resets_consecutive_failures() {
        VendorHealth h = create();
        h.recordFailure();
        h.recordFailure();

        h.recordSuccess();

        h.recordFailure();
        h.recordFailure();
        assertEquals(VendorHealth.State.CLOSED, h.state());
    }

    @Test
    void vendor_accessor() {
        VendorHealth h = create();

        assertEquals(VendorId.BAIDU, h.vendor());
    }

    @Test
    void not_available_during_cooldown() {
        VendorHealth h = create();
        for (int i = 0; i < VendorHealth.DEFAULT_FAILURE_THRESHOLD; i++) {
            h.recordFailure();
        }

        clock.set(VendorHealth.DEFAULT_COOLDOWN_MILLIS - 1);

        assertFalse(h.isAvailable());
        assertEquals(VendorHealth.State.OPEN, h.state());
    }
}
