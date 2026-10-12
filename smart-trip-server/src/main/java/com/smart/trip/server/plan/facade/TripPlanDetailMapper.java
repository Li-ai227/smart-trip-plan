package com.smart.trip.server.plan.facade;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Java domain {@link TripPlan} 转换为前端可消费的 Map 结构。
 * 字段名对齐前端 {@code TripPlanDetail.plan → TripPlan → TripDay[]}。
 *
 * <p>本类为纯函数转换，不依赖外部服务；提取到此以控制 {@link TripPlanFacade} 文件长度。</p>
 */
final class TripPlanDetailMapper {

    private TripPlanDetailMapper() {
        // 工具类
    }

    static Map<String, Object> toFrontendPlanMap(TripPlan plan) {
        Map<String, Object> frontendPlan = new LinkedHashMap<>();
        List<Map<String, Object>> frontendDays = new ArrayList<>();
        for (TripDay day : plan.days()) {
            frontendDays.add(toFrontendDayMap(day, plan.request().destination()));
        }
        frontendPlan.put("days", frontendDays);
        frontendPlan.put("totalCost", plan.totalCost());
        frontendPlan.put("budget", toFrontendBudgetMap(plan));
        return frontendPlan;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> normalizeLegacyDetail(Map<String, Object> detail) {
        if (detail == null) {
            return detail;
        }
        if (!detail.containsKey("plan") && detail.get("result") instanceof Map<?, ?> resultMap) {
            Object nestedPlan = resultMap.get("plan");
            if (nestedPlan instanceof Map) {
                detail.put("plan", nestedPlan);
            }
            if (!detail.containsKey("interventionReason")) {
                List<?> reasons = findHitlReasonsInLegacy((Map<?, ?>) resultMap);
                if (reasons != null && !reasons.isEmpty()) {
                    detail.put("interventionReason", String.valueOf(reasons.get(0)));
                }
            }
            detail.remove("result");
        }
        return detail;
    }

    private static Map<String, Object> toFrontendBudgetMap(TripPlan plan) {
        Map<String, Object> budgetMap = new LinkedHashMap<>();
        budgetMap.put("total", plan.budget().total());
        budgetMap.put("used", plan.budget().used());
        budgetMap.put("overrunRate", plan.budget().overrunRate());
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("transport", plan.budget().breakdown().transport());
        breakdown.put("hotel", plan.budget().breakdown().hotel());
        breakdown.put("food", plan.budget().breakdown().food());
        breakdown.put("ticket", plan.budget().breakdown().ticket());
        budgetMap.put("breakdown", breakdown);
        return budgetMap;
    }

    private static Map<String, Object> toFrontendDayMap(TripDay day, String destination) {
        Map<String, Object> fd = new LinkedHashMap<>();
        fd.put("date", day.date() != null ? day.date().toString() : null);
        fd.put("index", day.dayIndex());
        fd.put("cityLabel", "Day" + day.dayIndex() + " " + (destination != null ? destination : ""));
        fd.put("dailyCost", day.dailyCost());
        fd.put("attractions", toFrontendAttractionList(day.attractions()));
        fd.put("meals", toFrontendMealList(day.meals()));
        fd.put("routes", toFrontendRouteList(day));
        return fd;
    }

    private static List<Map<String, Object>> toFrontendAttractionList(List<Attraction> attractions) {
        List<Map<String, Object>> fa = new ArrayList<>();
        for (Attraction a : attractions) {
            Map<String, Object> ma = new LinkedHashMap<>();
            ma.put("id", a.attractionId());
            ma.put("name", a.name());
            ma.put("rating", a.rating());
            ma.put("durationMinutes", a.durationMin());
            ma.put("ticket", a.ticketPrice());
            ma.put("tags", a.tags());
            fa.add(ma);
        }
        return fa;
    }

    private static List<Map<String, Object>> toFrontendMealList(List<Meal> meals) {
        List<Map<String, Object>> fm = new ArrayList<>();
        for (Meal m : meals) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("name", m.name());
            mm.put("mealType", mealTypeToChinese(m.mealType()));
            mm.put("perCapita", m.avgPrice());
            mm.put("recommend", m.recommendDishes() != null && !m.recommendDishes().isEmpty()
                    ? m.recommendDishes().get(0) : "");
            fm.add(mm);
        }
        return fm;
    }

    private static List<Map<String, Object>> toFrontendRouteList(TripDay day) {
        List<String> stopNames = new ArrayList<>();
        for (Attraction a : day.attractions()) {
            stopNames.add(a.name());
        }
        for (Meal m : day.meals()) {
            stopNames.add(m.name());
        }
        List<Map<String, Object>> fr = new ArrayList<>();
        List<Route> routes = day.routes();
        for (int i = 0; i < routes.size(); i++) {
            Route r = routes.get(i);
            Map<String, Object> mr = new LinkedHashMap<>();
            mr.put("fromName", i < stopNames.size() ? stopNames.get(i) : r.from().address());
            mr.put("toName", i + 1 < stopNames.size() ? stopNames.get(i + 1) : r.to().address());
            mr.put("distanceKm", r.distanceKm());
            mr.put("durationMinutes", r.durationMin());
            mr.put("mode", r.transportMode());
            mr.put("fallback", r.fallback());
            fr.add(mr);
        }
        return fr;
    }

    private static String mealTypeToChinese(MealType mealType) {
        if (mealType == null) {
            return "";
        }
        return switch (mealType) {
            case BREAKFAST -> "早";
            case LUNCH -> "午";
            case DINNER -> "晚";
        };
    }

    /** 从 PlanSummary 解析出的 Map 中提取 request 字段子集。 */
    static Map<String, Object> summaryToRequestMap(Map<String, Object> summary) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("destination", summary.get("destination"));
        req.put("days", summary.get("days"));
        req.put("budget", summary.get("budget"));
        req.put("headcount", summary.get("headcount"));
        req.put("travelStyle", summary.get("travelStyle"));
        return req;
    }

    /** 将原始请求转换为前端可消费的 Map。 */
    static Map<String, Object> toFrontendRequestMap(TripPlanRequest req) {
        if (req == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("destination", req.destination());
        map.put("startDate", req.startDate() != null ? req.startDate().toString() : null);
        map.put("days", req.days());
        map.put("budget", req.budget());
        map.put("headcount", req.headcount());
        map.put("travelStyle", req.travelStyle() != null ? req.travelStyle().name() : null);
        map.put("preferences", req.preferences());
        return map;
    }

    private static List<?> findHitlReasonsInLegacy(Map<?, ?> resultMap) {
        if (resultMap.get("hitlReasons") instanceof List<?> list && !list.isEmpty()) {
            return list;
        }
        if (resultMap.get("plan") instanceof Map<?, ?> planMap
                && planMap.get("hitlReasons") instanceof List<?> list && !list.isEmpty()) {
            return list;
        }
        return List.of();
    }
}
