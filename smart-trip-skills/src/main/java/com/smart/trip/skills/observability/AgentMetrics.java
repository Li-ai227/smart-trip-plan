package com.smart.trip.skills.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;

/**
 * Agent 协同关键指标埋点（C-005 AC-7 / 接口协议.md §6）。
 *
 * <p>对 Micrometer {@link MeterRegistry} 的薄封装，集中暴露 §6 关键指标埋点位：调用延迟
 * {@code agent.call.latency}（P95 告警 &gt;10s）、调用错误 {@code agent.call.error}
 * （错误率告警 &gt;5%）、限频命中 {@code api.rate_limit.hit}（告警 &gt;0）。具体 Registry/Prometheus
 * 暴露由 server 层装配（{@code /actuator/prometheus}）。</p>
 */
public final class AgentMetrics {

    /** 指标名：Agent 调用延迟（Timer，标签 agent/taskType）。 */
    public static final String METRIC_CALL_LATENCY = "agent.call.latency";

    /** 指标名：Agent 调用错误计数（Counter，标签 agent；错误率由其与总量推导）。 */
    public static final String METRIC_CALL_ERROR = "agent.call.error";

    /** 指标名：外部 API 限频命中计数（Counter，标签 api）。 */
    public static final String METRIC_RATE_LIMIT_HIT = "api.rate_limit.hit";

    /** 指标名：记忆缓存访问计数（Counter，标签 cache/result；命中率 = hit/(hit+miss)）。 */
    public static final String METRIC_CACHE_ACCESS = "memory.cache.hit";

    /** 指标名：地图厂商切换计数（Counter，标签 from/to；AC-9d 可观测性）。 */
    public static final String METRIC_VENDOR_SWITCH = "map.vendor.switch";

    /** 指标名：地图通道切换计数（Counter，标签 from/to；AC-9d MCP→HTTP 通道切换）。 */
    public static final String METRIC_CHANNEL_SWITCH = "map.channel.switch";

    /** 指标名：认证失败计数（Counter，C-011 T-9）。 */
    public static final String METRIC_AUTH_FAILURE = "auth.failure.count";

    /** 指标名：token 吊销/登出计数（Counter，C-011 T-9）。 */
    public static final String METRIC_AUTH_REVOKE = "auth.revoke.count";

    /** 指标名：Redis 不可用降级告警（Counter，C-011 T-9）。 */
    public static final String METRIC_AUTH_REDIS_FALLBACK = "auth.redis.fallback";

    /** 指标名：HITL 人工介入计数（Counter，C-011 T-9）。 */
    public static final String METRIC_HUMAN_INTERVENTION = "human.intervention.count";

    /** 指标名：记忆召回延迟（Timer，标签 userId/destination；P95 < 1s 告警）。 */
    public static final String METRIC_MEMORY_RECALL_LATENCY = "memory.recall.latency";

    /** 指标名：记忆召回命中计数（Counter，标签 result=hit/miss）。 */
    public static final String METRIC_MEMORY_RECALL_HIT = "memory.recall.hit";

    /** 指标名：记忆降级计数（Counter，C-015 AC-9）。 */
    public static final String METRIC_MEMORY_FALLBACK_COUNT = "memory.fallback.count";

    private final MeterRegistry registry;

    /**
     * 构造指标埋点。
     *
     * @param registry Micrometer 指标注册表，不可为 null
     */
    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录一次 Agent 调用延迟。
     *
     * @param agentId  Agent 标识（{@code agent://<id>}）
     * @param taskType 任务类型
     * @param millis   调用耗时（毫秒）
     */
    public void recordCallLatency(String agentId, String taskType, long millis) {
        registry.timer(METRIC_CALL_LATENCY, "agent", agentId, "taskType", taskType)
                .record(Duration.ofMillis(millis));
    }

    /**
     * 累加一次 Agent 调用错误。
     *
     * @param agentId Agent 标识（{@code agent://<id>}）
     */
    public void incrementCallError(String agentId) {
        registry.counter(METRIC_CALL_ERROR, "agent", agentId).increment();
    }

    /**
     * 累加一次外部 API 限频命中。
     *
     * @param api 外部 API 标识（如 {@code xhs}/{@code map}/{@code llm}）
     */
    public void incrementRateLimitHit(String api) {
        registry.counter(METRIC_RATE_LIMIT_HIT, "api", api).increment();
    }

    /**
     * 记录一次记忆缓存访问（命中/未命中），用于推导命中率（接口协议.md §6 告警 &lt;80%）。
     *
     * @param cache 缓存标识（如 {@code xhs}）
     * @param hit   是否命中（{@code true}=命中，{@code false}=未命中）
     */
    public void recordCacheAccess(String cache, boolean hit) {
        registry.counter(METRIC_CACHE_ACCESS, "cache", cache, "result", hit ? "hit" : "miss")
                .increment();
    }

    /**
     * 累加一次地图厂商切换（AC-9d 可观测性）。
     *
     * @param from 原厂商标签（如 {@code "baidu"}）
     * @param to   切换后厂商标签（如 {@code "amap"}）
     */
    public void recordVendorSwitch(String from, String to) {
        registry.counter(METRIC_VENDOR_SWITCH, "from", from, "to", to).increment();
    }

    /**
     * 累加一次地图通道切换（AC-9d 通道可观测性，如 MCP→HTTP）。
     *
     * @param from 原通道标签（如 {@code "mcp"}）
     * @param to   切换后通道标签（如 {@code "http"}）
     */
    public void recordChannelSwitch(String from, String to) {
        registry.counter(METRIC_CHANNEL_SWITCH, "from", from, "to", to).increment();
    }

    /** 累加一次认证失败（登录用户名/密码错误，C-011 T-9）。 */
    public void incrementAuthFailure() {
        registry.counter(METRIC_AUTH_FAILURE).increment();
    }

    /** 累加一次 token 主动吊销/登出（C-011 T-9）。 */
    public void incrementAuthRevoke() {
        registry.counter(METRIC_AUTH_REVOKE).increment();
    }

    /** 累加一次 Redis 不可用降级（C-011 T-9）。 */
    public void incrementAuthRedisFallback() {
        registry.counter(METRIC_AUTH_REDIS_FALLBACK).increment();
    }

    /** 累加一次 HITL 人工介入（C-011 T-9）。 */
    public void incrementHumanIntervention() {
        registry.counter(METRIC_HUMAN_INTERVENTION).increment();
    }

    /**
     * 记录一次记忆召回延迟。
     *
     * @param userId      用户 ID
     * @param destination 目的地（便于按目的地分析召回性能）
     * @param millis      耗时（毫秒）
     */
    public void recordMemoryRecallLatency(String userId, String destination, long millis) {
        registry.timer(METRIC_MEMORY_RECALL_LATENCY,
                        "userId", userId != null ? userId : "unknown",
                        "destination", destination != null ? destination : "unknown")
                .record(Duration.ofMillis(millis));
    }

    /**
     * 累加一次记忆召回命中/未命中。
     *
     * @param hit true=命中（有记忆召回），false=未命中（无可用记忆）
     */
    public void incrementMemoryRecallHit(boolean hit) {
        registry.counter(METRIC_MEMORY_RECALL_HIT, "result", hit ? "hit" : "miss")
                .increment();
    }

    /** 累加一次记忆降级（ReMe Server 不可用或调用异常）。 */
    public void incrementMemoryFallback() {
        registry.counter(METRIC_MEMORY_FALLBACK_COUNT).increment();
    }
}
