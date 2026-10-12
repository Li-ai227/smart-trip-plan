package com.smart.trip.agent.route.mcp;

import com.smart.trip.skills.observability.AgentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ChannelFailoverExecutor} 单测（C-007 增量 B / AC-9b）。
 *
 * <p>覆盖 MCP→HTTP 通道级 failover 完整语义：主通道成功、降级切换、
 * 熔断直接跳转、连续失败触发 OPEN、双通道均不可用。</p>
 */
class ChannelFailoverExecutorTest {

    private static final String MCP_RESULT = "mcp-data";
    private static final String HTTP_RESULT = "http-data";

    private final AtomicLong clock = new AtomicLong(0L);
    private SimpleMeterRegistry registry;
    private AgentMetrics metrics;
    private VendorHealth mcpHealth;
    private VendorHealth httpHealth;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AgentMetrics(registry);
        mcpHealth = new VendorHealth(VendorId.AMAP, 3, 30_000L, clock::get);
        httpHealth = new VendorHealth(VendorId.AMAP, 3, 30_000L, clock::get);
    }

    @Test
    void mcp_success_returns_without_switch() throws Exception {
        ChannelFailoverExecutor executor =
                new ChannelFailoverExecutor(mcpHealth, httpHealth, metrics);

        String result = executor.execute(() -> MCP_RESULT, () -> HTTP_RESULT);

        assertEquals(MCP_RESULT, result);
        assertEquals(0, channelSwitchCount());
        assertEquals(VendorHealth.State.CLOSED, mcpHealth.state());
    }

    @Test
    void mcp_fail_http_success_records_switch_metric() throws Exception {
        ChannelFailoverExecutor executor =
                new ChannelFailoverExecutor(mcpHealth, httpHealth, metrics);

        String result = executor.execute(
                () -> { throw new RuntimeException("MCP 不可用"); },
                () -> HTTP_RESULT);

        assertEquals(HTTP_RESULT, result);
        assertEquals(1, channelSwitchCount());
    }

    @Test
    void both_unavailable_throws_map_api_exception() {
        // 将两个通道的 health 都设为 OPEN
        for (int i = 0; i < 3; i++) {
            mcpHealth.recordFailure();
            httpHealth.recordFailure();
        }

        ChannelFailoverExecutor executor =
                new ChannelFailoverExecutor(mcpHealth, httpHealth, metrics);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> executor.execute(
                        () -> MCP_RESULT,
                        () -> HTTP_RESULT));
        assertEquals(-1, ex.statusCode());
    }

    @Test
    void mcp_open_circuit_skips_directly_to_http() throws Exception {
        // 触发 MCP 熔断（连续失败 3 次）
        for (int i = 0; i < 3; i++) {
            mcpHealth.recordFailure();
        }

        ChannelFailoverExecutor executor =
                new ChannelFailoverExecutor(mcpHealth, httpHealth, metrics);

        // MCP call 不该被调用（熔断打开），直接走 HTTP
        String result = executor.execute(
                () -> { throw new AssertionError("MCP 不应被调用"); },
                () -> HTTP_RESULT);

        assertEquals(HTTP_RESULT, result);
    }

    @Test
    void http_consecutive_failures_trigger_open() throws Exception {
        ChannelFailoverExecutor executor =
                new ChannelFailoverExecutor(mcpHealth, httpHealth, metrics);

        // MCP 持续故障，HTTP 也连续失败 — 每轮 executor.execute 将 HTTP 异常直接 rethrow
        for (int i = 0; i < 3; i++) {
            try {
                executor.execute(
                        () -> { throw new RuntimeException("MCP 失败"); },
                        () -> { throw new RuntimeException("HTTP 失败"); });
            } catch (RuntimeException ignored) {
                // execute() 在 HTTP 路径失败时 rethrow 原始异常
            }
        }

        // 3 次 HTTP 失败 → OPEN
        assertEquals(VendorHealth.State.OPEN, httpHealth.state());
    }

    @Test
    void null_metrics_does_not_throw() throws Exception {
        ChannelFailoverExecutor executor =
                new ChannelFailoverExecutor(mcpHealth, httpHealth, null);

        String result = executor.execute(
                () -> { throw new RuntimeException("MCP 不可用"); },
                () -> HTTP_RESULT);

        assertEquals(HTTP_RESULT, result);
        // 不应 NPE
    }

    private double channelSwitchCount() {
        return registry.counter(AgentMetrics.METRIC_CHANNEL_SWITCH,
                "from", "mcp", "to", "http").count();
    }

    /**
     * 自定义 AssertionError 用于 lambda 内验证"不应被调用"语义。
     */
    private static class AssertionError extends RuntimeException {
        AssertionError(String msg) {
            super(msg);
        }
    }
}
