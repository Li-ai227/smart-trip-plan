package com.smart.trip.skills.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.exception.RateLimitedException;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * {@link GovernanceMiddleware} 单测：MiddlewareBase 包装治理链（C-005 增量 B AC-9 / TDD Red→Green）。
 *
 * <p>验证 {@code onModelCall} 钩子中的治理行为与增量 A {@link GovernedExternalCaller} 等价：
 * 限频 → 重试退避 1/2/4s → 降级空事件。退避用记录式 {@link Sleeper} 验证序列而不真实 sleep。</p>
 */
class GovernanceMiddlewareTest {

    /** 记录退避时长的测试 Sleeper，不真实阻塞。 */
    private final List<Long> sleeps = new ArrayList<>();
    private final Sleeper recordingSleeper = sleeps::add;

    /** 最小 ModelCallInput，治理中间件不检查其内容。 */
    private final ModelCallInput dummyInput = new ModelCallInput(List.of(), List.of(), null, null);

    private AgentEvent testEvent() {
        return new ModelCallEndEvent("test-reply", new ChatUsage(10, 20, 0.5));
    }

    private GovernanceMiddleware middleware() {
        return new GovernanceMiddleware(RetryPolicy.defaultPolicy(), recordingSleeper);
    }

    // ── 成功路径 ──

    @Nested
    @DisplayName("成功路径：模型调用正常通过治理链")
    class Success {

        @Test
        @DisplayName("首次成功：无重试无退避，事件透传")
        void firstTrySucceeds_eventsPassThrough() {
            GovernanceMiddleware mw = middleware();
            AgentEvent event = testEvent();

            Function<ModelCallInput, Flux<AgentEvent>> next = input -> Flux.just(event);

            List<AgentEvent> result = mw.onModelCall(null, null, dummyInput, next)
                    .collectList().block();

            assertEquals(1, result.size());
            assertSame(event, result.get(0));
            assertTrue(sleeps.isEmpty(), "首次成功不应有退避");
        }

        @Test
        @DisplayName("第 3 次尝试成功：退避 [1000,2000]，事件正常返回")
        void succeedsOnThirdAttempt() {
            GovernanceMiddleware mw = middleware();
            AgentEvent event = testEvent();
            AtomicInteger attempts = new AtomicInteger();

            Function<ModelCallInput, Flux<AgentEvent>> next = input -> {
                if (attempts.incrementAndGet() < 3) {
                    return Flux.error(new TimeoutException("slow"));
                }
                return Flux.just(event);
            };

            List<AgentEvent> result = mw.onModelCall(null, null, dummyInput, next)
                    .collectList().block();

            assertEquals(1, result.size());
            assertSame(event, result.get(0));
            assertEquals(List.of(1_000L, 2_000L), sleeps);
        }
    }

    // ── 降级路径 ──

    @Nested
    @DisplayName("降级路径：重试耗尽 → 降级空事件（Case-6）")
    class Degradation {

        @Test
        @DisplayName("持续超时：重试 3 次退避 1/2/4s 后降级为空事件")
        void persistentTimeout_fallsBackToEmptyEvents() {
            GovernanceMiddleware mw = middleware();
            AtomicInteger attempts = new AtomicInteger();

            Function<ModelCallInput, Flux<AgentEvent>> next = input -> {
                attempts.incrementAndGet();
                return Flux.error(new TimeoutException("always slow"));
            };

            List<AgentEvent> result = mw.onModelCall(null, null, dummyInput, next)
                    .collectList().block();

            assertTrue(result.isEmpty(), "降级应返回空事件列表");
            assertEquals(4, attempts.get(), "1 次初始 + 3 次重试");
            assertEquals(List.of(1_000L, 2_000L, 4_000L), sleeps);
        }
    }

    // ── 限频拦截 ──

    @Nested
    @DisplayName("限频拦截：RateLimitedException 直接传播（边界 #2）")
    class RateLimiting {

        @Test
        @DisplayName("命中 LLM 限频：RateLimitedException 直接传播，不重试不降级")
        void rateLimitedPropagates_noRetryNoFallback() {
            AtomicLong clock = new AtomicLong(0L);
            RateLimiter limiter = RateLimiter.withDefaults(new InMemoryRateLimitStore(clock::get));
            GovernanceMiddleware mw = new GovernanceMiddleware(
                    RetryPolicy.defaultPolicy(), recordingSleeper, limiter);

            for (int i = 0; i < 60; i++) {
                limiter.acquireOrThrow(RateLimiter.API_LLM);
            }

            Function<ModelCallInput, Flux<AgentEvent>> next = input -> Flux.just(testEvent());

            assertThrows(RateLimitedException.class,
                    () -> mw.onModelCall(null, null, dummyInput, next).collectList().block());
            assertTrue(sleeps.isEmpty(), "限频命中不应有退避");
        }
    }
}
