package com.smart.trip.skills.itinerary;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 高德 POI 周边搜索餐饮推荐（C-008 AC-3/AC-4 ⑦，主数据源 {@code source=AMAP}）。
 *
 * <p>调用 {@code GET https://restapi.amap.com/v3/place/around}，{@code types=050000}（餐饮服务），
 * 搜索半径 5000m（对齐强规则 ④ ≤5km），按 {@link TravelStyle} 注入 {@code keywords} 个性化。
 * 返回的 {@link Meal} 包含真实名称/坐标/人均价格。</p>
 *
 * <p>任何异常（网络/超时/API 错误）内部捕获返回空列表，由 {@link DegradingMealRecommender}
 * 自动降级到 {@link CuisineKnowledgeBase}。</p>
 */
public final class AmapMealRecommender implements MealRecommender {

    private static final String BASE_URL = "https://restapi.amap.com/v3/place/around";
    private static final String DINING_TYPE = "050000";
    private static final int RADIUS_METERS = 5000;
    private static final int PAGE_SIZE = 20;
    private static final double MIN_RATING = 3.5;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final System.Logger LOG =
            System.getLogger(AmapMealRecommender.class.getName());

    private final PoiHttpExecutor http;
    private final String ak;

    /**
     * @param http HTTP 执行端口，非 null
     * @param ak   高德 Web API Key，非空白
     */
    public AmapMealRecommender(PoiHttpExecutor http, String ak) {
        if (http == null) {
            throw new IllegalArgumentException("PoiHttpExecutor 不可为 null");
        }
        if (ak == null || ak.isBlank()) {
            throw new IllegalArgumentException("AMAP_MAP_AK 不可为空白");
        }
        this.http = http;
        this.ak = ak;
    }

    @Override
    public List<Meal> recommend(MealRecommendQuery query) {
        try {
            String url = buildUrl(query);
            String json = http.execute(url);
            return parsePois(json, query.mealType(), query.city());
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "高德 POI 搜索失败，将降级到知识库: {0}", ex.getMessage());
            return List.of();
        }
    }

    private String buildUrl(MealRecommendQuery query) {
        String location = query.location().longitude() + "," + query.location().latitude();
        StringBuilder sb = new StringBuilder(BASE_URL)
                .append("?key=").append(encode(ak))
                .append("&location=").append(encode(location))
                .append("&radius=").append(RADIUS_METERS)
                .append("&types=").append(DINING_TYPE)
                .append("&offset=").append(PAGE_SIZE)
                .append("&extensions=all");
        String keywords = keywordsForStyle(query.style());
        if (!keywords.isEmpty()) {
            sb.append("&keywords=").append(encode(keywords));
        }
        return sb.toString();
    }

    /**
     * TravelStyle → 高德 keywords 映射。
     *
     * @param style 出行风格，可为 null
     * @return 管道分隔关键词，null/未知返回空串
     */
    static String keywordsForStyle(TravelStyle style) {
        if (style == null) {
            return "";
        }
        return switch (style) {
            case FAMILY -> "家常菜|粤菜|江浙菜";
            case COUPLE -> "私房菜|西餐|日料";
            case SOLO -> "小吃|面馆|快餐";
            case TEAM -> "火锅|烧烤|大排档";
        };
    }

    /**
     * 解析高德 POI 周边搜索响应。
     *
     * @param json     响应 JSON
     * @param mealType 餐别
     * @param city     城市（用于构造 GeoLocation）
     * @return 餐饮列表，过滤 rating &lt; 3.5
     */
    static List<Meal> parsePois(String json, MealType mealType, String city) {
        try {
            JsonNode root = MAPPER.readTree(json);
            checkStatus(root);
            JsonNode pois = root.path("pois");
            if (!pois.isArray()) {
                return List.of();
            }
            List<Meal> meals = new ArrayList<>();
            for (JsonNode poi : pois) {
                Meal meal = parseSinglePoi(poi, mealType, city);
                if (meal != null) {
                    meals.add(meal);
                }
            }
            return meals;
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "高德 POI 响应解析失败: {0}", ex.getMessage());
            return List.of();
        }
    }

    private static Meal parseSinglePoi(JsonNode poi, MealType mealType, String city) {
        String name = poi.path("name").asText("");
        if (name.isEmpty()) {
            return null;
        }

        double rating = parseRating(poi);
        if (rating > 0 && rating < MIN_RATING) {
            return null;
        }

        GeoLocation location = parseLocation(poi, name, city);
        if (location == null) {
            return null;
        }

        BigDecimal avgPrice = parseCost(poi);

        return new Meal(name, location, mealType, avgPrice, List.of());
    }

    private static double parseRating(JsonNode poi) {
        String ratingStr = poi.path("biz_ext").path("rating").asText("");
        if (ratingStr.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(ratingStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BigDecimal parseCost(JsonNode poi) {
        String costStr = poi.path("biz_ext").path("cost").asText("");
        if (costStr.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(costStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static GeoLocation parseLocation(JsonNode poi, String name, String city) {
        String locStr = poi.path("location").asText("");
        if (locStr.isEmpty()) {
            return null;
        }
        String[] parts = locStr.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            double lng = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);
            String address = poi.path("address").asText(name);
            return new GeoLocation(lng, lat, address, city);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void checkStatus(JsonNode root) {
        String status = root.path("status").asText("");
        if (!"1".equals(status)) {
            String info = root.path("info").asText("未知错误");
            throw new IllegalStateException("高德 API 返回错误: status=" + status + ", info=" + info);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
