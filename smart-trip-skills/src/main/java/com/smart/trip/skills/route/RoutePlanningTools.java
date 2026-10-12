package com.smart.trip.skills.route;

import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.AttractionJsonParser;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 路线规划业务工具（C-007 增量 C / AC-11）：把确定性 {@link RoutePlanningService} 暴露为 AgentScope
 * {@code @Tool}，供 {@code HarnessAgent}（主 deepseek-v4-pro / 降级 qwen3-max）在 ReAct 循环中调用。
 *
 * <p><b>不变量留在 Java（绝不交给 LLM 裁决）</b>：耗时≤120min 超限标记、三类降级（地理编码/路线/路况）、
 * 限频≤30/min、haversine 离线估算，全部由既有确定性 {@link RoutePlanningService} 强制——本工具只是其门面。
 * LLM 的职责是理解委派意图、调用本工具、解释结果，而非伪造或放宽路线数据。</p>
 *
 * <p><b>权威产物</b>：每次调用把确定性 {@link RoutePlanResult}（routes + 超限清单 + fallback + 遥测）
 * 捕获到 {@link #lastResult()}，供 Agent 适配层回写 A2A {@code TASK_RESULT}——不依赖解析 LLM 自由文本，
 * 因此 LLM 非确定性不会污染路线数据正确性。</p>
 */
public final class RoutePlanningTools {

    private static final System.Logger LOG =
            System.getLogger(RoutePlanningTools.class.getName());

    private final RoutePlanningService service;
    private final AtomicReference<RoutePlanResult> lastResult = new AtomicReference<>();

    /**
     * @param service 确定性路线规划 Skill（承载全部业务不变量与降级），非 null
     */
    public RoutePlanningTools(RoutePlanningService service) {
        if (service == null) {
            throw new IllegalArgumentException("service 不可为 null");
        }
        this.service = service;
    }

    /**
     * 规划给定景点间的相邻段交通路线，产出满足业务不变量的路线结果。
     *
     * @param destination    目的地名（如 成都），非空白
     * @param attractionsJson 景点列表 JSON 数组（含 id/name/location 等字段）
     * @param transportMode  偏好交通方式（驾车/步行/骑行/公交），可空，缺省驾车
     * @return 路线规划结果 JSON（routes/overLimitRoutes/unreachableSegments/fallback/message）
     */
    @Tool(name = "plan_routes",
            description = "规划给定景点间的相邻段交通路线（坐标解析→相邻点路线→装配 Route→超限标记）。"
                    + "耗时≤120min 超限标记、三类降级（地理编码/路线/路况）、限频≤30/min 已在工具内强制，"
                    + "禁止在工具外伪造、放宽或篡改路线数据。")
    public String planRoutes(
            @ToolParam(name = "destination", description = "目的地名，如 成都/云南") String destination,
            @ToolParam(name = "attractions_json",
                    description = "景点列表 JSON 数组，每项含 attractionId/name/location 等字段")
            String attractionsJson,
            @ToolParam(name = "transport_mode", required = false,
                    description = "偏好交通方式（驾车/步行/骑行/公交），可空，缺省驾车")
            String transportMode) {
        com.smart.trip.skills.supervisor.SupervisorNativeRunContext ctx = null;
        try {
            List<Attraction> attractions = AttractionJsonParser.parse(attractionsJson);
            int rawAttrCount = attractionsJson != null
                    ? (attractionsJson.split("\"attractionId\"").length - 1) : 0;
            // LLM 截断兜底：若 XHS 已产出完整候选列表但 LLM 只传了子集，从 ctx 取权威数据
            ctx = com.smart.trip.skills.supervisor.SupervisorHarnessAgentFactory.activeRunContext();
            if (ctx != null && ctx.xhsResult() != null
                    && ctx.xhsResult().candidates().size() > attractions.size()) {
                LOG.log(System.Logger.Level.WARNING,
                        "plan_routes LLM truncated: parsed={0}, ctxHas={1} → using ctx candidates",
                        attractions.size(), ctx.xhsResult().candidates().size());
                attractions = ctx.xhsResult().candidates().stream()
                        .map(com.smart.trip.skills.xhs.AttractionCandidate::attraction)
                        .toList();
            }
            LOG.log(System.Logger.Level.INFO,
                    "plan_routes input: destination={0}, rawJsonLen={1}, rawAttrIds={2}, parsed={3}",
                    destination,
                    attractionsJson != null ? attractionsJson.length() : 0,
                    Math.max(0, rawAttrCount),
                    attractions.size());
            RoutePlanQuery query = new RoutePlanQuery(destination, attractions, transportMode);
            RoutePlanResult result = service.plan(query);
            lastResult.set(result);
            if (ctx != null) { ctx.setRouteResult(result); }
            return toJson(result);
        } catch (Exception e) {
            RoutePlanResult fallback = new RoutePlanResult(
                    List.of(), List.of(), List.of(), true, "plan_routes 失败: " + e.getMessage(), null);
            lastResult.set(fallback);
            if (ctx != null) { ctx.setRouteResult(fallback); }
            return toJson(fallback);
        }
    }

    /**
     * 取最近一次工具调用产出的确定性结果（权威路线 + 超限清单 + fallback + 遥测），Agent 适配层据此
     * 回写 {@code TASK_RESULT}。尚未调用则返回 {@code null}。
     *
     * @return 最近一次 {@link RoutePlanResult}，或 {@code null}
     */
    public RoutePlanResult lastResult() {
        return lastResult.get();
    }

    private static String toJson(RoutePlanResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("routes", result.routes().stream()
                .map(RoutePlanningTools::routeView).collect(Collectors.toList()));
        payload.put("overLimitRoutes", result.overLimitRoutes().stream()
                .map(RoutePlanningTools::routeView).collect(Collectors.toList()));
        payload.put("unreachableSegments", result.unreachableSegments());
        payload.put("fallback", result.fallback());
        payload.put("message", result.message());
        return JsonUtils.toJson(payload);
    }

    private static Map<String, Object> routeView(Route route) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("from", locationView(route.from()));
        view.put("to", locationView(route.to()));
        view.put("distanceKm", route.distanceKm());
        view.put("durationMin", route.durationMin());
        view.put("transportMode", route.transportMode());
        view.put("fallback", route.fallback());
        return view;
    }

    private static Map<String, Object> locationView(com.smart.trip.common.model.GeoLocation loc) {
        if (loc == null) {
            return Map.of();
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("longitude", loc.longitude());
        view.put("latitude", loc.latitude());
        view.put("address", loc.address());
        view.put("city", loc.city());
        return view;
    }
}
