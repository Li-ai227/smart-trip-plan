package com.smart.trip.skills.governance;

/**
 * 单次外部调用超时策略（C-005 AC-4 / 接口协议.md §2/§4：单次超时 30s）。
 *
 * <p>承载超时阈值，供具体 {@code ExternalClient} 适配器在真实 I/O 上施加超时；
 * 信封层 {@code Msg.timeoutMs} 与本策略默认值一致（30s），保持治理口径统一。</p>
 *
 * @param timeoutMillis 单次调用超时（毫秒），{@code > 0}
 */
public record TimeoutPolicy(long timeoutMillis) {

    /** 默认单次超时：30s（接口协议.md §4 外部 API 约束总表）。 */
    public static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    /**
     * 紧凑构造器：校验超时为正（编码规范 §5）。
     */
    public TimeoutPolicy {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis 必须 > 0: " + timeoutMillis);
        }
    }

    /**
     * 默认超时策略（30s）。
     *
     * @return 默认超时策略
     */
    public static TimeoutPolicy defaultPolicy() {
        return new TimeoutPolicy(DEFAULT_TIMEOUT_MILLIS);
    }
}
