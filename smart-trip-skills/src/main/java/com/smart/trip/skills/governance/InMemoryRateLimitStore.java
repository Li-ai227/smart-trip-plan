package com.smart.trip.skills.governance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 进程内限频计数存储（{@link RateLimitStore} 的本地实现，C-005 AC-4）。
 *
 * <p>以可注入毫秒时钟模拟 Redis {@code INCR + EXPIRE} 的固定窗口语义：同窗口内自增，
 * 窗口到期后重置为 1。时钟可注入便于单测确定性地推进窗口（编码规范 §8.2）。
 * 线程安全（{@link ConcurrentHashMap} + 同步窗口翻转）。</p>
 */
public final class InMemoryRateLimitStore implements RateLimitStore {

    private final LongSupplier nowMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 使用系统时钟构造。
     */
    public InMemoryRateLimitStore() {
        this(System::currentTimeMillis);
    }

    /**
     * 使用可注入时钟构造（测试用）。
     *
     * @param nowMillis 当前时间（毫秒）供给，不可为 null
     */
    public InMemoryRateLimitStore(LongSupplier nowMillis) {
        this.nowMillis = nowMillis;
    }

    @Override
    public long incrementAndGet(String key, long windowSeconds) {
        long now = nowMillis.getAsLong();
        Window window = windows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            if (now >= window.expiryMillis) {
                window.count = 1L;
                window.expiryMillis = now + windowSeconds * 1_000L;
            } else {
                window.count++;
            }
            return window.count;
        }
    }

    /** 单个窗口的可变计数与到期时间。 */
    private static final class Window {
        private long count;
        private long expiryMillis;
    }
}
