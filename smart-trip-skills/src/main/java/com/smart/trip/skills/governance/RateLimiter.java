package com.smart.trip.skills.governance;

import com.smart.trip.common.constant.CacheKeys;
import com.smart.trip.common.exception.RateLimitedException;
import java.util.Map;

/**
 * 外部 API 滑动窗口限频器（C-005 AC-4 / 接口协议.md §4 / 数据模型.md §4）。
 *
 * <p>按 API 维护独立窗口额度（小红书 ≤10/min、地图 ≤30/min、通义 ≤60/min），计数经
 * {@link RateLimitStore} 落于键 {@code trip:ratelimit:{api}:{window}}。命中限频抛
 * {@link RateLimitedException}（接口协议.md §1.1 → HTTP 429）。未配置规则的 API 直接放行
 * （向后兼容，避免误伤未治理调用）。</p>
 */
public final class RateLimiter {

    /** 小红书 API 标识（≤10/min）。 */
    public static final String API_XHS = "xhs";

    /** 地图 API 标识（百度/高德，≤30/min）。 */
    public static final String API_MAP = "map";

    /** 通义千问 LLM 标识（≤60/min）。 */
    public static final String API_LLM = "llm";

    private static final long ONE_MINUTE_SECONDS = 60L;

    private final RateLimitStore store;
    private final Map<String, RateLimitRule> rules;

    /**
     * 构造限频器。
     *
     * @param store 计数存储，不可为 null
     * @param rules 各 API 限频规则（按 api 标识索引），不可为 null
     */
    public RateLimiter(RateLimitStore store, Map<String, RateLimitRule> rules) {
        this.store = store;
        this.rules = Map.copyOf(rules);
    }

    /**
     * 以接口协议.md §4 默认额度构造（小红书 10、地图 30、通义 60，每分钟）。
     *
     * @param store 计数存储，不可为 null
     * @return 配置默认额度的限频器
     */
    public static RateLimiter withDefaults(RateLimitStore store) {
        return new RateLimiter(store, Map.of(
                API_XHS, new RateLimitRule(API_XHS, 10, ONE_MINUTE_SECONDS),
                API_MAP, new RateLimitRule(API_MAP, 30, ONE_MINUTE_SECONDS),
                API_LLM, new RateLimitRule(API_LLM, 60, ONE_MINUTE_SECONDS)));
    }

    /**
     * 尝试获取一次调用额度（不抛异常）。
     *
     * @param api 外部 API 标识
     * @return 额度足够返回 {@code true}；命中限频返回 {@code false}；未配置规则恒为 {@code true}
     */
    public boolean tryAcquire(String api) {
        RateLimitRule rule = rules.get(api);
        if (rule == null) {
            return true;
        }
        String key = CacheKeys.rateLimitKey(api, rule.windowSeconds() + "s");
        long count = store.incrementAndGet(key, rule.windowSeconds());
        return count <= rule.maxPerWindow();
    }

    /**
     * 获取一次调用额度，命中限频则抛出。
     *
     * @param api 外部 API 标识
     * @throws RateLimitedException 当命中限频（接口协议.md §1.1 → 429）
     */
    public void acquireOrThrow(String api) {
        if (!tryAcquire(api)) {
            throw new RateLimitedException("命中限频: api=" + api);
        }
    }
}
