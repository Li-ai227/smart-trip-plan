package com.smart.trip.skills.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentMetrics} 单测：Micrometer 关键指标埋点（C-005 AC-7 / 接口协议.md §6）。
 */
class AgentMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AgentMetrics metrics = new AgentMetrics(registry);

    @Test
    @DisplayName("记录调用延迟到 agent.call.latency（带 agent/taskType 标签）")
    void recordsCallLatency() {
        metrics.recordCallLatency("agent://route-planner", "ROUTE_PLANNING", 1_500L);

        Timer timer = registry.find(AgentMetrics.METRIC_CALL_LATENCY)
                .tag("agent", "agent://route-planner")
                .tag("taskType", "ROUTE_PLANNING")
                .timer();
        assertTrue(timer != null);
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) >= 1_500L);
    }

    @Test
    @DisplayName("调用错误计数 agent.call.error 累加")
    void countsCallErrors() {
        metrics.incrementCallError("agent://route-planner");
        metrics.incrementCallError("agent://route-planner");

        Counter counter = registry.find(AgentMetrics.METRIC_CALL_ERROR)
                .tag("agent", "agent://route-planner").counter();
        assertTrue(counter != null);
        assertEquals(2.0, counter.count());
    }

    @Test
    @DisplayName("限频命中计数 api.rate_limit.hit 累加（接口协议 §6 告警 >0）")
    void countsRateLimitHits() {
        metrics.incrementRateLimitHit("xhs");
        Counter counter = registry.find(AgentMetrics.METRIC_RATE_LIMIT_HIT)
                .tag("api", "xhs").counter();
        assertTrue(counter != null);
        assertEquals(1.0, counter.count());
    }

    @Test
    @DisplayName("缓存命中/未命中分别累加 memory.cache.hit（result 标签，支撑命中率 §6 告警 <80%）")
    void recordsCacheHitRate() {
        metrics.recordCacheAccess("xhs", true);
        metrics.recordCacheAccess("xhs", true);
        metrics.recordCacheAccess("xhs", false);

        Counter hit = registry.find(AgentMetrics.METRIC_CACHE_ACCESS)
                .tag("cache", "xhs").tag("result", "hit").counter();
        Counter miss = registry.find(AgentMetrics.METRIC_CACHE_ACCESS)
                .tag("cache", "xhs").tag("result", "miss").counter();
        assertTrue(hit != null && miss != null);
        assertEquals(2.0, hit.count());
        assertEquals(1.0, miss.count());
    }

    @Test
    @DisplayName("记录记忆召回延迟到 memory.recall.latency（带 userId/destination 标签）")
    void recordsMemoryRecallLatency() {
        metrics.recordMemoryRecallLatency("user-001", "杭州", 500L);

        Timer timer = registry.find(AgentMetrics.METRIC_MEMORY_RECALL_LATENCY)
                .tag("userId", "user-001")
                .tag("destination", "杭州")
                .timer();
        assertTrue(timer != null);
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) >= 500L);
    }

    @Test
    @DisplayName("记忆召回命中/未命中分别累加 memory.recall.hit（result 标签）")
    void countsMemoryRecallHit() {
        metrics.incrementMemoryRecallHit(true);
        metrics.incrementMemoryRecallHit(true);
        metrics.incrementMemoryRecallHit(false);

        Counter hit = registry.find(AgentMetrics.METRIC_MEMORY_RECALL_HIT)
                .tag("result", "hit").counter();
        Counter miss = registry.find(AgentMetrics.METRIC_MEMORY_RECALL_HIT)
                .tag("result", "miss").counter();
        assertTrue(hit != null && miss != null);
        assertEquals(2.0, hit.count());
        assertEquals(1.0, miss.count());
    }

    @Test
    @DisplayName("记忆降级计数 memory.fallback.count 累加")
    void countsMemoryFallback() {
        metrics.incrementMemoryFallback();
        metrics.incrementMemoryFallback();

        Counter counter = registry.find(AgentMetrics.METRIC_MEMORY_FALLBACK_COUNT).counter();
        assertTrue(counter != null);
        assertEquals(2.0, counter.count());
    }

    @Test
    @DisplayName("userId 为 null 时降级为 unknown 标签")
    void recordsMemoryRecallLatencyWithNullUserIdUsesUnknown() {
        metrics.recordMemoryRecallLatency(null, "杭州", 200L);

        Timer timer = registry.find(AgentMetrics.METRIC_MEMORY_RECALL_LATENCY)
                .tag("userId", "unknown")
                .tag("destination", "杭州")
                .timer();
        assertTrue(timer != null);
        assertEquals(1, timer.count());
    }
}
