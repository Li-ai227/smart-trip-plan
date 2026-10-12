package com.smart.trip.skills.itinerary;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜系知识库降级推荐（C-008 AC-3，{@code source=CUISINE_KB}）。
 *
 * <p>从 classpath {@code cuisine/*.json} 加载城市美食数据，按 {@link TravelStyle}
 * 标签过滤后确定性选择（同输入 → 同输出），作为 {@link AmapMealRecommender} 的降级层。</p>
 */
public final class CuisineKnowledgeBase implements MealRecommender {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final System.Logger LOG =
            System.getLogger(CuisineKnowledgeBase.class.getName());
    private static final String[] DEFAULT_CITIES = {"chengdu", "beijing", "shanghai"};

    private final Map<String, List<CuisineEntry>> cityData;

    record CuisineEntry(String name, List<String> dishes, int priceLow, int priceHigh,
                        List<String> tags) {}

    /**
     * 从 classpath 加载默认城市数据。
     */
    public CuisineKnowledgeBase() {
        this.cityData = new HashMap<>();
        for (String file : DEFAULT_CITIES) {
            loadFromClasspath("cuisine/" + file + ".json");
        }
    }

    /**
     * 测试构造器：直接注入数据，跳过文件加载。
     */
    CuisineKnowledgeBase(Map<String, List<CuisineEntry>> data) {
        this.cityData = new HashMap<>(data);
    }

    @Override
    public List<Meal> recommend(MealRecommendQuery query) {
        List<CuisineEntry> entries = cityData.get(query.city());
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        Set<String> preferredTags = tagsForStyle(query.style());
        List<CuisineEntry> filtered = preferredTags.isEmpty()
                ? entries
                : entries.stream().filter(e -> e.tags().stream().anyMatch(preferredTags::contains)).toList();
        if (filtered.isEmpty()) {
            filtered = entries;
        }

        int seed = seedFor(query);
        int index = Math.floorMod(seed, filtered.size());
        CuisineEntry chosen = filtered.get(index);

        BigDecimal avgPrice = BigDecimal.valueOf((chosen.priceLow() + chosen.priceHigh()) / 2);
        GeoLocation mealLoc = new GeoLocation(
                query.location().longitude() + 0.003,
                query.location().latitude() + 0.003,
                chosen.name(),
                query.city());

        Meal meal = new Meal(chosen.name(), mealLoc, query.mealType(), avgPrice, chosen.dishes());
        return List.of(meal);
    }

    private static Set<String> tagsForStyle(TravelStyle style) {
        if (style == null) {
            return Set.of();
        }
        return switch (style) {
            case FAMILY -> Set.of("家常菜", "粤菜", "江浙菜", "川菜");
            case COUPLE -> Set.of("私房菜", "西餐", "日料");
            case SOLO -> Set.of("小吃", "面馆", "快餐");
            case TEAM -> Set.of("火锅", "烧烤", "大排档");
        };
    }

    private static int seedFor(MealRecommendQuery query) {
        String raw = query.location().address() + "|" + query.mealType() + "|" + query.style();
        return raw.hashCode();
    }

    private void loadFromClasspath(String resource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                LOG.log(System.Logger.Level.WARNING, "菜系数据文件不存在: {0}", resource);
                return;
            }
            JsonNode root = MAPPER.readTree(is);
            String city = root.path("city").asText("");
            if (city.isEmpty()) {
                return;
            }
            JsonNode cuisines = root.path("cuisines");
            if (!cuisines.isArray()) {
                return;
            }
            List<CuisineEntry> entries = new ArrayList<>();
            for (JsonNode node : cuisines) {
                String name = node.path("name").asText("");
                if (name.isEmpty()) {
                    continue;
                }
                List<String> dishes = new ArrayList<>();
                node.path("dishes").forEach(d -> dishes.add(d.asText()));
                JsonNode pr = node.path("priceRange");
                int priceLow = pr.has(0) ? pr.get(0).asInt(0) : 0;
                int priceHigh = pr.has(1) ? pr.get(1).asInt(0) : 0;
                List<String> tags = new ArrayList<>();
                node.path("tags").forEach(t -> tags.add(t.asText()));
                entries.add(new CuisineEntry(name, List.copyOf(dishes), priceLow, priceHigh,
                        List.copyOf(tags)));
            }
            cityData.put(city, List.copyOf(entries));
        } catch (IOException ex) {
            LOG.log(System.Logger.Level.WARNING, "加载菜系数据失败: {0}", ex.getMessage());
        }
    }
}
