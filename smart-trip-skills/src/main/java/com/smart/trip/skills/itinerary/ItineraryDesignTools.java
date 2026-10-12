package com.smart.trip.skills.itinerary;

import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.AttractionJsonParser;
import com.smart.trip.skills.xhs.AttractionCatalog;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 行程编排业务工具（C-008 增量 B / AC-8）：把确定性 {@link ItineraryDesignService} 暴露为 AgentScope
 * {@code @Tool}，供 {@code HarnessAgent}（主 deepseek-v4-pro / 降级 qwen3-max）在 ReAct 循环中调用。
 *
 * <p><b>不变量留在 Java（绝不交给 LLM 裁决）</b>：每日午晚餐、景点不重复、时间窗 8:00-20:00、
 * 餐厅≤5km、交通耗时≤2h、评分≥3.5，全部由既有确定性 {@link ItineraryDesignService} 强制——
 * 本工具只是其门面。LLM 的职责是理解委派意图、调用本工具、解释结果，而非伪造或放宽行程数据。</p>
 *
 * <p><b>权威产物</b>：每次调用把确定性 {@link ItineraryDesignResult}（days + 风险清单 + fallback + 遥测）
 * 捕获到 {@link #lastResult()}，供 Agent 适配层回写 A2A {@code TASK_RESULT}——不依赖解析 LLM 自由文本，
 * 因此 LLM 非确定性不会污染行程数据正确性。</p>
 */
public final class ItineraryDesignTools {

    private static final System.Logger LOG =
            System.getLogger(ItineraryDesignTools.class.getName());

    private final ItineraryDesignService service;
    private final AttractionCatalog catalog;
    private final AtomicReference<ItineraryDesignResult> lastResult = new AtomicReference<>();

    /**
     * 带 catalog 坐标补全的构造器（生产路使用）。
     * 当 xhs-analyst 候选景点无 {@code location} 字段时，从 catalog 按 attractionId/name 查找补全。
     *
     * @param service 确定性行程编排 Skill，非 null
     * @param catalog 景点目录（补全坐标），可为 null（无补全能力时降级为空 days）
     */
    public ItineraryDesignTools(ItineraryDesignService service, AttractionCatalog catalog) {
        if (service == null) {
            throw new IllegalArgumentException("service 不可为 null");
        }
        this.service = service;
        this.catalog = catalog;
    }

    /**
     * 无 catalog 的向后兼容构造器（测试/简单场景）。
     *
     * @param service 确定性行程编排 Skill，非 null
     */
    public ItineraryDesignTools(ItineraryDesignService service) {
        this(service, null);
    }

    /**
     * 编排给定候选景点的多日行程，产出满足业务不变量的行程结果。
     *
     * @param requestJson     用户规划请求 JSON（含 destination/startDate/days/travelStyle 等字段）
     * @param attractionsJson 候选景点列表 JSON 数组（C-006 产出，含 attractionId/name/location 等字段）
     * @param routeResultJson 路线规划结果 JSON（C-007 产出），可空（无路线时按景点顺序编排）
     * @return 行程编排结果 JSON（days/risks/fallback/message）
     */
    @Tool(name = "design_itinerary",
            description = "编排给定候选景点的多日行程（按天分配景点→插入餐饮→排布时间槽→交通动线）。"
                    + "六项强规则（每日午晚餐、景点不重复、时间窗8:00-20:00、餐厅≤5km、交通耗时≤2h、"
                    + "评分≥3.5）已在工具内强制，禁止在工具外伪造、放宽或篡改行程数据。")
    public String designItinerary(
            @ToolParam(name = "request_json",
                    description = "用户规划请求 JSON，含 destination/startDate/days/budget/headcount/travelStyle")
            String requestJson,
            @ToolParam(name = "attractions_json",
                    description = "候选景点列表 JSON 数组，每项含 attractionId/name/location/rating/durationMin")
            String attractionsJson,
            @ToolParam(name = "route_result_json", required = false,
                    description = "路线规划结果 JSON（C-007 产出），可空，无路线时按景点顺序编排")
            String routeResultJson) {
        try {
            ItineraryDesignQuery query = parseQuery(requestJson, attractionsJson, routeResultJson);
            LOG.log(System.Logger.Level.INFO,
                    "design_itinerary input: attractionCount={0}, routeIsNull={1}, headcount={2}, days={3}",
                    query.candidateAttractions().size(),
                    query.routeResult() == null,
                    query.request() != null ? query.request().headcount() : -1,
                    query.request() != null ? query.request().days() : -1);
            ItineraryDesignResult result = service.design(query);
            lastResult.set(result);
            com.smart.trip.skills.supervisor.SupervisorNativeRunContext ctx =
                    com.smart.trip.skills.supervisor.SupervisorHarnessAgentFactory.activeRunContext();
            if (ctx != null) { ctx.setItineraryResult(result); }
            return toJson(result);
        } catch (Exception e) {
            ItineraryDesignResult fallback = new ItineraryDesignResult(
                    List.of(), List.of(), true, "design_itinerary 失败: " + e.getMessage(), null);
            lastResult.set(fallback);
            com.smart.trip.skills.supervisor.SupervisorNativeRunContext ctx =
                    com.smart.trip.skills.supervisor.SupervisorHarnessAgentFactory.activeRunContext();
            if (ctx != null) { ctx.setItineraryResult(fallback); }
            return toJson(fallback);
        }
    }

    /**
     * 取最近一次工具调用产出的确定性结果（权威行程 + 风险清单 + fallback + 遥测），Agent 适配层据此
     * 回写 {@code TASK_RESULT}。尚未调用则返回 {@code null}。
     *
     * @return 最近一次 {@link ItineraryDesignResult}，或 {@code null}
     */
    public ItineraryDesignResult lastResult() {
        return lastResult.get();
    }

    private ItineraryDesignQuery parseQuery(String requestJson, String attractionsJson,
                                            String routeResultJson) {
        com.smart.trip.common.dto.TripPlanRequest request = null;
        try {
            request = JsonUtils.fromJson(requestJson, com.smart.trip.common.dto.TripPlanRequest.class);
        } catch (Exception ignored) { }
        com.smart.trip.skills.supervisor.SupervisorNativeRunContext ctx =
                com.smart.trip.skills.supervisor.SupervisorHarnessAgentFactory.activeRunContext();
        if (request == null && ctx != null && ctx.request() != null) {
            request = ctx.request();
        }
        List<Attraction> attractions = enrichFromCatalog(AttractionJsonParser.parse(attractionsJson));
        // LLM 截断兜底：若 ctx 有更完整的候选列表，以 Java typed artifact 为准
        if (ctx != null && ctx.xhsResult() != null
                && ctx.xhsResult().candidates().size() > attractions.size()) {
            LOG.log(System.Logger.Level.WARNING,
                    "design_itinerary LLM truncated attractions: parsed={0}, ctxHas={1} → using ctx",
                    attractions.size(), ctx.xhsResult().candidates().size());
            attractions = enrichFromCatalog(ctx.xhsResult().candidates().stream()
                    .map(com.smart.trip.skills.xhs.AttractionCandidate::attraction)
                    .toList());
        }
        com.smart.trip.skills.route.RoutePlanResult routeResult =
                parseRouteResult(routeResultJson, ctx);
        return new ItineraryDesignQuery(request, attractions, routeResult);
    }

    private List<Attraction> enrichFromCatalog(List<Attraction> attractions) {
        if (catalog == null || attractions.isEmpty()) {
            return attractions;
        }
        return attractions.stream()
                .map(a -> {
                    if (a.location() != null) {
                        return a;
                    }
                    if (a.attractionId() != null) {
                        Optional<Attraction> enriched = catalog.findById(a.attractionId());
                        if (enriched.isPresent()) {
                            return enriched.get();
                        }
                    }
                    if (a.name() != null) {
                        Optional<Attraction> enriched = catalog.findByName(a.name());
                        if (enriched.isPresent()) {
                            return enriched.get();
                        }
                    }
                    return a;
                })
                .collect(Collectors.toList());
    }

    private static com.smart.trip.skills.route.RoutePlanResult parseRouteResult(String json,
            com.smart.trip.skills.supervisor.SupervisorNativeRunContext ctx) {
        if (json == null || json.isBlank()) {
            // LLM 未传路线结果 → 从 ctx 取 Java typed artifact 兜底
            if (ctx != null && ctx.routeResult() != null) {
                LOG.log(System.Logger.Level.INFO,
                        "design_itinerary: no route JSON, using ctx.routeResult() as fallback");
                return ctx.routeResult();
            }
            return null;
        }
        try {
            return JsonUtils.fromJson(json, com.smart.trip.skills.route.RoutePlanResult.class);
        } catch (Exception e) {
            // LLM 拼的 JSON 格式不合法 → 从 ctx 取 Java typed artifact 兜底
            LOG.log(System.Logger.Level.WARNING,
                    "design_itinerary: route JSON deserialization failed ({0}), using ctx.routeResult()",
                    e.getMessage());
            if (ctx != null && ctx.routeResult() != null) {
                return ctx.routeResult();
            }
            return null;
        }
    }

    private static String toJson(ItineraryDesignResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("days", result.days().stream()
                .map(ItineraryDesignTools::dayView).collect(Collectors.toList()));
        payload.put("risks", result.risks().stream()
                .map(ItineraryDesignTools::riskView).collect(Collectors.toList()));
        payload.put("fallback", result.fallback());
        payload.put("message", result.message());
        return JsonUtils.toJson(payload);
    }

    private static Map<String, Object> dayView(TripDay day) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("dayIndex", day.dayIndex());
        view.put("date", String.valueOf(day.date()));
        view.put("attractionCount", day.attractions().size());
        view.put("attractions", day.attractions().stream()
                .map(a -> {
                    Map<String, Object> av = new LinkedHashMap<>();
                    av.put("attractionId", a.attractionId());
                    av.put("name", a.name());
                    av.put("rating", a.rating());
                    av.put("durationMin", a.durationMin());
                    return av;
                }).collect(Collectors.toList()));
        view.put("meals", day.meals().stream()
                .map(ItineraryDesignTools::mealView).collect(Collectors.toList()));
        view.put("dailyCost", day.dailyCost());
        return view;
    }

    private static Map<String, Object> mealView(Meal meal) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", meal.name());
        view.put("mealType", String.valueOf(meal.mealType()));
        view.put("avgPrice", meal.avgPrice());
        return view;
    }

    private static Map<String, Object> riskView(ItineraryRisk risk) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", risk.code());
        view.put("message", risk.message());
        return view;
    }
}
