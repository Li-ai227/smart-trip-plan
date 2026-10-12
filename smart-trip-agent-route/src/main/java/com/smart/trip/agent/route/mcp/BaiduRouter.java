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
 * 百度地图路线规划适配器（C-007 增量 B / AC-8）。
 *
 * <p>调用百度地图驾车路线规划 API，返回两点间距离和耗时。</p>
 */
public final class BaiduRouter implements MapRouter {

    private static final String BASE_URL = "https://api.map.baidu.com/direction/v2/driving";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final MapHttpExecutor http;
    private final String ak;

    /**
     * @param http HTTP 执行器，非 null
     * @param ak   百度地图 AK（环境变量注入），非空白
     */
    public BaiduRouter(MapHttpExecutor http, String ak) {
        if (http == null) {
            throw new IllegalArgumentException("http 不可为 null");
        }
        if (ak == null || ak.isBlank()) {
            throw new IllegalArgumentException("百度地图 AK 不可为空");
        }
        this.http = http;
        this.ak = ak;
    }

    @Override
    public Route route(GeoLocation from, GeoLocation to) throws Exception {
        String origin = from.latitude() + "," + from.longitude();
        String destination = to.latitude() + "," + to.longitude();

        String url = BASE_URL + "?origin=" + encode(origin)
                + "&destination=" + encode(destination)
                + "&ak=" + encode(ak);

        String body = http.execute(url);
        return parseResponse(body, from, to);
    }

    private static Route parseResponse(String json, GeoLocation from, GeoLocation to)
            throws MapApiException {
        try {
            JsonNode root = MAPPER.readTree(json);
            int status = root.path("status").asInt(-1);
            if (status != 0) {
                throw new MapApiException(status,
                        "百度路线规划失败: status=" + status);
            }
            JsonNode routes = root.path("result").path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                throw new MapApiException(0, "百度路线规划无可用路线");
            }
            JsonNode first = routes.get(0);
            double distanceMeters = first.path("distance").asDouble();
            double durationSeconds = first.path("duration").asDouble();
            double distanceKm = distanceMeters / 1000.0;
            int durationMin = (int) Math.ceil(durationSeconds / 60.0);
            return new Route(from, to, distanceKm, durationMin, "驾车", false);
        } catch (MapApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MapApiException(-1, "百度路线规划响应解析失败", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
