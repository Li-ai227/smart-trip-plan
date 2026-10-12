package com.smart.trip.agent.route.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.skills.route.MapGeocoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 高德地图地理编码 HTTP 适配器（C-007 增量 B / T-7）。
 *
 * <p>调用高德 Geocoding API v3，将地址解析为经纬度。AK 由构造器注入（环境变量，
 * 禁硬编码）。共享 {@link GeocodingCache}（厂商无关）。此实现作为 MCP 不可用时的
 * 降级层。</p>
 */
public final class AmapGeocoderHttp implements MapGeocoder {

    private static final String BASE_URL = "https://restapi.amap.com/v3/geocode/geo";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final MapHttpExecutor http;
    private final String ak;
    private final GeocodingCache cache;

    /**
     * @param http  HTTP 执行器，非 null
     * @param ak    高德地图 AK（环境变量注入，禁硬编码），非空白
     * @param cache 坐标缓存，可为 null（不缓存）
     */
    public AmapGeocoderHttp(MapHttpExecutor http, String ak, GeocodingCache cache) {
        if (http == null) {
            throw new IllegalArgumentException("http 不可为 null");
        }
        if (ak == null || ak.isBlank()) {
            throw new IllegalArgumentException("高德地图 AK 不可为空");
        }
        this.http = http;
        this.ak = ak;
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

        String url = BASE_URL + "?address=" + encode(address)
                + "&city=" + encode(city != null ? city : "")
                + "&key=" + encode(ak)
                + "&output=JSON";

        String body = http.execute(url);
        GeoLocation result = parseResponse(body, address, city);

        if (cache != null) {
            cache.put(address, city, result);
        }
        return result;
    }

    private static GeoLocation parseResponse(String json, String address, String city)
            throws MapApiException {
        try {
            JsonNode root = MAPPER.readTree(json);
            String status = root.path("status").asText("");
            if (!"1".equals(status)) {
                String info = root.path("info").asText("unknown");
                throw new MapApiException(Integer.parseInt(root.path("infocode").asText("-1")),
                        "高德地理编码失败：" + info);
            }
            JsonNode geocodes = root.path("geocodes");
            if (!geocodes.isArray() || geocodes.isEmpty()) {
                throw new MapApiException(0, "高德地理编码无结果");
            }
            String location = geocodes.get(0).path("location").asText("");
            String[] parts = location.split(",");
            if (parts.length != 2) {
                throw new MapApiException(-1, "高德坐标格式异常：" + location);
            }
            double lng = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);
            return new GeoLocation(lng, lat, address, city);
        } catch (MapApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MapApiException(-1, "高德地理编码响应解析失败", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
