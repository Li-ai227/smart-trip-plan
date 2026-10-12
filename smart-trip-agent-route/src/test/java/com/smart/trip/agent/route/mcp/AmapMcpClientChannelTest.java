package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.MapGeocoder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AmapMcpClient + ChannelFailoverExecutor 端到端集成测试（C-007 增量 B / Case-11/Case-13）。
 *
 * <p>验证高德 MCP 主通道→HTTP 降级通道的完整 failover 链路，
 * 模拟 {@link MapVendorFactory#buildAmapChannelPair} 的装配方式。</p>
 */
class AmapMcpClientChannelTest {

    private static final GeoLocation FROM = new GeoLocation(104.065735, 30.659462, "宽窄巷子", "成都");
    private static final GeoLocation TO = new GeoLocation(104.143254, 30.731768, "大熊猫基地", "成都");
    private static final GeoLocation HTTP_RESULT = new GeoLocation(104.0, 30.5, "宽窄巷子", "成都");
    private static final String GEO_RESPONSE = """
            {"geocodes":[{"location":"104.065735,30.659462"}]}""";

    private SimpleMeterRegistry registry;
    private AgentMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AgentMetrics(registry);
    }

    @Test
    void mcp_fails_http_geocoder_succeeds() throws Exception {
        // MCP stub: 抛出异常模拟 MCP 不可用
        McpClientWrapper failingMcp = new McpClientWrapper("test-amap") {
            @Override public Mono<Void> initialize() { return Mono.empty(); }
            @Override public Mono<List<McpSchema.Tool>> listTools() { return Mono.just(List.of()); }
            @Override public Mono<McpSchema.CallToolResult> callTool(String n, Map<String, Object> a) {
                return Mono.error(new RuntimeException("MCP 连接失败"));
            }
            @Override public Mono<McpSchema.CallToolResult> callTool(String name, Map<String, Object> args, Map<String, Object> metadata) {
                return callTool(name, args);
            }
            @Override public void close() {}
        };

        AmapMcpClient mcpClient = new AmapMcpClient(failingMcp, null);
        MapGeocoder httpGeocoder = (addr, city) -> HTTP_RESULT;

        // 通道级 failover：MCP → HTTP（对齐 MapVendorFactory.buildAmapChannelPair）
        VendorHealth mcpHealth = new VendorHealth(VendorId.AMAP);
        VendorHealth httpHealth = new VendorHealth(VendorId.AMAP);
        ChannelFailoverExecutor channel =
                new ChannelFailoverExecutor(mcpHealth, httpHealth, metrics);

        MapGeocoder failoverGeocoder = (addr, city) -> channel.execute(
                () -> mcpClient.geocode(addr, city),
                () -> httpGeocoder.geocode(addr, city));

        GeoLocation result = failoverGeocoder.geocode("宽窄巷子", "成都");

        assertEquals(HTTP_RESULT, result);
        // 验证 map.channel.switch 指标 +1
        double count = registry.counter(AgentMetrics.METRIC_CHANNEL_SWITCH,
                "from", "mcp", "to", "http").count();
        assertEquals(1, count, "MCP→HTTP 通道切换应记录 metric");
    }

    @Test
    void both_mcp_and_http_fail_throws() {
        // MCP stub: 抛出异常
        McpClientWrapper failingMcp = new McpClientWrapper("test-amap") {
            @Override public Mono<Void> initialize() { return Mono.empty(); }
            @Override public Mono<List<McpSchema.Tool>> listTools() { return Mono.just(List.of()); }
            @Override public Mono<McpSchema.CallToolResult> callTool(String n, Map<String, Object> a) {
                return Mono.error(new RuntimeException("MCP 连接失败"));
            }
            @Override public Mono<McpSchema.CallToolResult> callTool(String name, Map<String, Object> args, Map<String, Object> metadata) {
                return callTool(name, args);
            }
            @Override public void close() {}
        };

        AmapMcpClient mcpClient = new AmapMcpClient(failingMcp, null);
        MapGeocoder failingHttp = (addr, city) -> {
            throw new MapApiException(500, "HTTP 降级也失败");
        };

        VendorHealth mcpHealth = new VendorHealth(VendorId.AMAP);
        VendorHealth httpHealth = new VendorHealth(VendorId.AMAP);
        ChannelFailoverExecutor channel =
                new ChannelFailoverExecutor(mcpHealth, httpHealth, metrics);

        MapGeocoder failoverGeocoder = (addr, city) -> channel.execute(
                () -> mcpClient.geocode(addr, city),
                () -> failingHttp.geocode(addr, city));

        MapApiException ex = assertThrows(MapApiException.class,
                () -> failoverGeocoder.geocode("宽窄巷子", "成都"));
        assertEquals(500, ex.statusCode(), "双通道均故障应抛出 HTTP 降级异常");
    }
}
