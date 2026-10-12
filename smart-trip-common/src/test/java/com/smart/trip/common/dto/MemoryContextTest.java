package com.smart.trip.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MemoryContext} 单测（C-015 T-2）。
 */
class MemoryContextTest {

    @Test
    @DisplayName("EMPTY 常量：三类列表为空、非降级、isEmpty=true")
    void empty_isEmptyTrue() {
        MemoryContext ctx = MemoryContext.EMPTY;
        assertTrue(ctx.isEmpty());
        assertFalse(ctx.memoryFallback());
        assertTrue(ctx.userProfiles().isEmpty());
        assertTrue(ctx.historyPlans().isEmpty());
        assertTrue(ctx.attractionKb().isEmpty());
    }

    @Test
    @DisplayName("FALLBACK 常量：三类列表为空、降级标记为 true")
    void fallback_memoryFallbackTrue() {
        MemoryContext ctx = MemoryContext.FALLBACK;
        assertTrue(ctx.isEmpty());
        assertTrue(ctx.memoryFallback());
    }

    @Test
    @DisplayName("含有效记忆时 isEmpty=false")
    void withProfiles_isEmptyFalse() {
        MemoryContext ctx = new MemoryContext(List.of("偏好海鲜"), List.of(), null, false);
        assertFalse(ctx.isEmpty());
    }

    @Test
    @DisplayName("null 列表安全归一为空列表")
    void nullLists_normalizedToEmpty() {
        MemoryContext ctx = new MemoryContext(null, null, null, false);
        assertTrue(ctx.userProfiles().isEmpty());
        assertTrue(ctx.historyPlans().isEmpty());
        assertTrue(ctx.attractionKb().isEmpty());
        assertTrue(ctx.isEmpty());
    }

    @Test
    @DisplayName("列表不可变：外部修改不影响内部")
    void lists_areImmutable() {
        var profiles = new java.util.ArrayList<>(List.of("a", "b"));
        MemoryContext ctx = new MemoryContext(profiles, List.of(), List.of(), false);
        profiles.add("c");
        assertEquals(2, ctx.userProfiles().size());
    }
}
