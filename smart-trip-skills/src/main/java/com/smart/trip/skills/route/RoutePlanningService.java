package com.smart.trip.skills.route;

import com.smart.trip.common.exception.RateLimitedException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.skills.governance.CallResult;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RateLimiter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code route-planning} 业务 Skill 核心（C-007 AC-2~AC-5/AC-7）。
 *
 * <p>按输入景点访问序构造相邻段路线，统一经 {@link GovernedExternalCaller} 治理外部调用。
 * 三类降级（接口协议.md §2.2/§2.3）：</p>
 * <ol>
 *   <li>地理编码失败 → 预置坐标 → 仍无则跳过该段 + 记录不可达</li>
 *   <li>路线规划失败 → haversine 离线估算（{@code fallback=true}）</li>
 *   <li>路况不可用 → 静默忽略，不标记 fallback</li>
 * </ol>
 *
 * <p>超限标记（业务模型.md §4 强规则）：{@code durationMin > 120} 的路线保留在 {@code routes}
 * 中同时加入 {@code overLimitRoutes}，由下游 QualityGate 决策（C-008）。</p>
 */
public final class RoutePlanningService {

    private static final System.Logger LOG =
            System.getLogger(RoutePlanningService.class.getName());

    /**
     * MCP 服务端 QPS 限频保护：连续外部 API 调用（地理编码/路线规划）之间最小间隔。
     * 高德 MCP Server 侧有 CUQPS 限制，客户端 RateLimiter 为分钟级窗口无法防瞬间并发，
     * 此处以固定间隔兜底，约 3 QPS。
     */
    static final long QPS_GAP_MS = 350L;

    private final MapGeocoder geocoder;
    private final MapRouter router;
    private final MapTraffic traffic;
    private final PresetCoordinateCatalog presetCatalog;
    private final GovernedExternalCaller caller;

    /**
     * 构造路线规划 Skill。
     *
     * @param geocoder      地理编码端口
     * @param router        路线规划端口
     * @param traffic       路况端口，可为 null（null 时跳过路况查询）
     * @param presetCatalog 预置坐标目录（地理编码降级源）
     * @param caller        统一外部调用治理执行器
     */
    public RoutePlanningService(MapGeocoder geocoder, MapRouter router, MapTraffic traffic,
                                PresetCoordinateCatalog presetCatalog,
                                GovernedExternalCaller caller) {
        this.geocoder = geocoder;
        this.router = router;
        this.traffic = traffic;
        this.presetCatalog = presetCatalog;
        this.caller = caller;
    }

    /**
     * 按访问序规划相邻段路线。
     * 路线规划主逻辑
     * @param query 规划请求
     * @return 规划结果
     */
    public RoutePlanResult plan(RoutePlanQuery query) {
        List<Attraction> attractions = query.attractions();
        if (attractions.size() < 2) {
            return new RoutePlanResult(List.of(), List.of(), List.of(), false,
                    "景点数不足 2，无需路线规划", null);
        }
        // Phase 1: 坐标解析（已有坐标直接用，无坐标走地理编码→预置目录降级）
        List<ResolvedLocation> resolved = resolveLocations(attractions, query.destination());
        // Phase 2: 逐相邻段规划路线
        List<Route> routes = new ArrayList<>();
        List<Route> overLimit = new ArrayList<>();
        List<String> unreachable = new ArrayList<>();
        boolean anyFallback = false;

        for (int i = 0; i < resolved.size() - 1; i++) {
            ResolvedLocation fromLoc = resolved.get(i);
            ResolvedLocation toLoc = resolved.get(i + 1);

            if (fromLoc.location() == null || toLoc.location() == null) {
                unreachable.add(attractions.get(i).name() + " → " + attractions.get(i + 1).name());
                anyFallback = true;
                continue;
            }

            SegmentResult sr = routeSegment(fromLoc, toLoc, query.transportMode());
            routes.add(sr.route());
            if (sr.fallback()) {
                anyFallback = true;
            }
            if (sr.route().durationMin() > RoutePlanResult.DURATION_LIMIT_MINUTES) { // >120min
                overLimit.add(sr.route());
            }
            sleepQpsGap();  // 350ms QPS 保护间隔
        }

        return new RoutePlanResult(routes, overLimit, unreachable, anyFallback, null, null);
    }

    // 逐段路线规划的治理包装，这是统一的"限频→重试→降级"执行链：
    private SegmentResult routeSegment(ResolvedLocation fromLoc, ResolvedLocation toLoc,
                                       String transportMode) {
        boolean coordFallback = fromLoc.fallback() || toLoc.fallback();
        GeoLocation from = fromLoc.location();
        GeoLocation to = toLoc.location();

        CallResult<Route> routeResult;
        try {
            routeResult = caller.call(
                    RateLimiter.API_MAP, // 限频标识
                    () -> router.route(from, to),  // 实际调用
                    cause -> haversineFallback(from, to, transportMode)); // 降级处理
        } catch (RateLimitedException e) {
            // 限频命中短路降级（AC-6）：该段走 haversine 离线估算并标记 fallback
            LOG.log(System.Logger.Level.WARNING,
                    "限频命中 API_MAP，降级 haversine 估算: from={0}, to={1}",
                    from, to);
            routeResult = new CallResult<>(
                    haversineFallback(from, to, transportMode), true);
        }

        boolean segmentFallback = coordFallback || routeResult.fallback();
        Route route = routeResult.value();
        if (segmentFallback && !route.fallback()) {
            route = new Route(route.from(), route.to(), route.distanceKm(),
                    route.durationMin(), route.transportMode(), true);
        }
        return new SegmentResult(route, segmentFallback);
    }

    private List<ResolvedLocation> resolveLocations(List<Attraction> attractions, String city) {
        List<ResolvedLocation> resolved = new ArrayList<>(attractions.size());
        for (int i = 0; i < attractions.size(); i++) {
            Attraction a = attractions.get(i);
            GeoLocation loc = a.location();
            if (loc != null && isValidCoordinate(loc)) {
                resolved.add(new ResolvedLocation(loc, false));
            } else {
                String address = loc != null ? loc.address() : null;
                resolved.add(resolveByGeocodeOrPreset(a.name(), address, city));
                sleepQpsGap();
            }
        }
        return resolved;
    }

    private ResolvedLocation resolveByGeocodeOrPreset(String name, String address, String city) {
        // 优先用地址做地理编码（LLM 传字符串 location 时 address 比 name 更精确），
        // 地址缺城市前缀时自动补全（如 "武隆区" → "重庆武隆区"）
        String query = buildGeoQuery(name, address, city);
        String searchName = (address != null && !address.isBlank()) ? address : name;
        try {
            CallResult<GeoLocation> result = caller.call(
                    RateLimiter.API_MAP,
                    () -> geocoder.geocode(query, city),
                    cause -> presetCatalog.findByName(searchName)
                            .or(() -> presetCatalog.findByName(name))
                            .orElse(null));
            return new ResolvedLocation(result.value(), result.fallback());
        } catch (Exception e) {
            GeoLocation preset = presetCatalog.findByName(searchName)
                    .or(() -> presetCatalog.findByName(name))
                    .orElse(null);
            return new ResolvedLocation(preset, preset != null);
        }
    }

    private static String buildGeoQuery(String name, String address, String city) {
        if (address != null && !address.isBlank()) {
            if (city != null && !city.isBlank() && !address.startsWith(city)) {
                return city + address;
            }
            return address;
        }
        return name;
    }

    private static Route haversineFallback(GeoLocation from, GeoLocation to, String transportMode) {
        double km = HaversineUtils.distanceKm(from, to);
        int minutes = HaversineUtils.estimateDurationMin(from, to);
        return new Route(from, to, km, minutes, transportMode, true);
    }

    private static void sleepQpsGap() {
        try {
            Thread.sleep(QPS_GAP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isValidCoordinate(GeoLocation loc) {
        // 排除哨兵值 (0,0)：LLM 将 location 传为字符串时，
        // AttractionJsonParser.normalizeLocationStrings 转为 GeoLocation(0,0)，必须走地理编码
        if (loc.longitude() == 0.0 && loc.latitude() == 0.0) {
            return false;
        }
        return loc.longitude() >= -180 && loc.longitude() <= 180
                && loc.latitude() >= -90 && loc.latitude() <= 90;
    }

    private record ResolvedLocation(GeoLocation location, boolean fallback) {
    }

    private record SegmentResult(Route route, boolean fallback) {
    }
}
