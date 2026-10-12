package com.smart.trip.agent.route.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.skills.route.MapGeocoder;
import com.smart.trip.skills.route.MapRouter;
import com.smart.trip.skills.route.MapTraffic;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.Optional;

/**
 * 高德 MCP Server 适配器（C-007 增量 B / T-8 / AC-8）。
 *
 * <p>通过 AgentScope 原生 {@link McpClientWrapper} 调用高德 MCP Server 提供的工具
 * （{@code maps_geo}、{@code maps_direction_driving}），实现
 * {@link MapGeocoder}、{@link MapRouter}、{@link MapTraffic} 三端口。
 * 作为高德地图的 MCP 主通道，HTTP 直连（{@link AmapGeocoderHttp}/{@link AmapRouterHttp}/{@link AmapTrafficHttp}）
 * 作为降级层（AC-9b）。</p>
 *
 * <p>传输层由 AgentScope {@code McpClientBuilder.create().sseTransport()} 提供，
 * 测试以匿名子类桩注入确定性响应（编码规范 §8.2）。</p>
 */
@SuppressWarnings("PMD.GodClass")
public final class AmapMcpClient implements MapGeocoder, MapRouter, MapTraffic {

    static final String TOOL_GEO = "maps_geo"; // 地理编码
    static final String TOOL_DRIVING = "maps_direction_driving"; // 驾车路线规划

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final McpClientWrapper mcpClient;
    private final GeocodingCache cache;

    /**
     * @param mcpClient AgentScope MCP 客户端，非 null
     * @param cache     坐标缓存（可为 null，不缓存）
     */
    public AmapMcpClient(McpClientWrapper mcpClient, GeocodingCache cache) {
        if (mcpClient == null) {
            throw new IllegalArgumentException("mcpClient 不可为 null");
        }
        this.mcpClient = mcpClient;
        this.cache = cache;
    }

    @Override
    public GeoLocation geocode(String address, String city) throws Exception {
        if (cache != null) {
            Optional<GeoLocation> cached = cache.get(address, city);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        Map<String, Object> args = (city != null && !city.isBlank())
                ? Map.of("address", address, "city", city)
                : Map.of("address", address);

        String resultText = callMcpTool(TOOL_GEO, args);
        GeoLocation result = parseGeoResult(resultText, address, city);

        if (cache != null) {
            cache.put(address, city, result);
        }
        return result;
    }

    @Override
    public Route route(GeoLocation from, GeoLocation to) throws Exception {
        Map<String, Object> args = Map.of(
                "origin", from.longitude() + "," + from.latitude(),
                "destination", to.longitude() + "," + to.latitude());

        String resultText = callMcpTool(TOOL_DRIVING, args);
        return parseRouteResult(resultText, from, to);
    }

    @Override
    public double trafficFactor(GeoLocation from, GeoLocation to) throws Exception {
        Map<String, Object> args = Map.of(
                "origin", from.longitude() + "," + from.latitude(),
                "destination", to.longitude() + "," + to.latitude());

        String resultText = callMcpTool(TOOL_DRIVING, args);
        return parseTrafficFactor(resultText);
    }

    private String callMcpTool(String name, Map<String, Object> args) throws Exception {
        McpSchema.CallToolResult result;
        try {
            result = mcpClient.callTool(name, args).block();
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw e;
        }
        if (result == null) {
            throw new MapApiException(-1, "MCP 调用无响应: " + name);
        }
        if (Boolean.TRUE.equals(result.isError())) {
            throw new MapApiException(-1, "MCP 工具错误: " + extractText(result));
        }
        return extractText(result);
    }

    private static String extractText(McpSchema.CallToolResult result) {
        if (result.content() != null) {
            for (McpSchema.Content c : result.content()) {
                if (c instanceof McpSchema.TextContent tc) {
                    return tc.text();
                }
            }
        }
        return "";
    }

    static GeoLocation parseGeoResult(String text, String address, String city)
            throws MapApiException {
        try {
            JsonNode root = MAPPER.readTree(text);
            JsonNode geocodes = root.path("geocodes");
            if (geocodes.isArray() && !geocodes.isEmpty()) {
                return extractLocationFromGeocode(geocodes.get(0), address, city);
            }
            String location = root.path("location").asText("");
            if (!location.isEmpty()) {
                return parseLocationString(location, address, city);
            }
            throw new MapApiException(0, "MCP 地理编码无结果");
        } catch (MapApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MapApiException(-1, "MCP 地理编码响应解析失败", e);
        }
    }

    private static GeoLocation extractLocationFromGeocode(JsonNode geocode,
                                                          String address, String city)
            throws MapApiException {
        String location = geocode.path("location").asText("");
        if (location.isEmpty()) {
            throw new MapApiException(0, "MCP 地理编码坐标缺失");
        }
        return parseLocationString(location, address, city);
    }

    private static GeoLocation parseLocationString(String location,
                                                   String address, String city)
            throws MapApiException {
        String[] parts = location.split(",");
        if (parts.length != 2) {
            throw new MapApiException(-1, "MCP 坐标格式异常: " + location);
        }
        try {
            double lng = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);
            return new GeoLocation(lng, lat, address, city);
        } catch (NumberFormatException e) {
            throw new MapApiException(-1, "MCP 坐标解析失败: " + location, e);
        }
    }

    static Route parseRouteResult(String text, GeoLocation from, GeoLocation to)
            throws MapApiException {
        try {
            JsonNode root = MAPPER.readTree(text);
            JsonNode paths = root.path("paths");
            if (!paths.isArray() || paths.isEmpty()) {
                paths = root.path("route").path("paths");
            }
            if (!paths.isArray() || paths.isEmpty()) {
                return new Route(from, to, 0, 0, "驾车", true);
            }
            return computeRoute(paths.get(0), from, to);
        } catch (Exception e) {
            throw new MapApiException(-1, "MCP 路线规划响应解析失败", e);
        }
    }

    private static Route computeRoute(JsonNode path, GeoLocation from, GeoLocation to) {
        long distance = path.path("distance").asLong(0);
        long duration = path.path("duration").asLong(0);

        double distanceKm = Math.round(distance / 1000.0 * 10.0) / 10.0;
        int durationMin = (int) Math.ceil(duration / 60.0);

        return new Route(from, to, distanceKm, durationMin, "驾车", false);
    }

    static double parseTrafficFactor(String text) throws MapApiException {
        try {
            JsonNode root = MAPPER.readTree(text);
            JsonNode paths = root.path("paths");
            if (!paths.isArray() || paths.isEmpty()) {
                paths = root.path("route").path("paths");
            }
            if (!paths.isArray() || paths.isEmpty()) {
                return 1.0;
            }
            return computeCongestionFactor(paths.get(0));
        } catch (Exception e) {
            throw new MapApiException(-1, "MCP 路况响应解析失败", e);
        }
    }

    private static double computeCongestionFactor(JsonNode path) {
        double totalDistance = path.path("distance").asDouble(0);
        JsonNode steps = path.path("steps");
        if (!steps.isArray() || steps.isEmpty() || totalDistance <= 0) {
            return 1.0;
        }
        double congestedDistance = 0;
        for (JsonNode step : steps) {
            JsonNode tmcList = step.path("tmcs");
            if (!tmcList.isArray()) {
                continue;
            }
            for (JsonNode tmc : tmcList) {
                String tmcStatus = tmc.path("status").asText("");
                if ("拥堵".equals(tmcStatus) || "缓行".equals(tmcStatus)) {
                    congestedDistance += tmc.path("distance").asDouble(0);
                }
            }
        }
        return 1.0 + congestedDistance / totalDistance;
    }
}
