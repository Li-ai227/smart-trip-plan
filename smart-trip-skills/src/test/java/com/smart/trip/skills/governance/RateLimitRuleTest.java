package com.smart.trip.skills.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * {@link RateLimitRule} 单测（C-005 增量 B 阶段 ③ 补齐）。
 */
class RateLimitRuleTest {

    @Test
    void validRule_preservesFields() {
        RateLimitRule rule = new RateLimitRule("xhs", 10, 60);
        assertEquals("xhs", rule.api());
        assertEquals(10, rule.maxPerWindow());
        assertEquals(60, rule.windowSeconds());
    }

    @Test
    void zeroMaxPerWindow_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitRule("xhs", 0, 60));
    }

    @Test
    void negativeMaxPerWindow_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitRule("xhs", -1, 60));
    }

    @Test
    void zeroWindowSeconds_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitRule("xhs", 10, 0));
    }

    @Test
    void negativeWindowSeconds_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitRule("xhs", 10, -1));
    }
}
