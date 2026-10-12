package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AmapMcpClient} 确定性单测（C-007 增量 B / T-8）。
 *
 * <p>通过 AgentScope {@link McpClientWrapper} 匿名子类桩注入预设 JSON，
 * 验证 MCP 工具调用的解析逻辑。</p>
 */
class AmapMcpClientTest {

    private static final GeoLocation FROM = new GeoLocation(104.065735, 30.659462, "宽窄巷子", "成都");
    private static final GeoLocation TO = new GeoLocation(104.143254, 30.731768, "大熊猫基地", "成都");

    private static final String GEO_RESPONSE = """
            {"geocodes":[{"location":"104.065735,30.659462","formatted_address":"宽窄巷子"}],"count":"1"}""";

    private static final String ROUTE_RESPONSE = """
            {"route":{"paths":[{"distance":"10000","duration":"1800"}]}}""";

    private static final String TRAFFIC_RESPONSE = """
            {"route":{"paths":[{"distance":"10000","steps":[{"tmcs":[{"status":"拥堵","distance":"3000"},{"status":"畅通","distance":"7000"}]}]}]}}""";

    @Test
    void geocode_valid_response() throws Exception {
        AmapMcpClient client = new AmapMcpClient(stubClient((name, args) -> {
            assertEquals(AmapMcpClient.TOOL_GEO, name);
            assertTrue(args.containsKey("address"));
            return GEO_RESPONSE;
        }), null);

        GeoLocation result = client.geocode("宽窄巷子", "成都");

        assertEquals(104.065735, result.longitude(), 0.0001);
        assertEquals(30.659462, result.latitude(), 0.0001);
        assertEquals("宽窄巷子", result.address());
    }

    @Test
    void geocode_error_throws() {
        AmapMcpClient client = new AmapMcpClient(stubClient((name, args) -> {
            throw new MapApiException(10001, "MCP 工具调用失败: INVALID_USER_KEY");
        }), null);

        assertThrows(MapApiException.class, () -> client.geocode("unknown", "成都"));
    }

    @Test
    void geocode_cache_hit_skips_mcp() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicLong clock = new AtomicLong(0);
        GeocodingCache cache = new GeocodingCache(60_000, clock::get);

        AmapMcpClient client = new AmapMcpClient(stubClient((name, args) -> {
            callCount.incrementAndGet();
            return GEO_RESPONSE;
        }), cache);

        client.geocode("宽窄巷子", "成都");
        client.geocode("宽窄巷子", "成都");

        assertEquals(1, callCount.get());
    }

    @Test
    void route_valid_response() throws Exception {
        AmapMcpClient client = new AmapMcpClient(stubClient((name, args) -> {
            assertEquals(AmapMcpClient.TOOL_DRIVING, name);
            return ROUTE_RESPONSE;
        }), null);

        Route result = client.route(FROM, TO);

        assertEquals(10.0, result.distanceKm(), 0.1);
        assertEquals(30, result.durationMin());
        assertEquals("驾车", result.transportMode());
        assertFalse(result.fallback());
    }

    @Test
    void route_empty_paths_returns_fallback() throws Exception {
        AmapMcpClient client = new AmapMcpClient(
                stubClient((name, args) -> "{\"route\":{\"paths\":[]}}"), null);

        Route result = client.route(FROM, TO);

        assertEquals(0, result.distanceKm());
        assertEquals(0, result.durationMin());
        assertTrue(result.fallback());
    }

    @Test
    void traffic_factor_with_congestion() throws Exception {
        AmapMcpClient client = new AmapMcpClient(
                stubClient((name, args) -> TRAFFIC_RESPONSE), null);

        double factor = client.trafficFactor(FROM, TO);

        // 3000m 拥堵 / 10000m 总距离 = 0.3 → factor = 1.3
        assertEquals(1.3, factor, 0.01);
    }

    @Test
    void null_mcp_client_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new AmapMcpClient(null, null));
    }

    @Test
    void geocode_null_city_omits_city_arg() throws Exception {
        AmapMcpClient client = new AmapMcpClient(stubClient((name, args) -> {
            assertEquals(AmapMcpClient.TOOL_GEO, name);
            assertEquals("宽窄巷子", args.get("address"));
            assertEquals(1, args.size());  // city=null → 不传入 args
            return GEO_RESPONSE;
        }), null);

        GeoLocation result = client.geocode("宽窄巷子", null);

        assertEquals(104.065735, result.longitude(), 0.0001);
        assertEquals(30.659462, result.latitude(), 0.0001);
    }

    @Test
    void parse_geo_root_location_field() throws MapApiException {
        String json = "{\"location\":\"116.397428,39.90923\",\"formatted_address\":\"天安门\"}";

        GeoLocation result = AmapMcpClient.parseGeoResult(json, "天安门", "北京");

        assertEquals(116.397428, result.longitude(), 0.0001);
        assertEquals(39.90923, result.latitude(), 0.0001);
    }

    @Test
    void parse_traffic_no_tmc_steps() throws MapApiException {
        // 无 tmcs 数组（空 steps）→ factor=1.0
        String json = "{\"route\":{\"paths\":[{\"distance\":\"10000\"}]}}";

        double factor = AmapMcpClient.parseTrafficFactor(json);

        assertEquals(1.0, factor, 0.01);
    }

    @Test
    void call_mcp_tool_null_result_throws() {
        McpClientWrapper nullResultStub = new McpClientWrapper("test-null") {
            @Override public Mono<Void> initialize() { return Mono.empty(); }
            @Override public Mono<List<McpSchema.Tool>> listTools() { return Mono.just(List.of()); }
            @Override public Mono<McpSchema.CallToolResult> callTool(String n, Map<String, Object> a) {
                return Mono.empty();  // null result → block() 返回 null
            }
            @Override public Mono<McpSchema.CallToolResult> callTool(String name, Map<String, Object> args, Map<String, Object> metadata) {
                return callTool(name, args);
            }
            @Override public void close() {}
        };
        AmapMcpClient client = new AmapMcpClient(nullResultStub, null);

        assertThrows(MapApiException.class, () -> client.geocode("x", null));
    }

    @Test
    void call_mcp_tool_is_error_throws() {
        McpClientWrapper errorStub = new McpClientWrapper("test-error") {
            @Override public Mono<Void> initialize() { return Mono.empty(); }
            @Override public Mono<List<McpSchema.Tool>> listTools() { return Mono.just(List.of()); }
            @Override public Mono<McpSchema.CallToolResult> callTool(String n, Map<String, Object> a) {
                return Mono.just(new McpSchema.CallToolResult("工具调用失败", true));
            }
            @Override public Mono<McpSchema.CallToolResult> callTool(String name, Map<String, Object> args, Map<String, Object> metadata) {
                return callTool(name, args);
            }
            @Override public void close() {}
        };
        AmapMcpClient client = new AmapMcpClient(errorStub, null);

        assertThrows(MapApiException.class, () -> client.geocode("x", null));
    }

    @FunctionalInterface
    private interface McpToolStub {
        String callTool(String name, Map<String, Object> args) throws Exception;
    }

    /**
     * 创建 {@link McpClientWrapper} 桩：将 tool 调用委托给 handler，
     * 返回值包装为 {@link McpSchema.CallToolResult}。
     */
    private static McpClientWrapper stubClient(McpToolStub handler) {
        return new McpClientWrapper("test-amap") {
            @Override
            public Mono<Void> initialize() {
                return Mono.empty();
            }

            @Override
            public Mono<List<McpSchema.Tool>> listTools() {
                return Mono.just(List.of());
            }

            @Override
            public Mono<McpSchema.CallToolResult> callTool(String name,
                                                           Map<String, Object> args) {
                try {
                    String text = handler.callTool(name, args);
                    return Mono.just(new McpSchema.CallToolResult(text, false));
                } catch (Exception e) {
                    return Mono.error(e);
                }
            }

            @Override
            public Mono<McpSchema.CallToolResult> callTool(String name,
                                                           Map<String, Object> args,
                                                           Map<String, Object> metadata) {
                return callTool(name, args);
            }

            @Override
            public void close() {
            }
        };
    }
}
