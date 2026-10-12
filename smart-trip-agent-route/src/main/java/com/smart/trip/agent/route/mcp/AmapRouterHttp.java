package com.smart.trip.agent.route.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.skills.route.MapRouter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 高德地图路线规划 HTTP 适配器（C-007 增量 B / T-7）。
 *
 * <p>调用高德 Route API v3，计算点间驾车路线。AK 由构造器注入（环境变量，禁硬编码）。
 * 此实现作为 MCP 不可用时的降级层。</p>
 */
public final class AmapRouterHttp implements MapRouter {

    private static final String BASE_URL = "https://restapi.amap.com/v3/direction/driving";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final MapHttpExecutor http;
    private final String ak;

    /**
     * @param http HTTP 执行器，非 null
     * @param ak   高德地图 AK（环境变量注入，禁硬编码），非空白
     */
    public AmapRouterHttp(MapHttpExecutor http, String ak) {
        if (http == null) {
            throw new IllegalArgumentException("http 不可为 null");
        }
        if (ak == null || ak.isBlank()) {
            throw new IllegalArgumentException("高德地图 AK 不可为空");
        }
        this.http = http;
        this.ak = ak;
    }

    @Override
    public Route route(GeoLocation from, GeoLocation to) throws Exception {
        String origin = from.longitude() + "," + from.latitude();
        String destination = to.longitude() + "," + to.latitude();

        String url = BASE_URL + "?origin=" + encode(origin)
                + "&destination=" + encode(destination)
                + "&key=" + encode(ak)
                + "&extensions=all";

        String body = http.execute(url);
        return parseRoute(body, from, to);
    }

    private static Route parseRoute(String json, GeoLocation from, GeoLocation to)
            throws MapApiException {
        try {
            JsonNode root = MAPPER.readTree(json);
            checkStatus(root);
            JsonNode paths = root.path("route").path("paths");
            if (!paths.isArray() || paths.isEmpty()) {
                return new Route(from, to, 0, 0, "驾车", true);
            }
            return computeRoute(paths.get(0), from, to);
        } catch (MapApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MapApiException(-1, "高德路线规划响应解析失败", e);
        }
    }

    private static void checkStatus(JsonNode root) throws MapApiException {
        String status = root.path("status").asText("");
        if (!"1".equals(status)) {
            throw new MapApiException(Integer.parseInt(root.path("infocode").asText("-1")),
                    "高德路线规划失败");
        }
    }

    private static Route computeRoute(JsonNode path, GeoLocation from, GeoLocation to) {
        long distance = path.path("distance").asLong(0);
        long duration = path.path("duration").asLong(0);

        // 单位转换：米→公里，秒→分钟（向上取整）
        double distanceKm = Math.round(distance / 1000.0 * 10.0) / 10.0;
        int durationMin = (int) Math.ceil(duration / 60.0);

        return new Route(from, to, distanceKm, durationMin, "驾车", false);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
