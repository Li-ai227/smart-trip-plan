package com.smart.trip.agent.route.mcp;

import com.smart.trip.skills.env.EnvAccessor;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.MapGeocoder;
import com.smart.trip.skills.route.MapRouter;
import com.smart.trip.skills.route.MapTraffic;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;

/**
 * 地图厂商适配器工厂（C-007 增量 B / AC-9e）。
 *
 * <p>从环境变量读取 AK，装配百度 + 高德双厂商 failover。
 * 百度：HTTP 直连；高德：AgentScope 原生 MCP 主通道 + HTTP 降级通道（双通道 failover）。
 * AK 仅在此处 {@code System.getenv()} 读取，禁止硬编码。</p>
 *
 * <p>MCP 客户端使用 AgentScope 原生 {@link McpClientBuilder}，
 * 不引入未经评审的新依赖（设计约束）。</p>
 */
public final class MapVendorFactory {

    private static final System.Logger LOG = System.getLogger(MapVendorFactory.class.getName());
    private static final Duration MCP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MCP_INIT_TIMEOUT = Duration.ofSeconds(10);

    /** 高德 MCP Server SSE URL 模板。 */
    static final String AMAP_MCP_SSE_URL = "https://mcp.amap.com/sse";

    /** 百度地图 AK 环境变量名。 */
    static final String ENV_BAIDU_AK = "BAIDU_MAP_AK";

    /** 高德地图 AK 环境变量名。 */
    static final String ENV_AMAP_AK = "AMAP_MAP_AK";

    /** 高德 MCP 连接模式环境变量名。 */
    static final String ENV_AMAP_MCP_CONNECTION_MODE = "AMAP_MCP_CONNECTION_MODE";

    private MapVendorFactory() {
    }

    enum AmapMcpConnectionMode {
        LONG_LIVED,
        SHORT_LIVED
    }

    /**
     * 工厂装配结果。
     *
     * @param geocoder 地理编码器（可 null 表示无 AK 可用）
     * @param router   路线规划器（可 null）
     * @param traffic  路况查询器（可 null）
     */
    public record VendorAdapters(MapGeocoder geocoder, MapRouter router, MapTraffic traffic) {
    }

    /**
     * 从环境变量装配适配器（支持 Spring {@link EnvAccessor}，推荐入口）。
     *
     * <p>相比 {@link #fromEnvironment(MapHttpExecutor, AgentMetrics)}，
     * 本方法通过 {@link EnvAccessor} 读取密钥，可正确感知 Spring Environment /
     * {@code .env} 文件中配置的 AK，适用于 Spring 上下文中的 HarnessAgent 原生路装配。</p>
     *
     * @param http    HTTP 执行器
     * @param metrics 指标（可 null）
     * @param env     环境变量访问器（AK 禁止硬编码，ADR-009），非 null
     * @return 装配结果
     */
    public static VendorAdapters fromEnvironment(MapHttpExecutor http, AgentMetrics metrics,
                                                 EnvAccessor env) {
        String baiduAk = env.get(ENV_BAIDU_AK);
        String amapAk = env.get(ENV_AMAP_AK);
        AmapMcpConnectionMode connectionMode =
                parseConnectionMode(env.get(ENV_AMAP_MCP_CONNECTION_MODE));
        McpClientWrapper mcpClient = null;
        if (connectionMode == AmapMcpConnectionMode.LONG_LIVED
                && amapAk != null && !amapAk.isBlank()) {
            mcpClient = createLongLivedMcpClient(amapAk);
        }
        return assemble(http, baiduAk, amapAk, mcpClient, metrics, connectionMode);
    }

    /**
     * 从环境变量装配适配器（直接读 {@link System#getenv()}）。
     *
     * @param http    HTTP 执行器
     * @param metrics 指标（可 null）
     * @return 装配结果
     * @deprecated 请使用 {@link #fromEnvironment(MapHttpExecutor, AgentMetrics, EnvAccessor)}，
     *             支持 Spring Environment / .env 文件中配置的 AK。
     */
    @Deprecated
    public static VendorAdapters fromEnvironment(MapHttpExecutor http, AgentMetrics metrics) {
        return fromEnvironment(http, metrics, EnvAccessor.fromSystemEnv());
    }

    /**
     * 按 AK 装配（可测试入口，不依赖环境变量）。
     */
    public static VendorAdapters assemble(MapHttpExecutor http, String baiduAk, String amapAk,
                                          McpClientWrapper mcpClient, AgentMetrics metrics) {
        return assemble(http, baiduAk, amapAk, mcpClient, metrics, AmapMcpConnectionMode.LONG_LIVED);
    }

    static VendorAdapters assemble(MapHttpExecutor http, String baiduAk, String amapAk,
                                   McpClientWrapper mcpClient, AgentMetrics metrics,
                                   AmapMcpConnectionMode connectionMode) {
        boolean hasBaidu = baiduAk != null && !baiduAk.isBlank();
        boolean hasAmap = amapAk != null && !amapAk.isBlank();

        if (hasBaidu && hasAmap) {
            return buildFailover(http, baiduAk, amapAk, mcpClient, metrics, connectionMode);
        }
        if (hasBaidu) {
            LOG.log(System.Logger.Level.WARNING, "仅百度地图 AK 可用，无 failover");
            GeocodingCache cache = new GeocodingCache();
            return new VendorAdapters(
                    new BaiduGeocoder(http, baiduAk, cache),
                    new BaiduRouter(http, baiduAk),
                    new BaiduTraffic(http, baiduAk));
        }
        if (hasAmap) {
            LOG.log(System.Logger.Level.WARNING, "仅高德地图 AK 可用，无 failover");
            return buildAmapChannel(http, amapAk, mcpClient, metrics, connectionMode);
        }

        LOG.log(System.Logger.Level.ERROR,
                "无地图 AK 可用（{0}/{1} 均未设置），地图功能不可用",
                ENV_BAIDU_AK, ENV_AMAP_AK);
        return new VendorAdapters(null, null, null);
    }

    private static VendorAdapters buildFailover(MapHttpExecutor http, String baiduAk,
                                                String amapAk, McpClientWrapper mcpClient,
                                                AgentMetrics metrics,
                                                AmapMcpConnectionMode connectionMode) {
        GeocodingCache cache = new GeocodingCache();

        VendorHealth baiduHealth = new VendorHealth(VendorId.BAIDU);
        VendorHealth amapHealth = new VendorHealth(VendorId.AMAP);

        AmapChannel amap = buildAmapChannelPair(http, amapAk, cache, mcpClient, metrics, connectionMode);

        MapGeocoder geocoder = new FailoverGeocoder(
                new BaiduGeocoder(http, baiduAk, cache),
                amap.geocoder, baiduHealth, amapHealth, metrics);

        MapRouter router = new FailoverRouter(
                new BaiduRouter(http, baiduAk),
                amap.router, baiduHealth, amapHealth, metrics);

        MapTraffic traffic = new FailoverTraffic(
                new BaiduTraffic(http, baiduAk),
                amap.traffic, baiduHealth, amapHealth, metrics);

        return new VendorAdapters(geocoder, router, traffic);
    }

    private static VendorAdapters buildAmapChannel(MapHttpExecutor http, String amapAk,
                                                   McpClientWrapper mcpClient,
                                                   AgentMetrics metrics,
                                                   AmapMcpConnectionMode connectionMode) {
        GeocodingCache cache = new GeocodingCache();
        AmapChannel ch = buildAmapChannelPair(http, amapAk, cache, mcpClient, metrics, connectionMode);
        return new VendorAdapters(ch.geocoder, ch.router, ch.traffic);
    }

    private static AmapChannel buildAmapChannelPair(MapHttpExecutor http, String amapAk,
                                                    GeocodingCache cache,
                                                    McpClientWrapper mcpClient,
                                                    AgentMetrics metrics,
                                                    AmapMcpConnectionMode connectionMode) {
        AmapGeocoderHttp httpGeo = new AmapGeocoderHttp(http, amapAk, cache);
        AmapRouterHttp httpRoute = new AmapRouterHttp(http, amapAk);
        AmapTrafficHttp httpTraffic = new AmapTrafficHttp(http, amapAk);

        McpClientWrapper effectiveClient = mcpClient;
        if (effectiveClient == null && connectionMode == AmapMcpConnectionMode.SHORT_LIVED
                && amapAk != null && !amapAk.isBlank()) {
            effectiveClient = createShortLivedProxy(amapAk);
        }

        if (effectiveClient == null) {
            LOG.log(System.Logger.Level.INFO, "高德仅 HTTP 直连（无 MCP 客户端）");
            return new AmapChannel(httpGeo, httpRoute, httpTraffic);
        }

        AmapMcpClient mcp = new AmapMcpClient(effectiveClient, cache);
        VendorHealth mcpHealth = new VendorHealth(VendorId.AMAP);
        VendorHealth httpHealth = new VendorHealth(VendorId.AMAP);
        ChannelFailoverExecutor channel = new ChannelFailoverExecutor(
                mcpHealth, httpHealth, metrics);

        MapGeocoder geocoder = (addr, city) -> channel.execute(
                () -> mcp.geocode(addr, city), () -> httpGeo.geocode(addr, city));
        MapRouter router = (from, to) -> channel.execute(
                () -> mcp.route(from, to), () -> httpRoute.route(from, to));
        MapTraffic traffic = (from, to) -> channel.execute(
                () -> mcp.trafficFactor(from, to), () -> httpTraffic.trafficFactor(from, to));

        LOG.log(System.Logger.Level.INFO, "高德 MCP 主通道 + HTTP 降级通道已装配，mode={0}",
                connectionMode.name());
        return new AmapChannel(geocoder, router, traffic);
    }

    static AmapMcpConnectionMode parseConnectionMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return AmapMcpConnectionMode.LONG_LIVED;
        }
        if ("short".equalsIgnoreCase(rawMode.trim())) {
            return AmapMcpConnectionMode.SHORT_LIVED;
        }
        return AmapMcpConnectionMode.LONG_LIVED;
    }

    private static McpClientWrapper createLongLivedMcpClient(String amapAk) {
        McpClientWrapper mcpClient = null;
        String fullSseUrl = AMAP_MCP_SSE_URL + "?key=" + amapAk;
        try {
            mcpClient = McpClientBuilder.create("amap")
                    .sseTransport(fullSseUrl)
                    .timeout(MCP_TIMEOUT)
                    .buildSync();
            // block 限时 10s：若 mcp.amap.com 不可达则快速降级 HTTP，不阻塞启动
            mcpClient.initialize().block(MCP_INIT_TIMEOUT);
            LOG.log(System.Logger.Level.INFO,
                    "高德 MCP 客户端初始化成功: name={0}, url={1}",
                    "amap", AMAP_MCP_SSE_URL);
            return new ReconnectingMcpClientWrapper(
                    "amap", mcpClient, fullSseUrl, MCP_TIMEOUT, MCP_INIT_TIMEOUT);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "MCP 客户端初始化失败，高德降级为仅 HTTP: {0}", e.getMessage());
            closeQuietly(mcpClient);
            return null;
        }
    }

    private static McpClientWrapper createShortLivedProxy(String amapAk) {
        return new McpClientWrapper("amap-short-lived") {
            @Override
            public reactor.core.publisher.Mono<Void> initialize() {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public reactor.core.publisher.Mono<java.util.List<io.modelcontextprotocol.spec.McpSchema.Tool>> listTools() {
                return reactor.core.publisher.Mono.error(
                        new UnsupportedOperationException("short-lived proxy does not support listTools"));
            }

            @Override
            public reactor.core.publisher.Mono<io.modelcontextprotocol.spec.McpSchema.CallToolResult> callTool(
                    String name, java.util.Map<String, Object> args) {
                McpClientWrapper delegate = null;
                try {
                    delegate = McpClientBuilder.create("amap")
                            .sseTransport(AMAP_MCP_SSE_URL + "?key=" + amapAk)
                            .timeout(MCP_TIMEOUT)
                            .buildSync();
                    // block 限时 10s：若 mcp.amap.com 不可达则快速降级 HTTP，不阻塞启动
                    delegate.initialize().block(MCP_INIT_TIMEOUT);
                    final McpClientWrapper activeClient = delegate;
                    return delegate.callTool(name, args)
                            .doFinally(signalType -> closeQuietly(activeClient));
                } catch (Exception ex) {
                    closeQuietly(delegate);
                    return reactor.core.publisher.Mono.error(ex);
                }
            }

            @Override
            public reactor.core.publisher.Mono<io.modelcontextprotocol.spec.McpSchema.CallToolResult> callTool(
                    String name, java.util.Map<String, Object> args,
                    java.util.Map<String, Object> metadata) {
                return callTool(name, args);
            }

            @Override
            public void close() {
                // no-op: short-lived delegate closes per call
            }
        };
    }

    private static void closeQuietly(McpClientWrapper client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception ignored) {
            // ignore cleanup failure
        }
    }

    private record AmapChannel(MapGeocoder geocoder, MapRouter router, MapTraffic traffic) {
    }
}
