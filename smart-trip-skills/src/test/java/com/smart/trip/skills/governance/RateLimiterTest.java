package com.smart.trip.skills.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.exception.RateLimitedException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RateLimiter} 单测：滑动窗口限频（数据模型.md §4 / 接口协议.md §4）。
 *
 * <p>对齐外部 API 限频：小红书 ≤10/min、地图 ≤30/min、通义 ≤60/min；命中抛
 * {@link RateLimitedException}（接口协议.md §1.1，429）。窗口到期后计数重置。</p>
 */
class RateLimiterTest {

    /** 可控时钟，驱动 {@link InMemoryRateLimitStore} 的 EXPIRE 语义（测试确定性）。 */
    private final AtomicLong clock = new AtomicLong(0L);

    private RateLimiter limiterWithDefaults() {
        return RateLimiter.withDefaults(new InMemoryRateLimitStore(clock::get));
    }

    @Test
    @DisplayName("窗口内额度足够：放行")
    void allowsWithinLimit() {
        RateLimiter limiter = limiterWithDefaults();
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire(RateLimiter.API_XHS));
        }
    }

    @Test
    @DisplayName("超过窗口额度：拒绝并抛 RateLimitedException")
    void rejectsWhenExceeded() {
        RateLimiter limiter = limiterWithDefaults();
        for (int i = 0; i < 10; i++) {
            limiter.acquireOrThrow(RateLimiter.API_XHS);
        }
        assertFalse(limiter.tryAcquire(RateLimiter.API_XHS));
        assertThrows(RateLimitedException.class, () -> limiter.acquireOrThrow(RateLimiter.API_XHS));
    }

    @Test
    @DisplayName("窗口到期后计数重置，重新放行")
    void resetsAfterWindow() {
        RateLimiter limiter = limiterWithDefaults();
        for (int i = 0; i < 10; i++) {
            limiter.acquireOrThrow(RateLimiter.API_XHS);
        }
        assertFalse(limiter.tryAcquire(RateLimiter.API_XHS));

        clock.addAndGet(61_000L); // 推进超过 60s 窗口
        assertTrue(limiter.tryAcquire(RateLimiter.API_XHS));
    }

    @Test
    @DisplayName("地图限频 30/min 独立于小红书窗口")
    void perApiWindowsAreIndependent() {
        RateLimiter limiter = limiterWithDefaults();
        for (int i = 0; i < 30; i++) {
            assertTrue(limiter.tryAcquire(RateLimiter.API_MAP));
        }
        assertFalse(limiter.tryAcquire(RateLimiter.API_MAP));
        // 小红书窗口未受影响
        assertTrue(limiter.tryAcquire(RateLimiter.API_XHS));
    }

    @Test
    @DisplayName("未配置规则的 API 直接放行（向后兼容）")
    void unknownApiPassesThrough() {
        RateLimiter limiter = limiterWithDefaults();
        assertTrue(limiter.tryAcquire("some-unconfigured-api"));
    }
}
