package com.smart.trip.skills.governance;

/**
 * 降级处理器（C-005 AC-4/AC-5）。
 *
 * <p>当外部调用重试耗尽后被调用，返回降级默认值（如离线估算、缓存、模板）。返回的结果会被
 * {@link GovernedExternalCaller} 标记为 {@code fallback=true}（接口协议.md §2.3）。</p>
 *
 * @param <T> 降级结果类型
 */
@FunctionalInterface
public interface FallbackHandler<T> {

    /**
     * 产出降级默认值。
     *
     * @param cause 触发降级的最后一次失败根因
     * @return 降级默认值，可空
     */
    T onFallback(Exception cause);
}
