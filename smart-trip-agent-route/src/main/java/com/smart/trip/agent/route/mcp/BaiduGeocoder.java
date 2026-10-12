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
 * 百度地图地理编码适配器（C-007 增量 B / AC-8）。
 *
 * <p>调用百度地图 Geocoding API v3，将地址解析为经纬度。AK 由构造器注入（环境变量，
 * 禁硬编码）。查询结果写入 {@link GeocodingCache}（厂商无关，避免重复调用）。</p>
 */
public final class BaiduGeocoder implements MapGeocoder {

    private static final String BASE_URL = "https://api.map.baidu.com/geocoding/v3/";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final MapHttpExecutor http;
    private final String ak;
    private final GeocodingCache cache;

    /**
     * @param http  HTTP 执行器，非 null
     * @param ak    百度地图 AK（环境变量注入，禁硬编码），非空白
     * @param cache 坐标缓存，可为 null（不缓存）
     */
    public BaiduGeocoder(MapHttpExecutor http, String ak, GeocodingCache cache) {
        if (http == null) {
            throw new IllegalArgumentException("http 不可为 null");
        }
        if (ak == null || ak.isBlank()) {
            throw new IllegalArgumentException("百度地图 AK 不可为空");
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
                + "&ak=" + encode(ak)
                + "&output=json";

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
            int status = root.path("status").asInt(-1);
            if (status != 0) {
                throw new MapApiException(status,
                        "百度地理编码失败: status=" + status);
            }
            JsonNode location = root.path("result").path("location");
            double lng = location.path("lng").asDouble();
            double lat = location.path("lat").asDouble();
            if (lng == 0 && lat == 0) {
                throw new MapApiException(0, "百度地理编码返回坐标为零");
            }
            return new GeoLocation(lng, lat, address, city);
        } catch (MapApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MapApiException(-1, "百度地理编码响应解析失败", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
