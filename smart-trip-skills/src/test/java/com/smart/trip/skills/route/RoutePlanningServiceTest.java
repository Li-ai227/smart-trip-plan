package com.smart.trip.skills.route;

import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RateLimitRule;
import com.smart.trip.skills.governance.RateLimiter;
import com.smart.trip.skills.governance.RateLimitStore;
import com.smart.trip.skills.governance.RetryPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RoutePlanningService} 核心逻辑测试（C-007 T-2/T-3，TDD Red→Green）。
 *
 * <p>覆盖：Happy Path、超限标记（≤120min 边界）、三类降级、空/单景点、限频短路。</p>
 */
class RoutePlanningServiceTest {

    // ── 测试固件 ──

    private static final GeoLocation LOC_KZX = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");
    private static final GeoLocation LOC_PANDA = new GeoLocation(104.1461, 30.7327, "大熊猫基地", "成都");
    private static final GeoLocation LOC_WHC = new GeoLocation(104.0479, 30.6437, "武侯祠", "成都");

    private static final Attraction KZX = attraction("A-001", "宽窄巷子", LOC_KZX);
    private static final Attraction PANDA = attraction("A-002", "大熊猫基地", LOC_PANDA);
    private static final Attraction WHC = attraction("A-003", "武侯祠", LOC_WHC);

    private static Attraction attraction(String id, String name, GeoLocation loc) {
        return new Attraction(id, name, loc, 4.5, 120, BigDecimal.TEN, List.of());
    }

    private static Attraction attractionNoLocation(String id, String name) {
        return new Attraction(id, name, null, 4.5, 120, BigDecimal.TEN, List.of());
    }

    // ── 桩实现 ──

    /** 直通式 caller：不重试（maxRetries=0）、不限频、不真实等待。 */
    private static final GovernedExternalCaller PASSTHROUGH_CALLER = new GovernedExternalCaller(
            new RetryPolicy(0, 1), millis -> { });

    /** 桩路线端口：返回固定距离和耗时。 */
    private static MapRouter stubRouter(double km, int minutes) {
        return (from, to) -> new Route(from, to, km, minutes, "驾车", false);
    }

    /** 桩路线端口：超限（150min）。 */
    private static final MapRouter OVER_LIMIT_ROUTER = stubRouter(200.0, 150);

    /** 桩路线端口：抛异常。 */
    private static final MapRouter FAILING_ROUTER = (from, to) -> {
        throw new RuntimeException("路线服务不可用");
    };

    /** 桩地理编码端口：抛异常。 */
    private static final MapGeocoder FAILING_GEOCODER = (addr, city) -> {
        throw new RuntimeException("地理编码不可用");
    };

    /** 桩预置坐标：总是找不到。 */
    private static final PresetCoordinateCatalog EMPTY_CATALOG = name -> Optional.empty();

    /** 桩预置坐标：总是返回成都中心坐标。 */
    private static final PresetCoordinateCatalog CHENGDU_CATALOG = name ->
            Optional.of(new GeoLocation(104.0665, 30.5728, name, "成都"));

    /** 空路况端口：不可用。 */
    private static final MapTraffic NO_TRAFFIC = null;

    // ── Happy Path ──

    @Test
    void happyPath_multipleAttractions_producesAdjacentRoutes() {
        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, stubRouter(10.0, 30), NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        var query = RoutePlanQuery.of("成都", List.of(KZX, PANDA, WHC));
        RoutePlanResult result = service.plan(query);

        assertEquals(2, result.routes().size());
        assertFalse(result.fallback());
        assertTrue(result.overLimitRoutes().isEmpty());
        assertTrue(result.unreachableSegments().isEmpty());
    }

    @Test
    void happyPath_routesInInputOrder() {
        MapRouter router = (from, to) -> new Route(from, to,
                HaversineUtils.distanceKm(from, to), 30, "驾车", false);
        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, router, NO_TRAFFIC, EMPTY_CATALOG, PASSTHROUGH_CALLER);

        var query = RoutePlanQuery.of("成都", List.of(KZX, PANDA, WHC));
        RoutePlanResult result = service.plan(query);

        assertEquals(2, result.routes().size());
        // 第一段：KZX → PANDA
        assertSame(LOC_KZX, result.routes().get(0).from());
        assertSame(LOC_PANDA, result.routes().get(0).to());
        // 第二段：PANDA → WHC
        assertSame(LOC_PANDA, result.routes().get(1).from());
        assertSame(LOC_WHC, result.routes().get(1).to());
    }

    // ── 空/单景点边界 ──

    @Test
    void emptyAttractions_returnsEmptyRoutes() {
        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, stubRouter(10.0, 30), NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of()));

        assertTrue(result.routes().isEmpty());
        assertFalse(result.fallback());
        assertNotNull(result.message());
    }

    @Test
    void singleAttraction_returnsEmptyRoutes() {
        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, stubRouter(10.0, 30), NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(KZX)));

        assertTrue(result.routes().isEmpty());
        assertFalse(result.fallback());
    }

    // ── 超限标记（120min 临界） ──

    @Test
    void durationExactly120_notOverLimit() {
        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, stubRouter(100.0, 120), NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(KZX, PANDA)));

        assertEquals(1, result.routes().size());
        assertTrue(result.overLimitRoutes().isEmpty());
    }

    @Test
    void duration121_overLimit_routeRetainedAndMarked() {
        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, stubRouter(200.0, 121), NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(KZX, PANDA)));

        assertEquals(1, result.routes().size());
        assertEquals(1, result.overLimitRoutes().size());
        assertEquals(121, result.overLimitRoutes().get(0).durationMin());
        // 超限路线保留不剔除
        assertEquals(result.routes().get(0), result.overLimitRoutes().get(0));
    }

    @Test
    void duration150_overLimit_routePreservedNotRemoved() {
        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, OVER_LIMIT_ROUTER, NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(KZX, PANDA)));

        assertEquals(1, result.routes().size());
        assertEquals(1, result.overLimitRoutes().size());
    }

    // ── 降级：路线端口故障 → haversine 估算 ──

    @Test
    void routerFailure_fallsBackToHaversine() {
        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, FAILING_ROUTER, NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(KZX, PANDA)));

        assertEquals(1, result.routes().size());
        assertTrue(result.fallback());
        assertTrue(result.routes().get(0).fallback());
        assertTrue(result.routes().get(0).distanceKm() > 0);
    }

    // ── 降级：地理编码失败 → 预置坐标 ──

    @Test
    void geocodeFailure_presetCatalogFallback() {
        Attraction noLoc = attractionNoLocation("A-010", "未知景点");
        var service = new RoutePlanningService(
                FAILING_GEOCODER, stubRouter(10.0, 30), NO_TRAFFIC,
                CHENGDU_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(noLoc, KZX)));

        assertEquals(1, result.routes().size());
        assertTrue(result.fallback());
        assertTrue(result.routes().get(0).fallback());
    }

    @Test
    void geocodeFailure_noPreset_segmentSkipped() {
        Attraction noLoc = attractionNoLocation("A-010", "未知景点");
        var service = new RoutePlanningService(
                FAILING_GEOCODER, stubRouter(10.0, 30), NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(noLoc, KZX)));

        assertTrue(result.routes().isEmpty());
        assertFalse(result.unreachableSegments().isEmpty());
        assertTrue(result.fallback());
    }

    // ── 降级：路况不可用 → 忽略（不标记 fallback） ──

    @Test
    void trafficUnavailable_ignoredNoFallback() {
        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, stubRouter(10.0, 30), NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(KZX, PANDA)));

        assertFalse(result.fallback());
    }

    // ── 坐标相同（distance=0） ──

    @Test
    void sameCoordinates_zeroDistanceRoute() {
        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, stubRouter(0.0, 0), NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        Attraction a1 = attraction("A-001", "A", LOC_KZX);
        Attraction a2 = attraction("A-002", "B", LOC_KZX);
        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(a1, a2)));

        assertEquals(1, result.routes().size());
        assertEquals(0.0, result.routes().get(0).distanceKm());
    }

    // ── C-012-D 修复验证：HaversineRouter + CatalogBasedPresetCoordinateCatalog ──

    @Test
    void haversineRouterWithCatalogBackedPreset_producesNonEmptyRoutes() {
        // HaversineRouter 作为 MapRouter + CatalogBasedPresetCoordinateCatalog 作为坐标降级源
        Attraction kzx = attraction("A-001", "宽窄巷子", LOC_KZX);
        Attraction panda = attraction("A-002", "大熊猫基地", LOC_PANDA);
        Attraction whc = attraction("A-003", "武侯祠", LOC_WHC);

        // 避两地同坐标：KZX→PANDA→WHC 坐标各不相同
        var service = new RoutePlanningService(
                (addr, city) -> { throw new RuntimeException("地理编码不可用"); },
                new HaversineRouter(), NO_TRAFFIC, EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(kzx, panda, whc)));

        // 3 个不同坐标的景点 → 2 段 haversine 估算路线（核心验证：非空 routes）
        assertEquals(2, result.routes().size(), "应产出 2 段 haversine 估算路线");
        assertTrue(result.routes().get(0).distanceKm() > 0, "第 1 段应有正距离");
        assertTrue(result.routes().get(1).distanceKm() > 0, "第 2 段应有正距离");
        assertTrue(result.routes().get(0).fallback(),
                "HaversineRouter 产出的 Route 应自带 fallback=true");
    }

    // ── 坐标非法 → 按坐标缺失走降级链 ──

    @Test
    void invalidCoordinates_treatedAsMissing() {
        Attraction invalid = new Attraction("A-099", "非法坐标",
                new GeoLocation(999.0, 999.0, "x", "x"), 4.5, 60, BigDecimal.TEN, List.of());
        var service = new RoutePlanningService(
                FAILING_GEOCODER, stubRouter(10.0, 30), NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(invalid, KZX)));

        // 非法坐标视为缺失→地理编码失败→无预置→段跳过
        assertTrue(result.fallback());
        assertEquals(1, result.unreachableSegments().size());
        assertTrue(result.routes().isEmpty(),
                "非法坐标的景点无可用坐标，应跳过该段无路线产出");
    }

    @Test
    void invalidCoordinates_geo_181_91_skipped() {
        // 经度 181 > 180, 纬度 91 > 90 → 均应触发 isValidCoordinate 返回 false
        Attraction invalid = new Attraction("A-100", "超界坐标",
                new GeoLocation(181.0, 91.0, "x", "x"), 4.5, 60, BigDecimal.TEN, List.of());
        var service = new RoutePlanningService(
                FAILING_GEOCODER, stubRouter(10.0, 30), NO_TRAFFIC,
                EMPTY_CATALOG, PASSTHROUGH_CALLER);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(invalid, KZX)));

        assertTrue(result.fallback());
        assertEquals(1, result.unreachableSegments().size());
    }

    // ── 限频命中 → haversine 离线估算（AC-6 / Case-6） ──

    @Test
    void rateLimitHit_fallsBackToHaversine() {
        // 构造已耗尽的限频器：store 始终返回 >30，触发 RateLimitedException
        RateLimitStore exhausted = (key, window) -> 31L;
        RateLimiter limiter = new RateLimiter(exhausted,
                Map.of(RateLimiter.API_MAP,
                        new RateLimitRule(RateLimiter.API_MAP, 30, 60)));
        GovernedExternalCaller rateLimitedCaller = new GovernedExternalCaller(
                new RetryPolicy(0, 1), millis -> {}, limiter);

        var service = new RoutePlanningService(
                (addr, city) -> LOC_KZX, stubRouter(10.0, 30), NO_TRAFFIC,
                EMPTY_CATALOG, rateLimitedCaller);

        RoutePlanResult result = service.plan(RoutePlanQuery.of("成都", List.of(KZX, PANDA)));

        // 限频命中→降级 haversine→fallback=true（绝不超频，AC-6）
        assertEquals(1, result.routes().size());
        assertTrue(result.fallback());
        assertTrue(result.routes().get(0).fallback());
        assertTrue(result.routes().get(0).distanceKm() > 0,
                "限频降级应产出 haversine 估算距离");
    }
}
