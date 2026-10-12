package com.smart.trip.agent.route.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.skills.route.MapTraffic;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 高德地图路况 HTTP 适配器（C-007 增量 B / T-7）。
 *
 * <p>利用高德驾车路线规划 API 的路况信息推算拥堵系数。
 * 可选增强——不可用时静默忽略（AC-5 ③）。此实现作为 MCP 不可用时的降级层。</p>
 */
public final class AmapTrafficHttp implements MapTraffic {

    private static final String BASE_URL = "https://restapi.amap.com/v3/direction/driving";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final MapHttpExecutor http;
    private final String ak;

    /**
     * @param http HTTP 执行器，非 null
     * @param ak   高德地图 AK（环境变量注入），非空白
     */
    public AmapTrafficHttp(MapHttpExecutor http, String ak) {
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
    public double trafficFactor(GeoLocation from, GeoLocation to) throws Exception {
        String origin = from.longitude() + "," + from.latitude();
        String destination = to.longitude() + "," + to.latitude();

        String url = BASE_URL + "?origin=" + encode(origin)
                + "&destination=" + encode(destination)
                + "&key=" + encode(ak)
                + "&extensions=all";

        String body = http.execute(url);
        return parseTrafficFactor(body);
    }

    private static double parseTrafficFactor(String json) throws MapApiException {
        try {
            JsonNode root = MAPPER.readTree(json);
            checkAmapStatus(root);
            JsonNode paths = root.path("route").path("paths");
            if (!paths.isArray() || paths.isEmpty()) {
                return 1.0;
            }
            return computeCongestionFactor(paths.get(0));
        } catch (MapApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MapApiException(-1, "高德路况响应解析失败", e);
        }
    }

    private static void checkAmapStatus(JsonNode root) throws MapApiException {
        String status = root.path("status").asText("");
        if (!"1".equals(status)) {
            throw new MapApiException(Integer.parseInt(root.path("infocode").asText("-1")),
                    "高德路况查询失败");
        }
    }

    private static double computeCongestionFactor(JsonNode path) {
        double totalDistance = path.path("distance").asDouble(0);
        JsonNode steps = path.path("steps");
        if (!steps.isArray() || steps.isEmpty() || totalDistance <= 0) {
            return 1.0;
        }
        double congestedDistance = sumCongestedDistance(steps);
        return 1.0 + congestedDistance / totalDistance;
    }

    private static double sumCongestedDistance(JsonNode steps) {
        double sum = 0;
        for (JsonNode step : steps) {
            JsonNode tmcList = step.path("tmcs");
            if (!tmcList.isArray()) {
                continue;
            }
            for (JsonNode tmc : tmcList) {
                String tmcStatus = tmc.path("status").asText("");
                if ("拥堵".equals(tmcStatus) || "缓行".equals(tmcStatus)) {
                    sum += tmc.path("distance").asDouble(0);
                }
            }
        }
        return sum;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
