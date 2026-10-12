package com.smart.trip.agent.route.mcp;

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MapVendorFactory} 单测（C-007 增量 B / AC-9e）。
 */
class MapVendorFactoryTest {

    private static final MapHttpExecutor STUB_HTTP = url -> "{}";

    @Test
    void dual_ak_produces_failover_adapters() {
        MapVendorFactory.VendorAdapters adapters =
                MapVendorFactory.assemble(STUB_HTTP, "baidu-ak", "amap-ak", null, null);

        assertInstanceOf(FailoverGeocoder.class, adapters.geocoder());
        assertInstanceOf(FailoverRouter.class, adapters.router());
        assertInstanceOf(FailoverTraffic.class, adapters.traffic());
    }

    @Test
    void only_baidu_ak_produces_single_vendor() {
        MapVendorFactory.VendorAdapters adapters =
                MapVendorFactory.assemble(STUB_HTTP, "baidu-ak", null, null, null);

        assertInstanceOf(BaiduGeocoder.class, adapters.geocoder());
        assertInstanceOf(BaiduRouter.class, adapters.router());
        assertInstanceOf(BaiduTraffic.class, adapters.traffic());
    }

    @Test
    void only_amap_ak_no_mcp_produces_http_vendor() {
        MapVendorFactory.VendorAdapters adapters =
                MapVendorFactory.assemble(STUB_HTTP, null, "amap-ak", null, null);

        assertInstanceOf(AmapGeocoderHttp.class, adapters.geocoder());
        assertInstanceOf(AmapRouterHttp.class, adapters.router());
        assertInstanceOf(AmapTrafficHttp.class, adapters.traffic());
    }

    @Test
    void only_amap_ak_with_mcp_produces_channel_failover() {
        McpClientWrapper stubMcp = stubMcpClient();
        MapVendorFactory.VendorAdapters adapters =
                MapVendorFactory.assemble(STUB_HTTP, null, "amap-ak", stubMcp, null);

        assertNotNull(adapters.geocoder());
        assertNotNull(adapters.router());
        assertNotNull(adapters.traffic());
    }

    @Test
    void no_ak_produces_null_adapters() {
        MapVendorFactory.VendorAdapters adapters =
                MapVendorFactory.assemble(STUB_HTTP, null, null, null, null);

        assertNull(adapters.geocoder());
        assertNull(adapters.router());
        assertNull(adapters.traffic());
    }

    @Test
    void blank_ak_treated_as_missing() {
        MapVendorFactory.VendorAdapters adapters =
                MapVendorFactory.assemble(STUB_HTTP, "  ", "", null, null);

        assertNull(adapters.geocoder());
        assertNull(adapters.router());
        assertNull(adapters.traffic());
    }

    @Test
    void dual_ak_adapters_are_not_null() {
        MapVendorFactory.VendorAdapters adapters =
                MapVendorFactory.assemble(STUB_HTTP, "b", "a", null, null);

        assertNotNull(adapters.geocoder());
        assertNotNull(adapters.router());
        assertNotNull(adapters.traffic());
    }

    @Test
    void default_connection_mode_is_long_lived() {
        assertEquals(MapVendorFactory.AmapMcpConnectionMode.LONG_LIVED,
                MapVendorFactory.parseConnectionMode(null));
        assertEquals(MapVendorFactory.AmapMcpConnectionMode.LONG_LIVED,
                MapVendorFactory.parseConnectionMode(""));
    }

    @Test
    void short_connection_mode_can_be_enabled_by_env_value() {
        assertEquals(MapVendorFactory.AmapMcpConnectionMode.SHORT_LIVED,
                MapVendorFactory.parseConnectionMode("short"));
        assertEquals(MapVendorFactory.AmapMcpConnectionMode.SHORT_LIVED,
                MapVendorFactory.parseConnectionMode("SHORT"));
    }

    @Test
    void long_lived_mode_keeps_provided_mcp_client() {
        McpClientWrapper stubMcp = stubMcpClient();
        MapVendorFactory.VendorAdapters adapters =
                MapVendorFactory.assemble(
                        STUB_HTTP,
                        null,
                        "amap-ak",
                        stubMcp,
                        null,
                        MapVendorFactory.AmapMcpConnectionMode.LONG_LIVED);

        assertNotNull(adapters.geocoder());
        assertNotNull(adapters.router());
        assertNotNull(adapters.traffic());
    }

    private static McpClientWrapper stubMcpClient() {
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
                return Mono.just(new McpSchema.CallToolResult("{}", false));
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
