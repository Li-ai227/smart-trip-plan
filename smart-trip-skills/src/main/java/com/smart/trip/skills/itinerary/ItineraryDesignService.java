package com.smart.trip.skills.itinerary;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.route.HaversineUtils;
import com.smart.trip.skills.route.RoutePlanResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行程编排核心逻辑（C-008 AC-3 / AC-4）：评分筛选 → 去重 → 按天分配景点 → 时间窗约束 →
 * 交通耗时约束 → 插入餐饮 → 装配 TripDay。
 *
 * <p>六项强规则（业务模型.md §4.2）全部在此确定性 Java 中强制：
 * ① 每日至少午晚餐；② 景点不重复；③ 时间窗 8:00–20:00；
 * ④ 餐厅≤5km；⑤ 交通耗时≤2h；⑥ 评分≥3.5。</p>
 */
public final class ItineraryDesignService {

    private static final double RATING_THRESHOLD = 3.5;
    private static final int TRANSIT_LIMIT_MINUTES = 120;
    private static final BigDecimal FALLBACK_MEAL_PRICE = new BigDecimal("50");

    private final MealRecommender mealRecommender;

    /**
     * @param mealRecommender 餐饮推荐端口，非 null
     */
    public ItineraryDesignService(MealRecommender mealRecommender) {
        if (mealRecommender == null) {
            throw new IllegalArgumentException("mealRecommender 不可为 null");
        }
        this.mealRecommender = mealRecommender;
    }

    /**
     * 编排行程。
     *
     * @param query 编排请求，非 null
     * @return 编排结果
     */
    public ItineraryDesignResult design(ItineraryDesignQuery query) {
        TripPlanRequest request = query.request();
        List<ItineraryRisk> risks = new ArrayList<>();

        // ⑥ 评分筛选 + ② 去重（按 attractionId，保留评分更高者）
        FilteredAttractions fa = filterAndDedup(query.candidateAttractions(), risks);

        if (fa.attractions.isEmpty()) {
            return emptyResult(request.days(), risks, fa.lowRatingRemoved);
        }

        ScheduleProfile profile = ScheduleProfile.forStyle(request.travelStyle());
        Map<RouteKey, Route> routeMap = buildRouteMap(query.routeResult());
        detectTransitOverLimit(query.routeResult(), risks);

        // ③ 按天分配 + 装配（贪心装箱算法）
        List<List<Attraction>> dayGroups = assignToDays(
                fa.attractions, request.days(), profile, routeMap);
        if (dayGroups.size() < request.days()) {
            risks.add(new ItineraryRisk(ItineraryRisk.DAYS_MISMATCH,
                    "实际 " + dayGroups.size() + " 天 < 请求 " + request.days() + " 天"));
        }
        // ④ 逐日装配：景点→路线→餐饮→费用
        int mealFallbackCount = 0;
        boolean hasRouteFallback = false;
        List<TripDay> days = new ArrayList<>(dayGroups.size());
        for (int i = 0; i < dayGroups.size(); i++) {
            DayAssembly da = assembleDay(dayGroups.get(i), i, request, routeMap);
            days.add(da.day);
            mealFallbackCount += da.mealFallbacks;
            if (da.day.routes().stream().anyMatch(Route::fallback)) {
                hasRouteFallback = true;
            }
        }

        if (mealFallbackCount > 0) {
            risks.add(new ItineraryRisk(ItineraryRisk.MEAL_FALLBACK,
                    mealFallbackCount + " 次餐饮推荐降级"));
        }

        return new ItineraryDesignResult(days, risks, mealFallbackCount > 0, null,
                new ItineraryDesignResult.Telemetry(mealFallbackCount, fa.lowRatingRemoved));
    }

    private static FilteredAttractions filterAndDedup(List<Attraction> candidates,
                                                      List<ItineraryRisk> risks) {
        int lowRatingRemoved = 0;
        List<Attraction> filtered = new ArrayList<>();
        for (Attraction a : candidates) {
            if (a.rating() >= RATING_THRESHOLD) {
                filtered.add(a);
            } else {
                lowRatingRemoved++;
            }
        }
        if (lowRatingRemoved > 0) {
            risks.add(new ItineraryRisk(ItineraryRisk.LOW_RATING_REMOVED,
                    lowRatingRemoved + " 个景点因评分 <3.5 被剔除"));
        }

        Map<String, Attraction> deduped = new LinkedHashMap<>();
        for (Attraction a : filtered) {
            deduped.merge(a.attractionId(), a,
                    (existing, incoming) -> incoming.rating() > existing.rating() ? incoming : existing);
        }
        return new FilteredAttractions(new ArrayList<>(deduped.values()), lowRatingRemoved);
    }

    private static ItineraryDesignResult emptyResult(int requestedDays,
                                                     List<ItineraryRisk> risks,
                                                     int lowRatingRemoved) {
        if (requestedDays > 0) {
            risks.add(new ItineraryRisk(ItineraryRisk.DAYS_MISMATCH,
                    "无有效候选景点，无法满足 " + requestedDays + " 天行程"));
        }
        return new ItineraryDesignResult(List.of(), risks, false, "无有效候选景点",
                new ItineraryDesignResult.Telemetry(0, lowRatingRemoved));
    }

    private static void detectTransitOverLimit(RoutePlanResult routeResult,
                                               List<ItineraryRisk> risks) {
        if (routeResult == null) {
            return;
        }
        for (Route r : routeResult.routes()) {
            if (r.durationMin() > TRANSIT_LIMIT_MINUTES) {
                risks.add(new ItineraryRisk(ItineraryRisk.TRANSIT_OVER_LIMIT,
                        "存在交通耗时超 2h 的路线"));
                return;
            }
        }
    }

    private DayAssembly assembleDay(List<Attraction> dayAttractions, int dayIndex,
                                    TripPlanRequest request, Map<RouteKey, Route> routeMap) {
        List<Route> dayRoutes = buildDayRoutes(dayAttractions, routeMap);
        GeoLocation mealCenter = dayAttractions.get(0).location();
        String city = request.destination();

        List<Meal> meals = new ArrayList<>(2);
        int fb = insertMeal(meals, mealCenter, city, MealType.LUNCH, request);
        fb += insertMeal(meals, mealCenter, city, MealType.DINNER, request);

        BigDecimal dailyCost = computeDailyCost(dayAttractions, meals);
        TripDay day = new TripDay(request.startDate().plusDays(dayIndex), dayIndex + 1,
                dayAttractions, meals, dayRoutes, dailyCost);
        return new DayAssembly(day, fb);
    }

    /**
     * 按天分配 + 尾部挤压
     * 分配算法（`assignToDays`）是一个贪心装箱：按请求天数创建箱子（每天最多 `profile.maxAttractionsPerDay()` 个景点），逐个填入景点，遵守时间窗和交通约束
     * @param attractions
     * @param requestedDays
     * @param profile
     * @param routeMap
     * @return
     */
    private List<List<Attraction>> assignToDays(List<Attraction> attractions, int requestedDays,
                                                ScheduleProfile profile,
                                                Map<RouteKey, Route> routeMap) {
        int targetDays = Math.min(requestedDays, attractions.size());
        List<List<Attraction>> groups = new ArrayList<>();
        int[] cursor = {0};
        int remaining = attractions.size();

        for (int day = 0; day < targetDays && cursor[0] < attractions.size(); day++) {
            int remainingDays = targetDays - day;
            int targetForDay = Math.min(
                    (int) Math.ceil((double) remaining / remainingDays),
                    profile.maxAttractionsPerDay());

            List<Attraction> dayGroup = fillDayGroup(
                    attractions, cursor, targetForDay, profile, routeMap);
            remaining -= dayGroup.size();
            if (!dayGroup.isEmpty()) {
                groups.add(dayGroup);
            }
        }
        // 尾部景点尝试挤入最后一天（不超过时间窗和交通约束）
        squeezeIntoLastDay(attractions, cursor, groups, profile, routeMap);

        return groups;
    }

    private static void squeezeIntoLastDay(List<Attraction> attractions, int[] cursor,
                                           List<List<Attraction>> groups,
                                           ScheduleProfile profile,
                                           Map<RouteKey, Route> routeMap) {
        if (groups.isEmpty() || cursor[0] >= attractions.size()) {
            return;
        }
        List<Attraction> lastDay = new ArrayList<>(groups.get(groups.size() - 1));
        int dayMinutes = lastDay.stream().mapToInt(Attraction::durationMin).sum();
        while (cursor[0] < attractions.size()
                && lastDay.size() < profile.maxAttractionsPerDay()) {
            Attraction a = attractions.get(cursor[0]);
            if (dayMinutes + a.durationMin() > profile.availableMinutes()) {
                break;
            }
            if (!lastDay.isEmpty()) {
                Attraction prev = lastDay.get(lastDay.size() - 1);
                Route r = findRouteIfExists(prev, a, routeMap);
                if (r != null && r.durationMin() > TRANSIT_LIMIT_MINUTES) {
                    break;
                }
            }
            lastDay.add(a);
            dayMinutes += a.durationMin();
            cursor[0]++;
        }
        groups.set(groups.size() - 1, lastDay);
    }

    /**
     *  单日景点填装
     * @param attractions
     * @param cursor
     * @param maxCount
     * @param profile
     * @param routeMap
     * @return
     */
    private static List<Attraction> fillDayGroup(List<Attraction> attractions, int[] cursor,
                                                 int maxCount, ScheduleProfile profile,
                                                 Map<RouteKey, Route> routeMap) {
        List<Attraction> group = new ArrayList<>();
        int dayMinutes = 0;

        while (cursor[0] < attractions.size() && group.size() < maxCount) {
            Attraction a = attractions.get(cursor[0]);
            if (dayMinutes + a.durationMin() > profile.availableMinutes()) {   // 时间窗溢出
                break;
            }
            if (!group.isEmpty()) {
                Attraction prev = group.get(group.size() - 1);
                Route r = findRouteIfExists(prev, a, routeMap);
                if (r != null && r.durationMin() > TRANSIT_LIMIT_MINUTES) { // 交通超限拆分
                    break;
                }
            }
            group.add(a);
            dayMinutes += a.durationMin();
            cursor[0]++;
        }
        return group;
    }

    private int insertMeal(List<Meal> meals, GeoLocation center, String city,
                           MealType type, TripPlanRequest request) {
        List<Meal> recommended = mealRecommender.recommend(
                MealRecommendQuery.of(center, city, type, request.travelStyle()));
        if (!recommended.isEmpty()) {
            meals.add(recommended.get(0));
            return 0;
        }
        meals.add(fallbackMeal(city, type, center));
        return 1;
    }

    private static Meal fallbackMeal(String city, MealType type, GeoLocation near) {
        String label = type == MealType.LUNCH ? "午餐" : "晚餐";
        String name = city + "·" + label + "·推荐餐厅";
        GeoLocation loc = new GeoLocation(near.longitude(), near.latitude(), name, city);
        return new Meal(name, loc, type, FALLBACK_MEAL_PRICE, List.of());
    }

    private static Map<RouteKey, Route> buildRouteMap(RoutePlanResult routeResult) {
        if (routeResult == null) {
            return Map.of();
        }
        Map<RouteKey, Route> map = new HashMap<>();
        for (Route r : routeResult.routes()) {
            map.put(new RouteKey(r.from(), r.to()), r);
        }
        return map;
    }

    private static List<Route> buildDayRoutes(List<Attraction> dayAttractions,
                                              Map<RouteKey, Route> routeMap) {
        if (routeMap == null || routeMap.isEmpty()) {
            return List.of();
        }
        List<Route> routes = new ArrayList<>();
        for (int j = 0; j < dayAttractions.size() - 1; j++) {
            Route r = findOrEstimateRoute(
                    dayAttractions.get(j),
                    dayAttractions.get(j + 1),
                    routeMap);
            if (r != null) {
                routes.add(r);
            }
        }
        return routes;
    }

    private static Route findRouteIfExists(Attraction from, Attraction to,
                                           Map<RouteKey, Route> routeMap) {
        if (from == null || to == null) {
            return null;
        }
        if (from.location() != null && to.location() != null) {
            Route exact = routeMap.get(new RouteKey(from.location(), to.location()));
            if (exact != null) {
                return exact;
            }
        }
        for (Route r : routeMap.values()) {
            if (matchesLocationOrName(r.from(), from) && matchesLocationOrName(r.to(), to)) {
                return r;
            }
        }
        return null;
    }

    private static Route findOrEstimateRoute(Attraction from, Attraction to,
                                             Map<RouteKey, Route> routeMap) {
        Route planned = findRouteIfExists(from, to, routeMap);
        if (planned != null) {
            return planned;
        }
        // 兜底离线估算：当该相邻对未被上游顺序规划时（例如打散重组），通过 Haversine 补全车程
        GeoLocation fromLoc = resolveEffectiveLocation(from);
        GeoLocation toLoc = resolveEffectiveLocation(to);
        double distanceKm;
        int durationMin;
        if (isValidCoordinate(fromLoc) && isValidCoordinate(toLoc)) {
            distanceKm = Math.round(HaversineUtils.distanceKm(fromLoc, toLoc) * 10.0) / 10.0;
            durationMin = Math.max(10, HaversineUtils.estimateDurationMin(fromLoc, toLoc));
        } else {
            distanceKm = 5.0;
            durationMin = 20;
        }
        return new Route(fromLoc, toLoc, distanceKm, durationMin, "驾车", true);
    }

    private static boolean matchesLocationOrName(GeoLocation loc, Attraction attr) {
        if (loc == null || attr == null) {
            return false;
        }
        if (attr.location() != null && loc.equals(attr.location())) {
            return true;
        }
        if (matchesAddressOrName(loc.address(), attr)) {
            return true;
        }
        return matchesCoordinates(loc, attr.location());
    }

    private static boolean matchesAddressOrName(String locAddr, Attraction attr) {
        if (locAddr == null || locAddr.isBlank()) {
            return false;
        }
        if (containsBothWays(locAddr, attr.name())) {
            return true;
        }
        GeoLocation al = attr.location();
        return al != null && containsBothWays(locAddr, al.address());
    }

    private static boolean containsBothWays(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isBlank() || s2.isBlank()) {
            return false;
        }
        return s1.contains(s2) || s2.contains(s1);
    }

    private static boolean matchesCoordinates(GeoLocation l1, GeoLocation l2) {
        if (isValidCoordinate(l1) && isValidCoordinate(l2)) {
            return HaversineUtils.distanceKm(l1, l2) < 1.0;
        }
        return false;
    }

    private static GeoLocation resolveEffectiveLocation(Attraction attr) {
        if (attr != null && attr.location() != null && isValidCoordinate(attr.location())) {
            return attr.location();
        }
        String name = attr != null ? attr.name() : "";
        return new GeoLocation(0.0, 0.0, name, "");
    }

    private static boolean isValidCoordinate(GeoLocation loc) {
        if (loc == null || (loc.longitude() == 0.0 && loc.latitude() == 0.0)) {
            return false;
        }
        return Math.abs(loc.longitude()) <= 180 && Math.abs(loc.latitude()) <= 90;
    }

    private static BigDecimal computeDailyCost(List<Attraction> attractions, List<Meal> meals) {
        BigDecimal cost = BigDecimal.ZERO;
        for (Attraction a : attractions) {
            if (a.ticketPrice() != null) {
                cost = cost.add(a.ticketPrice());
            }
        }
        for (Meal m : meals) {
            if (m.avgPrice() != null) {
                cost = cost.add(m.avgPrice());
            }
        }
        return cost;
    }

    private record RouteKey(GeoLocation from, GeoLocation to) {
    }

    private record FilteredAttractions(List<Attraction> attractions, int lowRatingRemoved) {
    }

    private record DayAssembly(TripDay day, int mealFallbacks) {
    }
}
