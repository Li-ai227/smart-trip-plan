package com.smart.trip.skills.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.exception.RateLimitedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link GovernedExternalCaller} 单测：统一治理链 限频 → 超时/重试退避 → 失败降级（C-005 AC-4/AC-5）。
 *
 * <p>覆盖验收用例 Case-2：被调方持续超时 → 重试 3 次（退避 1/2/4s）后降级 → {@code fallback=true}。
 * 退避用记录式 {@link Sleeper} 验证序列而不真实 sleep（测试确定性）。</p>
 */
class GovernedExternalCallerTest {

    /** 记录退避时长的测试 Sleeper，不真实阻塞。 */
    private final List<Long> sleeps = new ArrayList<>();
    private final Sleeper recordingSleeper = sleeps::add;

    private GovernedExternalCaller caller() {
        return new GovernedExternalCaller(RetryPolicy.defaultPolicy(), recordingSleeper);
    }

    @Nested
    @DisplayName("成功路径：不降级、按需重试")
    class Success {

        @Test
        @DisplayName("首次成功：无重试无退避，fallback=false")
        void firstTrySucceeds() {
            GovernedExternalCaller caller = caller();
            CallResult<String> result = caller.call("xhs", () -> "ok", cause -> "fb");
            assertEquals("ok", result.value());
            assertFalse(result.fallback());
            assertTrue(sleeps.isEmpty());
        }

        @Test
        @DisplayName("第 3 次尝试成功：退避 [1000,2000]，fallback=false")
        void succeedsOnThirdAttempt() {
            GovernedExternalCaller caller = caller();
            AtomicInteger attempts = new AtomicInteger();
            CallResult<String> result = caller.call("xhs", () -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new TimeoutException("slow");
                }
                return "recovered";
            }, cause -> "fb");

            assertEquals("recovered", result.value());
            assertFalse(result.fallback());
            assertEquals(List.of(1_000L, 2_000L), sleeps);
        }
    }

    @Nested
    @DisplayName("降级路径：重试耗尽 → fallback=true（Case-2）")
    class Degradation {

        @Test
        @DisplayName("持续超时：重试 3 次退避 1/2/4s 后降级")
        void persistentTimeoutFallsBack() {
            GovernedExternalCaller caller = caller();
            AtomicInteger attempts = new AtomicInteger();
            CallResult<String> result = caller.call("xhs", () -> {
                attempts.incrementAndGet();
                throw new TimeoutException("always slow");
            }, cause -> "offline-default");

            assertEquals("offline-default", result.value());
            assertTrue(result.fallback());
            assertEquals(4, attempts.get()); // 1 次初始 + 3 次重试
            assertEquals(List.of(1_000L, 2_000L, 4_000L), sleeps);
        }

        @Test
        @DisplayName("降级处理器收到根因异常")
        void fallbackReceivesCause() {
            GovernedExternalCaller caller = caller();
            CallResult<String> result = caller.call("xhs", () -> {
                throw new IllegalStateException("downstream-broke");
            }, cause -> "cause:" + cause.getMessage());
            assertEquals("cause:downstream-broke", result.value());
            assertTrue(result.fallback());
        }
    }

    @Nested
    @DisplayName("限频拦截：命中即抛 429，不重试不降级")
    class RateLimiting {

        @Test
        void rateLimitedShortCircuits() {
            AtomicLong clock = new AtomicLong(0L);
            RateLimiter limiter = RateLimiter.withDefaults(new InMemoryRateLimitStore(clock::get));
            GovernedExternalCaller caller =
                    new GovernedExternalCaller(RetryPolicy.defaultPolicy(), recordingSleeper, limiter);

            // 耗尽 xhs 10/min 额度
            for (int i = 0; i < 10; i++) {
                limiter.acquireOrThrow(RateLimiter.API_XHS);
            }
            assertThrows(RateLimitedException.class,
                    () -> caller.call(RateLimiter.API_XHS, () -> "ok", cause -> "fb"));
            assertTrue(sleeps.isEmpty());
        }
    }
}
