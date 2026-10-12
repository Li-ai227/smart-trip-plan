package com.smart.trip.skills.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * {@link TimeoutPolicy} 单测（C-005 增量 B 阶段 ③ 补齐）。
 */
class TimeoutPolicyTest {

    @Test
    void defaultPolicy_returnsThirtySeconds() {
        TimeoutPolicy policy = TimeoutPolicy.defaultPolicy();
        assertEquals(30_000L, policy.timeoutMillis());
    }

    @Test
    void customTimeout_preservesValue() {
        TimeoutPolicy policy = new TimeoutPolicy(5_000L);
        assertEquals(5_000L, policy.timeoutMillis());
    }

    @Test
    void zeroTimeout_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new TimeoutPolicy(0));
    }

    @Test
    void negativeTimeout_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new TimeoutPolicy(-1));
    }
}
