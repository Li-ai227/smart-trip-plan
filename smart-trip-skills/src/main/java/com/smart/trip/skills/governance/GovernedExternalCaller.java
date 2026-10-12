package com.smart.trip.skills.governance;

import com.smart.trip.common.exception.RateLimitedException;
import java.util.concurrent.Callable;

/**
 * 统一外部调用治理执行器（C-005 AC-4/AC-5）。
 *
 * <p>把分散的治理关注点收敛为单一执行链，杜绝各 Agent 各自复制（设计约束「外部调用治理归属」）：</p>
 * <ol>
 *   <li><b>限频</b>（可选）：命中即抛 {@link RateLimitedException}（429），不重试、不降级；</li>
 *   <li><b>重试 + 指数退避</b>：按 {@link RetryPolicy} 最多重试 3 次，退避 1/2/4s（经 {@link Sleeper}）；</li>
 *   <li><b>失败降级</b>：重试耗尽后调用 {@link FallbackHandler} 返回默认值，并标记 {@code fallback=true}。</li>
 * </ol>
 *
 * <p>超时按 {@link TimeoutPolicy}（默认 30s）由具体 {@code ExternalClient} 适配器在真实 I/O 上施加；
 * 任何下游失败（含超时）均以异常进入本链的重试/降级路径，绝不静默挂起（接口协议.md §2 测试关注点）。</p>
 */
public final class GovernedExternalCaller {

    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;
    private final RateLimiter rateLimiter;

    /**
     * 构造不含限频的治理执行器。
     *
     * @param retryPolicy 重试与退避策略，不可为 null
     * @param sleeper     退避等待实现，不可为 null
     */
    public GovernedExternalCaller(RetryPolicy retryPolicy, Sleeper sleeper) {
        this(retryPolicy, sleeper, null);
    }

    /**
     * 构造含限频的治理执行器。
     *
     * @param retryPolicy 重试与退避策略，不可为 null
     * @param sleeper     退避等待实现，不可为 null
     * @param rateLimiter 限频器，可为 null（表示不施加限频）
     */
    public GovernedExternalCaller(RetryPolicy retryPolicy, Sleeper sleeper, RateLimiter rateLimiter) {
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 在统一治理下执行一次外部调用。
     *
     * @param api      外部 API 标识（用于限频），非空白
     * @param action   实际调用动作（可抛任意异常表示失败/超时）
     * @param fallback 重试耗尽后的降级处理器
     * @param <T>      结果类型
     * @return 调用结果或降级结果（降级时 {@code fallback=true}）
     * @throws RateLimitedException 当命中限频（不重试、不降级）
     */
    public <T> CallResult<T> call(String api, Callable<T> action, FallbackHandler<T> fallback) {
        if (rateLimiter != null) {
            rateLimiter.acquireOrThrow(api);
        }
        Exception last = null;
        for (int attempt = 0; attempt <= retryPolicy.maxRetries(); attempt++) {
            if (attempt > 0) {
                sleeper.sleep(retryPolicy.backoffMillis(attempt));
            }
            try {
                return new CallResult<>(action.call(), false);
            } catch (Exception ex) {
                // 刻意捕获任意下游失败（含超时）以驱动重试/降级，绝不静默挂起（接口协议 §2）。
                // 限频在进入循环前已 acquireOrThrow 短路，此处不会吞没 RateLimitedException。
                last = ex;
            }
        }
        return new CallResult<>(fallback.onFallback(last), true);
    }
}
