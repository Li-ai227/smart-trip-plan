package com.smart.trip.skills.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.xhs.AttractionCatalog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ItineraryDesignTools} 确定性单测（C-008 增量 B / AC-8）：验证 {@code @Tool design_itinerary}
 * 的产物与直接调用 {@link ItineraryDesignService} 完全一致（权威结果来自确定性核心，不依赖 LLM），
 * 且 {@link ItineraryDesignTools#lastResult()} 正确捕获。
 *
 * <p>不涉及任何 LLM/HarnessAgent——本层只锁工具门面的确定性正确性。</p>
 */
class ItineraryDesignToolsTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 7, 1);
    private static final GeoLocation LOC_KZX = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");
    private static final GeoLocation LOC_PANDA = new GeoLocation(104.1461, 30.7327, "大熊猫基地", "成都");
    private static final GeoLocation LOC_WHC = new GeoLocation(104.0479, 30.6437, "武侯祠", "成都");

    private static final Attraction KZX = attraction("A-001", "宽窄巷子", LOC_KZX);
    private static final Attraction PANDA = attraction("A-002", "大熊猫基地", LOC_PANDA);
    private static final Attraction WHC = attraction("A-003", "武侯祠", LOC_WHC);

    private static Attraction attraction(String id, String name, GeoLocation loc) {
        return new Attraction(id, name, loc, 4.5, 120, BigDecimal.TEN, List.of());
    }

    private static final MealRecommender STUB_MEAL = new StubMealRecommender();

    private ItineraryDesignService buildService() {
        return new ItineraryDesignService(STUB_MEAL);
    }

    private static TripPlanRequest request(int days) {
        return new TripPlanRequest("成都", START_DATE, days,
                new BigDecimal("10000"), 2, TravelStyle.COUPLE, List.of("美食"));
    }

    @Test
    @DisplayName("构造器拒绝 null service")
    void rejectsNullService() {
        assertThrows(IllegalArgumentException.class, () -> new ItineraryDesignTools(null));
    }

    @Test
    @DisplayName("lastResult 初始为 null")
    void lastResultInitiallyNull() {
        ItineraryDesignTools tools = new ItineraryDesignTools(buildService());
        assertNull(tools.lastResult());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Happy Path：工具产物 == 确定性 Service 产物，lastResult 一致，fallback=false")
    void toolOutputMatchesDeterministicService() {
        ItineraryDesignService service = buildService();
        ItineraryDesignTools tools = new ItineraryDesignTools(service);

        String requestJson = JsonUtils.toJson(request(2));
        String attractionsJson = JsonUtils.toJson(List.of(KZX, PANDA, WHC));
        String json = tools.designItinerary(requestJson, attractionsJson, null);

        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
        List<?> days = (List<?>) payload.get("days");
        assertFalse(days.isEmpty());
        assertEquals(Boolean.FALSE, payload.get("fallback"));

        ItineraryDesignResult last = tools.lastResult();
        assertNotNull(last);
        ItineraryDesignResult direct = service.design(
                new ItineraryDesignQuery(request(2), List.of(KZX, PANDA, WHC), null));
        assertEquals(direct.days().size(), last.days().size());
        assertEquals(direct.fallback(), last.fallback());
        assertEquals(direct.risks().size(), last.risks().size());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("降级透传：餐饮全空 → fallback=true（由底层 Service 保证）")
    void fallbackPassedThrough() {
        MealRecommender emptyMeal = query -> List.of();
        ItineraryDesignService service = new ItineraryDesignService(emptyMeal);
        ItineraryDesignTools tools = new ItineraryDesignTools(service);

        String requestJson = JsonUtils.toJson(request(1));
        String attractionsJson = JsonUtils.toJson(List.of(KZX));
        String json = tools.designItinerary(requestJson, attractionsJson, null);

        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
        assertEquals(Boolean.TRUE, payload.get("fallback"));
        assertTrue(tools.lastResult().fallback());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("空/无景点：返回空 days + 风险标记，不报错")
    void emptyAttractionsReturnsDaysMismatch() {
        ItineraryDesignTools tools = new ItineraryDesignTools(buildService());

        String requestJson = JsonUtils.toJson(request(3));
        String json = tools.designItinerary(requestJson, "[]", null);

        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
        assertTrue(((List<?>) payload.get("days")).isEmpty());
        assertFalse(((List<?>) payload.get("risks")).isEmpty());
        assertNotNull(tools.lastResult());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("风险清单透传：低评分候选被剔除后风险记录传入结果")
    void risksPassedThrough() {
        ItineraryDesignTools tools = new ItineraryDesignTools(buildService());

        Attraction lowRating = new Attraction("A-LOW", "低分景点", LOC_KZX,
                2.0, 60, BigDecimal.TEN, List.of());

        String requestJson = JsonUtils.toJson(request(1));
        String attractionsJson = JsonUtils.toJson(List.of(KZX, lowRating));
        tools.designItinerary(requestJson, attractionsJson, null);

        ItineraryDesignResult last = tools.lastResult();
        assertNotNull(last);
        assertTrue(last.risks().stream()
                .anyMatch(r -> ItineraryRisk.LOW_RATING_REMOVED.equals(r.code())));
    }

    @Test
    @DisplayName("连续调用覆盖 lastResult")
    void consecutiveCallsOverrideLastResult() {
        ItineraryDesignTools tools = new ItineraryDesignTools(buildService());

        String requestJson = JsonUtils.toJson(request(1));
        tools.designItinerary(requestJson, JsonUtils.toJson(List.of(KZX)), null);
        ItineraryDesignResult first = tools.lastResult();

        tools.designItinerary(requestJson, JsonUtils.toJson(List.of(KZX, PANDA)), null);
        ItineraryDesignResult second = tools.lastResult();

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(second.days().get(0).attractions().size()
                >= first.days().get(0).attractions().size());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("JSON 输出包含 days/risks/fallback/message 四个顶级字段")
    void jsonOutputHasExpectedTopLevelFields() {
        ItineraryDesignTools tools = new ItineraryDesignTools(buildService());

        String requestJson = JsonUtils.toJson(request(1));
        String json = tools.designItinerary(requestJson, JsonUtils.toJson(List.of(KZX)), null);

        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
        assertTrue(payload.containsKey("days"));
        assertTrue(payload.containsKey("risks"));
        assertTrue(payload.containsKey("fallback"));
        assertTrue(payload.containsKey("message"));
    }

    @Nested
    @DisplayName("坐标补全（enrichFromCatalog）")
    class CatalogEnrichment {

        private static Attraction noLoc(String id, String name) {
            return new Attraction(id, name, null, 4.5, 120, BigDecimal.TEN, List.of());
        }

        private AttractionCatalog stubCatalog() {
            return new AttractionCatalog() {
                @Override
                public Optional<Attraction> findById(String attractionId) {
                    if ("A-001".equals(attractionId)) { return Optional.of(KZX); }
                    if ("A-002".equals(attractionId)) { return Optional.of(PANDA); }
                    if ("A-003".equals(attractionId)) { return Optional.of(WHC); }
                    return Optional.empty();
                }
                @Override
                public Optional<Attraction> findByName(String name) {
                    if ("宽窄巷子".equals(name)) { return Optional.of(KZX); }
                    return Optional.empty();
                }
            };
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("有 catalog：无坐标景点按 attractionId 补全后产出非空 days")
        void enrichByIdProducesNonEmptyDays() {
            ItineraryDesignTools tools = new ItineraryDesignTools(buildService(), stubCatalog());
            String requestJson = JsonUtils.toJson(request(2));
            String attractionsJson = JsonUtils.toJson(List.of(
                    noLoc("A-001", "宽窄巷子"),
                    noLoc("A-002", "大熊猫基地"),
                    noLoc("A-003", "武侯祠")));
            String json = tools.designItinerary(requestJson, attractionsJson, null);
            Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
            assertFalse(((List<?>) payload.get("days")).isEmpty(),
                    "catalog 按 id 补全坐标后应产出非空 days");
            assertEquals(Boolean.FALSE, payload.get("fallback"));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("无 catalog（单参数构造器）：无坐标景点不补全，days 为空")
        void noCatalogNoEnrichmentEmptyDays() {
            ItineraryDesignTools tools = new ItineraryDesignTools(buildService());
            String requestJson = JsonUtils.toJson(request(1));
            String attractionsJson = JsonUtils.toJson(List.of(noLoc("A-001", "宽窄巷子")));
            String json = tools.designItinerary(requestJson, attractionsJson, null);
            Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
            assertTrue(((List<?>) payload.get("days")).isEmpty(),
                    "无 catalog 时无坐标景点无法排入 days");
        }

        @Test
        @DisplayName("已有坐标的景点不被 catalog 覆盖（幂等性）")
        void attractionWithLocationIsNotOverwrittenByCatalog() {
            ItineraryDesignTools tools = new ItineraryDesignTools(buildService(), stubCatalog());
            String requestJson = JsonUtils.toJson(request(1));
            // KZX 已有坐标 → enrichFromCatalog 直接返回原对象
            String attractionsJson = JsonUtils.toJson(List.of(KZX));
            tools.designItinerary(requestJson, attractionsJson, null);
            assertNotNull(tools.lastResult());
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("catalog 按名称查找补全（name 匹配）")
        void enrichByNameWhenIdMissing() {
            ItineraryDesignTools tools = new ItineraryDesignTools(buildService(), stubCatalog());
            String requestJson = JsonUtils.toJson(request(1));
            // 无 attractionId，仅 name → 走 findByName
            String attractionsJson = JsonUtils.toJson(List.of(noLoc(null, "宽窄巷子")));
            String json = tools.designItinerary(requestJson, attractionsJson, null);
            Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
            // catalog 按名找到 KZX（有坐标），service 可排入 days
            assertFalse(((List<?>) payload.get("days")).isEmpty(),
                    "catalog 按 name 补全坐标后应产出非空 days");
        }
    }
}
