package com.smart.trip.skills.governance;

/**
 * 限频计数存储抽象（数据模型.md §4：{@code INCR} + {@code EXPIRE} 滑动窗口）。
 *
 * <p>把计数介质与限频判定解耦：生产实现可由 Redis {@code INCR/EXPIRE} 承载
 * （键 {@code trip:ratelimit:{api}:{window}}），单测用 {@link InMemoryRateLimitStore}
 * 配可控时钟驱动窗口到期（编码规范 §8.2）。</p>
 */
public interface RateLimitStore {

    /**
     * 对窗口键自增并返回当前窗口内计数；窗口到期后从 1 重新计数（{@code EXPIRE} 语义）。
     *
     * @param key           窗口键（如 {@code trip:ratelimit:xhs:60s}），非空白
     * @param windowSeconds 窗口长度（秒），{@code > 0}
     * @return 自增后当前窗口内的计数（从 1 起）
     */
    long incrementAndGet(String key, long windowSeconds);
}
