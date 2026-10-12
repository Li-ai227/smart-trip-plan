package com.smart.trip.skills.governance;

import java.util.ArrayList;
import java.util.List;

/**
 * 重试与指数退避策略（C-005 AC-4 / 接口协议.md §2 调用约束）。
 *
 * <p>默认 3 次重试，退避序列 1/2/4s（指数 {@code base * 2^(n-1)}）。退避时长由
 * {@link GovernedExternalCaller} 经 {@link Sleeper} 消费，便于测试注入而不真实阻塞。</p>
 *
 * @param maxRetries        最大重试次数（不含首次尝试），{@code >= 0}
 * @param baseBackoffMillis 基准退避（首个退避，毫秒），{@code > 0}
 */
public record RetryPolicy(int maxRetries, long baseBackoffMillis) {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_BASE_BACKOFF_MILLIS = 1_000L;

    /**
     * 紧凑构造器：校验配置合法（编码规范 §5）。
     */
    public RetryPolicy {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不可为负: " + maxRetries);
        }
        if (baseBackoffMillis <= 0) {
            throw new IllegalArgumentException("baseBackoffMillis 必须 > 0: " + baseBackoffMillis);
        }
    }

    /**
     * 默认策略：3 次重试 + 退避 1/2/4s（接口协议.md §2）。
     *
     * @return 默认重试策略
     */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(DEFAULT_MAX_RETRIES, DEFAULT_BASE_BACKOFF_MILLIS);
    }

    /**
     * 计算第 {@code retryIndex} 次重试前的退避时长（指数退避）。
     *
     * @param retryIndex 重试序号，取值 [1, maxRetries]
     * @return 退避毫秒数 {@code base * 2^(retryIndex-1)}
     * @throws IllegalArgumentException 当 retryIndex 越界
     */
    public long backoffMillis(int retryIndex) {
        if (retryIndex < 1 || retryIndex > maxRetries) {
            throw new IllegalArgumentException(
                    "retryIndex 越界 [1, " + maxRetries + "]: " + retryIndex);
        }
        return baseBackoffMillis << (retryIndex - 1);
    }

    /**
     * 返回完整退避序列（长度等于 {@link #maxRetries()}）。
     *
     * @return 退避时长列表，如 {@code [1000, 2000, 4000]}
     */
    public List<Long> backoffSequence() {
        List<Long> sequence = new ArrayList<>(maxRetries);
        for (int i = 1; i <= maxRetries; i++) {
            sequence.add(backoffMillis(i));
        }
        return List.copyOf(sequence);
    }
}
