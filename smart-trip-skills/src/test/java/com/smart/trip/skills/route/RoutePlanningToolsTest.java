package com.smart.trip.skills.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RetryPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RoutePlanningTools} 确定性单测（C-007 增量 C / AC-11）：验证 {@code @Tool plan_routes}
 * 的产物与直接调用 {@link RoutePlanningService} 完全一致（权威结果来自确定性核心，不依赖 LLM），
 * 且 {@link RoutePlanningTools#lastResult()} 正确捕获。
 *
 * <p>不涉及任何 LLM/HarnessAgent——本层只锁工具门面的确定性正确性。</p>
 */
class RoutePlanningToolsTest {

    private static final GeoLocation LOC_KZX = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");
    private static final GeoLocation LOC_PANDA = new GeoLocation(104.1461, 30.7327, "大熊猫基地", "成都");
    private static final GeoLocation LOC_WHC = new GeoLocation(104.0479, 30.6437, "武侯祠", "成都");

    private static final Attraction KZX = attraction("A-001", "宽窄巷子", LOC_KZX);
    private static final Attraction PANDA = attraction("A-002", "大熊猫基地", LOC_PANDA);
    private static final Attraction WHC = attraction("A-003", "武侯祠", LOC_WHC);

    private static Attraction attraction(String id, String name, GeoLocation loc) {
        return new Attraction(id, name, loc, 4.5, 120, BigDecimal.TEN, List.of());
    }

    private static final GovernedExternalCaller CALLER = new GovernedExternalCaller(
            new RetryPolicy(0, 1), millis -> { });

    private RoutePlanningService buildService() {
        MapRouter router = (from, to) -> new Route(from, to, 10.0, 30, "驾车", false);
        return new RoutePlanningService(
                (addr, city) -> LOC_KZX, router, null, name -> Optional.empty(), CALLER);
    }

    @Test
    @DisplayName("构造器拒绝 null service")
    void rejectsNullService() {
        assertThrows(IllegalArgumentException.class, () -> new RoutePlanningTools(null));
    }

    @Test
    @DisplayName("lastResult 初始为 null")
    void lastResultInitiallyNull() {
        RoutePlanningTools tools = new RoutePlanningTools(buildService());
        assertNull(tools.lastResult());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Happy Path：工具产物 == 确定性 Service 产物，lastResult 一致，fallback=false")
    void toolOutputMatchesDeterministicService() {
        RoutePlanningService service = buildService();
        RoutePlanningTools tools = new RoutePlanningTools(service);

        String attractionsJson = JsonUtils.toJson(List.of(KZX, PANDA, WHC));
        String json = tools.planRoutes("成都", attractionsJson, "驾车");

        // 解析 JSON 验证结构
        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
        List<?> routes = (List<?>) payload.get("routes");
        assertEquals(2, routes.size());
        assertEquals(Boolean.FALSE, payload.get("fallback"));

        // 权威产物：lastResult == 确定性 Service 直调产物
        RoutePlanResult last = tools.lastResult();
        assertNotNull(last);
        RoutePlanResult direct = service.plan(RoutePlanQuery.of("成都", List.of(KZX, PANDA, WHC)));
        assertEquals(direct.routes().size(), last.routes().size());
        assertEquals(direct.fallback(), last.fallback());
        assertEquals(direct.overLimitRoutes().size(), last.overLimitRoutes().size());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("超限标记透传：durationMin>120 的路线进入 overLimitRoutes")
    void overLimitRoutesPassedThrough() {
        MapRouter overLimitRouter = (from, to) -> new Route(from, to, 200.0, 150, "驾车", false);
        RoutePlanningService service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, overLimitRouter, null, name -> Optional.empty(), CALLER);
        RoutePlanningTools tools = new RoutePlanningTools(service);

        String attractionsJson = JsonUtils.toJson(List.of(KZX, PANDA));
        tools.planRoutes("成都", attractionsJson, null);

        RoutePlanResult last = tools.lastResult();
        assertNotNull(last);
        assertFalse(last.overLimitRoutes().isEmpty());
        assertEquals(150, last.overLimitRoutes().get(0).durationMin());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("降级透传：路线端口故障 → fallback=true（由底层 Service 保证）")
    void fallbackPassedThrough() {
        MapRouter failing = (from, to) -> { throw new RuntimeException("不可用"); };
        RoutePlanningService service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, failing, null, name -> Optional.empty(), CALLER);
        RoutePlanningTools tools = new RoutePlanningTools(service);

        String attractionsJson = JsonUtils.toJson(List.of(KZX, PANDA));
        String json = tools.planRoutes("成都", attractionsJson, null);

        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
        assertEquals(Boolean.TRUE, payload.get("fallback"));
        assertTrue(tools.lastResult().fallback());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("空/单景点：返回空路线 + 可读说明，不报错")
    void emptyAttractionsReturnsMessage() {
        RoutePlanningTools tools = new RoutePlanningTools(buildService());

        String json = tools.planRoutes("成都", "[]", null);

        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
        assertTrue(((List<?>) payload.get("routes")).isEmpty());
        assertNotNull(payload.get("message"));
        assertNotNull(tools.lastResult());
    }

    @Test
    @DisplayName("transportMode 为 null/空白时走缺省驾车")
    void defaultTransportMode() {
        RoutePlanningTools tools = new RoutePlanningTools(buildService());

        String attractionsJson = JsonUtils.toJson(List.of(KZX, PANDA));
        tools.planRoutes("成都", attractionsJson, null);

        RoutePlanResult last = tools.lastResult();
        assertNotNull(last);
        assertFalse(last.routes().isEmpty());
    }

    @Nested
    @DisplayName("景点 JSON 解析健壮性（AttractionJsonParser）")
    class ParseAttractionsRobustness {

        @Test
        @DisplayName("非法 JSON 格式不抛异常，lastResult 非 null（含 fallback=false）")
        void malformedAttractionsJsonDoesNotThrow() {
            RoutePlanningTools tools = new RoutePlanningTools(buildService());
            // 传入纯字符串（非数组），期望 parser 静默回退空列表，工具不抛异常
            String json = tools.planRoutes("成都", "\"非法格式\"", null);
            assertNotNull(json);
            assertNotNull(tools.lastResult());
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("candidates 包装格式 {\"candidates\":[...]} 正确提取景点，routes 非空")
        void candidatesWrappedObjectExtractsAttractions() {
            RoutePlanningTools tools = new RoutePlanningTools(buildService());
            // xhs-analyst 原始输出格式：candidates 包装对象
            String candidatesJson = "{\"candidates\":["
                    + JsonUtils.toJson(KZX) + ","
                    + JsonUtils.toJson(PANDA)
                    + "],\"fallback\":false}";
            String json = tools.planRoutes("成都", candidatesJson, "驾车");
            Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
            assertFalse(((List<?>) payload.get("routes")).isEmpty(),
                    "candidates 包装格式应正确解析为景点并产出路线");
            assertEquals(Boolean.FALSE, payload.get("fallback"));
        }
    }
}
