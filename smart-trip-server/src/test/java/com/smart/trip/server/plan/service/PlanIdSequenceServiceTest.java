package com.smart.trip.server.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * {@link PlanIdSequenceService} 单测（C-017 T-9）。
 */
class PlanIdSequenceServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private PlanIdSequenceService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new PlanIdSequenceService(redisTemplate);
    }

    @Nested
    @DisplayName("Redis 可用")
    class RedisAvailable {

        @Test
        @DisplayName("首次调用 Redis INCR 返回 1")
        void firstCallReturnsOne() {
            when(valueOps.increment(anyString())).thenReturn(1L);
            int seq = service.nextSequence(LocalDate.of(2026, 7, 4));
            assertEquals(1, seq);
        }

        @Test
        @DisplayName("连续调用返回递增序列号")
        void consecutiveCallsIncrement() {
            when(valueOps.increment(anyString())).thenReturn(1L, 2L, 3L);
            assertEquals(1, service.nextSequence(LocalDate.of(2026, 7, 4)));
            assertEquals(2, service.nextSequence(LocalDate.of(2026, 7, 4)));
            assertEquals(3, service.nextSequence(LocalDate.of(2026, 7, 4)));
        }

        @Test
        @DisplayName("Redis key 格式正确")
        void keyFormat() {
            when(valueOps.increment(anyString())).thenReturn(1L);
            service.nextSequence(LocalDate.of(2026, 7, 4));
            verify(valueOps).increment("trip:plan:seq:20260704");
        }

        @Test
        @DisplayName("首次 INCR 时设置 TTL 48h")
        void setsTtlOnFirstIncr() {
            when(valueOps.increment(anyString())).thenReturn(1L);
            service.nextSequence(LocalDate.of(2026, 7, 4));
            verify(redisTemplate).expire(eq("trip:plan:seq:20260704"), eq(Duration.ofHours(48)));
        }
    }

    @Nested
    @DisplayName("Redis 不可用（降级内存）")
    class RedisFallback {

        @Test
        @DisplayName("Redis 异常时退化为内存自增，不抛异常")
        void fallbackToMemory() {
            when(valueOps.increment(anyString()))
                    .thenThrow(new RedisConnectionFailureException("连接失败"));
            int seq = service.nextSequence(LocalDate.of(2026, 7, 4));
            assertTrue(seq >= 1);
        }

        @Test
        @DisplayName("Redis 不可用时内存自增连续")
        void memoryIncrements() {
            when(valueOps.increment(anyString()))
                    .thenThrow(new RedisConnectionFailureException("连接失败"));
            int seq1 = service.nextSequence(LocalDate.of(2026, 7, 4));
            int seq2 = service.nextSequence(LocalDate.of(2026, 7, 4));
            assertEquals(seq1 + 1, seq2);
        }
    }

    @Nested
    @DisplayName("跨日重置")
    class CrossDayReset {

        @Test
        @DisplayName("日期变化时序列号重置")
        void resetOnDateChange() {
            when(valueOps.increment(anyString())).thenReturn(5L, 1L);
            service.nextSequence(LocalDate.of(2026, 7, 4));
            int seq = service.nextSequence(LocalDate.of(2026, 7, 5));
            assertEquals(1, seq);
        }
    }

    @Nested
    @DisplayName("边界与降级（C-017 T-7）")
    class EdgeAndDegradation {

        @Test
        @DisplayName("INCR 返回 null 时退化为内存自增")
        void fallbackWhenIncrReturnsNull() {
            when(valueOps.increment(anyString())).thenReturn(null);
            int seq = service.nextSequence(LocalDate.of(2026, 7, 4));
            assertTrue(seq >= 1);
        }

        @Test
        @DisplayName("redisTemplate 为 null 时构造不抛异常，调用退化为内存自增")
        void nullRedisTemplateGracefulFallback() {
            PlanIdSequenceService nullRedisService = new PlanIdSequenceService(null);
            int seq = nullRedisService.nextSequence(LocalDate.of(2026, 7, 4));
            assertTrue(seq >= 1);
        }

        @Test
        @DisplayName("序列号上限（999）仍正常工作")
        void sequenceNearMaxWorks() {
            when(valueOps.increment(anyString())).thenReturn(999L);
            int seq = service.nextSequence(LocalDate.of(2026, 7, 4));
            assertEquals(999, seq);
        }
    }
}
