package com.smart.trip.skills.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.TripDay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 餐饮降级链路集成测试（C-008 T-6）：Amap → CuisineKnowledgeBase → FALLBACK
 * 全链路穿透 {@link ItineraryDesignService} 验证。
 */
class ItineraryMealDegradationTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 7, 1);
    private static final GeoLocation CENTER = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");

    private static final String AMAP_JSON_LUNCH = """
            {"status":"1","pois":[
              {"name":"蜀九香火锅","location":"104.0600,30.6700",
               "address":"宽窄巷子旁","biz_ext":{"rating":"4.2","cost":"80"}}
            ]}""";

    private static final Meal KB_MEAL_LUNCH = new Meal("成都·蜀九香·午", CENTER,
            MealType.LUNCH, new BigDecimal("65"), List.of("麻婆豆腐", "回锅肉"));
    private static final Meal KB_MEAL_DINNER = new Meal("成都·蜀九香·晚", CENTER,
            MealType.DINNER, new BigDecimal("65"), List.of("鸳鸯锅", "毛肚"));

    private static TripPlanRequest request(TravelStyle style) {
        return new TripPlanRequest("成都", START_DATE, 1,
                new BigDecimal("5000"), 2, style, List.of());
    }

    private static Attraction attraction() {
        return new Attraction("A-001", "宽窄巷子", CENTER, 4.5, 120, new BigDecimal("80"), List.of());
    }

    // ── Amap 正常 + 构造校验 ──

    @Nested
    @DisplayName("AmapMealRecommender：URL 构造与数据源验证")
    class AmapUrlAndSource {

        @Test
        @DisplayName("URL 含必要参数：key/location/radius/types/keywords")
        void buildUrl_containsAllParams() {
            AtomicReference<String> captured = new AtomicReference<>();
            PoiHttpExecutor capturing = url -> {
                captured.set(url);
                return AMAP_JSON_LUNCH;
            };
            AmapMealRecommender recommender = new AmapMealRecommender(capturing, "test-ak");
            MealRecommendQuery query = MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH,
                    TravelStyle.FAMILY);

            recommender.recommend(query);
            String url = captured.get();

            assertNotNull(url);
            assertTrue(url.contains("key=test-ak"), "URL 应含 key: " + url);
            assertTrue(url.contains("location=104.0554%2C30.6697") || url.contains("location=104.0554,30.6697"),
                    "URL 应含 location 坐标");
            assertTrue(url.contains("radius=5000"), "URL 应含搜索半径");
            assertTrue(url.contains("types=050000"), "URL 应含餐饮类型");
            assertTrue(url.contains("keywords="), "URL 应含 keywords");
        }

        @Test
        @DisplayName("null style → 无 keywords 参数")
        void nullStyle_noKeywords() {
            AtomicReference<String> captured = new AtomicReference<>();
            PoiHttpExecutor capturing = url -> {
                captured.set(url);
                return AMAP_JSON_LUNCH;
            };
            AmapMealRecommender recommender = new AmapMealRecommender(capturing, "test-ak");
            MealRecommendQuery query = MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH, null);

            recommender.recommend(query);

            assertFalse(captured.get().contains("keywords="),
                    "null style 时不应含 keywords: " + captured.get());
        }

        @Test
        @DisplayName("正常 POI 返回 → Meal 名称/价格/坐标均非空")
        void amapSuccess_mealFieldsComplete() {
            PoiHttpExecutor stub = url -> AMAP_JSON_LUNCH;
            AmapMealRecommender recommender = new AmapMealRecommender(stub, "test-ak");
            MealRecommendQuery query = MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH,
                    TravelStyle.COUPLE);

            List<Meal> meals = recommender.recommend(query);

            assertFalse(meals.isEmpty());
            Meal meal = meals.get(0);
            assertEquals("蜀九香火锅", meal.name());
            assertNotNull(meal.avgPrice());
            assertEquals(0, meal.avgPrice().compareTo(new BigDecimal("80")));
            assertTrue(meal.location().longitude() != 0 || meal.location().latitude() != 0);
        }

        @Test
        @DisplayName("高德不同 style 返回不同 keywords → 不同结果或空")
        void differentStyle_differentKeywords() {
            AtomicReference<String> familyUrl = new AtomicReference<>();
            AtomicReference<String> coupleUrl = new AtomicReference<>();

            AmapMealRecommender familyR = new AmapMealRecommender(url -> {
                familyUrl.set(url);
                return AMAP_JSON_LUNCH;
            }, "test-ak");
            AmapMealRecommender coupleR = new AmapMealRecommender(url -> {
                coupleUrl.set(url);
                return AMAP_JSON_LUNCH;
            }, "test-ak");

            familyR.recommend(MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH, TravelStyle.FAMILY));
            coupleR.recommend(MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH, TravelStyle.COUPLE));

            assertNotEquals(familyUrl.get(), coupleUrl.get(),
                    "不同 TravelStyle 应产生不同 keywords 参数");
        }
    }

    // ── Amap 故障 → KB → FALLBACK 全链路 ──

    @Nested
    @DisplayName("三层降级链路：Amap → KB → FALLBACK（经 ItineraryDesignService）")
    class DegradationChain {

        @Test
        @DisplayName("Amap 正常 + KB 正常 → 走 Amap，fallback=false")
        void amapOk_usesAmap() {
            PoiHttpExecutor amapStub = url -> AMAP_JSON_LUNCH;
            MealRecommender amap = new AmapMealRecommender(amapStub, "test-ak");
            MealRecommender kb = new CuisineKnowledgeBase();
            DegradingMealRecommender degrading = new DegradingMealRecommender(amap, kb);
            ItineraryDesignService service = new ItineraryDesignService(degrading);

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(TravelStyle.COUPLE), List.of(attraction()), null);
            ItineraryDesignResult result = service.design(query);

            assertEquals(1, result.days().size());
            TripDay day = result.days().get(0);
            assertEquals(2, day.meals().size(), "应含午+晚餐");
            // Amap 正常时 meal 来自高德 POI → 名称非兜底
            boolean hasRealName = day.meals().stream()
                    .anyMatch(m -> !m.name().contains("推荐餐厅"));
            assertTrue(hasRealName, "Amap 成功时应使用真实餐厅名");
            assertEquals(0, result.telemetry().mealFallbackCount(), "Amap 成功不应有降级计数");
            assertFalse(result.fallback(), "Amap 正常时不应标记 fallback");
        }

        @Test
        @DisplayName("Amap 返回空 + KB 命中 → 走 KB 结果")
        void amapEmpty_kbUsed() {
            MealRecommender amap = q -> List.of(); // Amap down
            MealRecommender kb = q -> List.of(
                    q.mealType() == MealType.LUNCH ? KB_MEAL_LUNCH : KB_MEAL_DINNER);
            DegradingMealRecommender degrading = new DegradingMealRecommender(amap, kb);
            ItineraryDesignService service = new ItineraryDesignService(degrading);

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(TravelStyle.FAMILY), List.of(attraction()), null);
            ItineraryDesignResult result = service.design(query);

            assertEquals(1, result.days().size());
            TripDay day = result.days().get(0);
            assertFalse(day.meals().isEmpty());
            // KB meals should appear (not the "推荐餐厅" fallback)
            boolean hasKbMeal = day.meals().stream()
                    .anyMatch(m -> m.name().contains("蜀九香"));
            assertTrue(hasKbMeal, "应使用 KB 餐厅名而非兜底餐");
        }

        @Test
        @DisplayName("Amap 抛异常 + KB 命中 → 走 KB，不崩")
        void amapThrows_kbUsed() {
            MealRecommender amap = q -> { throw new RuntimeException("高德 API 超时"); };
            MealRecommender kb = q -> List.of(KB_MEAL_LUNCH);
            DegradingMealRecommender degrading = new DegradingMealRecommender(amap, kb);
            ItineraryDesignService service = new ItineraryDesignService(degrading);

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(TravelStyle.SOLO), List.of(attraction()), null);
            ItineraryDesignResult result = service.design(query);

            assertEquals(1, result.days().size());
            TripDay day = result.days().get(0);
            assertTrue(day.meals().stream().anyMatch(m -> m.name().contains("蜀九香")),
                    "Amap 异常应降级到 KB");
        }

        @Test
        @DisplayName("Amap 空 + KB 空 → 兜底餐 FALLBACK，fallback=true，午晚餐不缺失")
        void bothEmpty_fallbackMeals() {
            MealRecommender empty = q -> List.of();
            DegradingMealRecommender degrading = new DegradingMealRecommender(empty, empty);
            ItineraryDesignService service = new ItineraryDesignService(degrading);

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(TravelStyle.TEAM), List.of(attraction()), null);
            ItineraryDesignResult result = service.design(query);

            assertEquals(1, result.days().size());
            TripDay day = result.days().get(0);

            // 确保午晚餐存在
            boolean hasLunch = day.meals().stream().anyMatch(m -> m.mealType() == MealType.LUNCH);
            boolean hasDinner = day.meals().stream().anyMatch(m -> m.mealType() == MealType.DINNER);
            assertTrue(hasLunch, "兜底仍有午餐");
            assertTrue(hasDinner, "兜底仍有晚餐");

            // 兜底餐名含"推荐餐厅"
            boolean hasFallbackName = day.meals().stream()
                    .anyMatch(m -> m.name().contains("推荐餐厅"));
            assertTrue(hasFallbackName, "兜底餐应含'推荐餐厅'标识");

            assertTrue(result.fallback(), "双空应标记 fallback=true");
            assertEquals(2, result.telemetry().mealFallbackCount(), "2 餐均降级 → 计数=2");
        }

        @Test
        @DisplayName("Amap 空 + KB 空 + 多天 → 每天都有兜底午晚餐，遥测计数正确")
        void multiDay_allFallback_correctCount() {
            MealRecommender empty = q -> List.of();
            DegradingMealRecommender degrading = new DegradingMealRecommender(empty, empty);
            ItineraryDesignService service = new ItineraryDesignService(degrading);

            // 4 个景点，FAMILY 每天最多 3 个 → 至少 2 天（days=3 确保有足够天分配）
            List<Attraction> candidates = List.of(
                    new Attraction("A-001", "景点1", CENTER, 4.5, 120, BigDecimal.TEN, List.of()),
                    new Attraction("A-002", "景点2",
                            new GeoLocation(104.07, 30.68, "景点2", "成都"), 4.5, 120,
                            BigDecimal.TEN, List.of()),
                    new Attraction("A-003", "景点3",
                            new GeoLocation(104.08, 30.69, "景点3", "成都"), 4.5, 120,
                            BigDecimal.TEN, List.of()),
                    new Attraction("A-004", "景点4",
                            new GeoLocation(104.09, 30.70, "景点4", "成都"), 4.5, 120,
                            BigDecimal.TEN, List.of()));

            TripPlanRequest req = new TripPlanRequest("成都", START_DATE, 3,
                    new BigDecimal("5000"), 2, TravelStyle.FAMILY, List.of());
            ItineraryDesignQuery query = new ItineraryDesignQuery(req, candidates, null);
            ItineraryDesignResult result = service.design(query);

            assertTrue(result.days().size() >= 2, "FAMILY 每日≤3 → 4景点需≥2天");
            for (TripDay day : result.days()) {
                boolean lunch = day.meals().stream().anyMatch(m -> m.mealType() == MealType.LUNCH);
                boolean dinner = day.meals().stream().anyMatch(m -> m.mealType() == MealType.DINNER);
                assertTrue(lunch && dinner, "Day " + day.dayIndex() + " 缺餐");
            }
            assertTrue(result.fallback());
            int expected = result.days().size() * 2;
            assertEquals(expected, result.telemetry().mealFallbackCount(),
                    "N天×2餐/N = " + expected + " 次兜底");
        }
    }

    // ── KB 自身降级 ──

    @Nested
    @DisplayName("CuisineKnowledgeBase 自身降级场景")
    class KbDegradation {

        @Test
        @DisplayName("KB 未知城市 → 空列表（触发外层兜底餐）")
        void unknownCity_empty() {
            CuisineKnowledgeBase kb = new CuisineKnowledgeBase();
            MealRecommendQuery query = MealRecommendQuery.of(
                    new GeoLocation(91.0, 29.6, "布达拉宫广场", "拉萨"),
                    "拉萨", MealType.LUNCH, TravelStyle.SOLO);
            List<Meal> meals = kb.recommend(query);

            assertTrue(meals.isEmpty(), "未知城市 KB 应返回空");
        }

        @Test
        @DisplayName("KB classpath 加载后已知城市可查（回归）")
        void knownCities_loadable() {
            CuisineKnowledgeBase kb = new CuisineKnowledgeBase();
            for (String city : List.of("成都", "北京", "上海")) {
                MealRecommendQuery query = MealRecommendQuery.of(
                        CENTER, city, MealType.LUNCH, TravelStyle.FAMILY);
                List<Meal> meals = kb.recommend(query);
                assertFalse(meals.isEmpty(), city + " 应有数据");
                assertNotNull(meals.get(0).name());
                assertNotNull(meals.get(0).recommendDishes());
            }
        }
    }

    // ── DegradingMealRecommender 集成回归 ──

    @Nested
    @DisplayName("DegradingMealRecommender：null 守卫与回归")
    class DegradingRegression {

        @Test
        @DisplayName("primary 返回 null → 降级到 secondary（而非 NPE）")
        void primaryNull_secondaryUsed() {
            MealRecommender primary = q -> null;
            MealRecommender secondary = q -> List.of(KB_MEAL_LUNCH);
            DegradingMealRecommender degrading = new DegradingMealRecommender(primary, secondary);

            MealRecommendQuery query = MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH, null);
            List<Meal> result = degrading.recommend(query);

            assertFalse(result.isEmpty(), "primary null 应降级到 secondary");
            assertEquals("成都·蜀九香·午", result.get(0).name());
        }

        @Test
        @DisplayName("secondary 返回 null → 最终空列表")
        void secondaryNull_empty() {
            MealRecommender primary = q -> List.of();
            MealRecommender secondary = q -> null;
            DegradingMealRecommender degrading = new DegradingMealRecommender(primary, secondary);

            MealRecommendQuery query = MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH, null);
            List<Meal> result = degrading.recommend(query);

            assertTrue(result.isEmpty());
        }
    }
}
