package com.smart.trip.agent.route.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.skills.route.MapTraffic;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 百度地图路况适配器（C-007 增量 B / AC-8）。
 *
 * <p>利用百度驾车路线规划 API 的 {@code traffic_condition} 信息推算拥堵系数。
 * 该端口为可选增强——不可用时静默忽略（AC-5 ③），不标记 fallback。</p>
 */
public final class BaiduTraffic implements MapTraffic {

    private static final String BASE_URL = "https://api.map.baidu.com/direction/v2/driving";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final MapHttpExecutor http;
    private final String ak;

    /**
     * @param http HTTP 执行器，非 null
     * @param ak   百度地图 AK（环境变量注入），非空白
     */
    public BaiduTraffic(MapHttpExecutor http, String ak) {
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
    public double trafficFactor(GeoLocation from, GeoLocation to) throws Exception {
        String origin = from.latitude() + "," + from.longitude();
        String destination = to.latitude() + "," + to.longitude();

        String url = BASE_URL + "?origin=" + encode(origin)
                + "&destination=" + encode(destination)
                + "&ak=" + encode(ak)
                + "&alternatives=0&tactics=0";

        String body = http.execute(url);
        return parseTrafficFactor(body);
    }

    private static double parseTrafficFactor(String json) throws MapApiException {
        try {
            JsonNode root = MAPPER.readTree(json);
            int status = root.path("status").asInt(-1);
            if (status != 0) {
                throw new MapApiException(status, "百度路况查询失败: status=" + status);
            }
            JsonNode routes = root.path("result").path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                return 1.0;
            }
            JsonNode first = routes.get(0);
            double duration = first.path("duration").asDouble(0);
            double trafficDuration = first.path("traffic_condition_duration").asDouble(0);
            if (duration <= 0 || trafficDuration <= 0) {
                return 1.0;
            }
            return trafficDuration / duration;
        } catch (MapApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MapApiException(-1, "百度路况响应解析失败", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
