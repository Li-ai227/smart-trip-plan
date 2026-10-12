package com.smart.trip.skills.governance;

/**
 * 单个 API 的限频规则（C-005 AC-4 / 接口协议.md §4）。
 *
 * @param api           API 标识（如 {@code xhs}/{@code map}/{@code llm}）
 * @param maxPerWindow  窗口内最大调用次数
 * @param windowSeconds 窗口长度（秒）
 */
public record RateLimitRule(String api, int maxPerWindow, long windowSeconds) {

    /**
     * 紧凑构造器：校验额度与窗口为正（编码规范 §5）。
     */
    public RateLimitRule {
        if (maxPerWindow <= 0) {
            throw new IllegalArgumentException("maxPerWindow 必须 > 0: " + maxPerWindow);
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds 必须 > 0: " + windowSeconds);
        }
    }
}
