package com.smart.trip.agent.route.mcp;

import com.smart.trip.skills.observability.AgentMetrics;
import java.util.concurrent.Callable;

/**
 * 高德通道级 failover 引擎（C-007 增量 B / AC-9b）。
 *
 * <p>在高德厂商内部实现 MCP 主通道 → HTTP 降级通道的自动切换，
 * 熔断/冷却/半开探测语义复用 {@link VendorHealth} 状态机，
 * 指标发射 {@code map.channel.switch{from=mcp,to=http}}。</p>
 */
final class ChannelFailoverExecutor {

    private static final System.Logger LOG = System.getLogger(ChannelFailoverExecutor.class.getName());

    private final VendorHealth mcpHealth;
    private final VendorHealth httpHealth;
    private final AgentMetrics metrics;

    ChannelFailoverExecutor(VendorHealth mcpHealth, VendorHealth httpHealth,
                            AgentMetrics metrics) {
        this.mcpHealth = mcpHealth;
        this.httpHealth = httpHealth;
        this.metrics = metrics;
    }

    /**
     * MCP 主通道优先，失败后切 HTTP 降级通道。
     *
     * @param mcpCall  MCP 主通道调用
     * @param httpCall HTTP 降级通道调用
     * @param <T>      返回值类型
     * @return 调用结果（真实数据）
     * @throws Exception 双通道均失败
     */
    <T> T execute(Callable<T> mcpCall, Callable<T> httpCall) throws Exception {
        if (mcpHealth.isAvailable()) {
            try {
                T result = mcpCall.call();
                mcpHealth.recordSuccess();
                return result;
            } catch (Exception ex) {
                mcpHealth.recordFailure();
                LOG.log(System.Logger.Level.WARNING,
                        "Amap channel switch: mcp -> http, reason={0}", ex.getMessage());
            }
        }

        if (httpHealth.isAvailable()) {
            try {
                T result = httpCall.call();
                httpHealth.recordSuccess();
                if (metrics != null) {
                    metrics.recordChannelSwitch("mcp", "http");
                }
                return result;
            } catch (Exception ex) {
                httpHealth.recordFailure();
                throw ex;
            }
        }

        throw new MapApiException(-1,
                "高德双通道均不可用: mcp=" + mcpHealth.state()
                        + ", http=" + httpHealth.state());
    }
}
