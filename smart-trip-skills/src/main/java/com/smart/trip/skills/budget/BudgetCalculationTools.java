package com.smart.trip.skills.budget;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 费用核算业务工具（C-009 增量 B / AC-11）：把确定性 {@link BudgetCalculationService} 暴露为 AgentScope
 * {@code @Tool}，供 {@code HarnessAgent}（主 deepseek-v4-pro / 降级 qwen3-max）在 ReAct 循环中调用。
 *
 * <p><b>不变量留在 Java（绝不交给 LLM 裁决）</b>：BigDecimal 精确核算、overrunRate ≥0.15 触发 HITL、
 * 对账一致性、缺失项保守估算降级，全部由既有确定性 {@link BudgetCalculationService} 强制——本工具只是其门面。
 * LLM 的职责是理解委派意图、调用本工具、解释结果，而非伪造或放宽费用数据。</p>
 *
 * <p><b>权威产物</b>：每次调用把确定性 {@link BudgetCalculationResult}（四类拆分 + overrunRate + HITL + 建议）
 * 捕获到 {@link #lastResult()}，供 Agent 适配层回写 A2A {@code TASK_RESULT}——不依赖解析 LLM 自由文本，
 * 因此 LLM 非确定性不会污染费用数据正确性。</p>
 */
public final class BudgetCalculationTools {

    private final BudgetCalculationService service;
    private final AtomicReference<BudgetCalculationResult> lastResult = new AtomicReference<>();

    private static final System.Logger LOG = System.getLogger(BudgetCalculationTools.class.getName());

    /**
     * @param service 确定性费用核算 Skill（承载全部业务不变量与降级），非 null
     */
    public BudgetCalculationTools(BudgetCalculationService service) {
        if (service == null) {
            throw new IllegalArgumentException("service 不可为 null");
        }
        this.service = service;
    }

    /**
     * 对编排好的行程执行四类费用拆分与预算管控，产出满足业务不变量的核算结果。
     *
     * @param requestJson         规划请求 JSON（含 budget/headcount/days/destination/travelStyle）
     * @param itineraryResultJson 行程编排结果 JSON（含 days 逐日景点/餐饮/路线/dailyCost）
     * @return 费用核算结果 JSON（totalCost/四类拆分/overrunRate/requiresHITL/调整建议/fallback/telemetry）
     */
    @Tool(name = "calculate_budget",
            description = "对编排好的行程做四类费用拆分（交通/住宿/餐饮/门票）与 BigDecimal 精确核算，"
                    + "计算超支率 overrunRate，overrunRate ≥ 0.15（含等号）时触发 HITL 并生成调整建议。"
                    + "缺失费用项保守估算（住宿/交通/餐饮）+ fallback=true。"
                    + "BigDecimal 精确判定、HITL 阈值、对账一致性已在工具内强制，"
                    + "禁止在工具外伪造、放宽或篡改费用数据。")
    public String calculateBudget(
            @ToolParam(name = "request_json",
                    description = "规划请求 JSON 对象（含 budget/headcount/days/destination/travelStyle 等字段）")
            String requestJson,
            @ToolParam(name = "itinerary_result_json",
                    description = "行程编排结果 JSON 对象（含 days 列表，每项含 attractions/meals/routes/dailyCost）")
            String itineraryResultJson) {
        com.smart.trip.skills.supervisor.SupervisorNativeRunContext ctx =
                com.smart.trip.skills.supervisor.SupervisorHarnessAgentFactory.activeRunContext();
        TripPlanRequest request = resolveRequest(requestJson, ctx);
        ItineraryDesignResult itinerary = resolveItinerary(itineraryResultJson, ctx);
        BudgetCalculationQuery query = new BudgetCalculationQuery(request, itinerary);
        BudgetCalculationResult result = service.calculate(query);
        lastResult.set(result);
        if (ctx != null) { ctx.setBudgetResult(result); }
        return toJson(result);
    }

    private static TripPlanRequest resolveRequest(String requestJson,
            com.smart.trip.skills.supervisor.SupervisorNativeRunContext ctx) {
        TripPlanRequest request = null;
        try {
            request = JsonUtils.fromJson(requestJson, TripPlanRequest.class);
        } catch (Exception ignored) { }
        if (request == null && ctx != null && ctx.request() != null) {
            request = ctx.request();
        }
        return request;
    }

    private static ItineraryDesignResult resolveItinerary(String itineraryResultJson,
            com.smart.trip.skills.supervisor.SupervisorNativeRunContext ctx) {
        ItineraryDesignResult itinerary = null;
        try {
            itinerary = JsonUtils.fromJson(itineraryResultJson, ItineraryDesignResult.class);
        } catch (Exception e) {
            if (ctx != null && ctx.itineraryResult() != null) {
                LOG.log(System.Logger.Level.WARNING,
                    "LLM 可能传错/截断行程编排结果 JSON → 从 Java 侧已捕获的权威产物 fallback {0}", ctx.itineraryResult());
                return ctx.itineraryResult();
            }
            throw new IllegalArgumentException("无法解析行程结果且上下文中无权威行程", e);
        }
        if ((itinerary == null || itinerary.days() == null || itinerary.days().isEmpty())
                && ctx != null && ctx.itineraryResult() != null) {
            return ctx.itineraryResult();
        }
        return itinerary;
    }

    /**
     * 取最近一次工具调用产出的确定性结果（四类拆分 + overrunRate + HITL + 建议 + 遥测），
     * Agent 适配层据此回写 {@code TASK_RESULT}。尚未调用则返回 {@code null}。
     *
     * @return 最近一次 {@link BudgetCalculationResult}，或 {@code null}
     */
    public BudgetCalculationResult lastResult() {
        return lastResult.get();
    }

    private static String toJson(BudgetCalculationResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalCost", result.totalCost());
        payload.put("transport", result.transport());
        payload.put("hotel", result.hotel());
        payload.put("food", result.food());
        payload.put("ticket", result.ticket());
        payload.put("overrunRate", result.overrunRate());
        payload.put("dailyBreakdowns", result.dailyBreakdowns());
        payload.put("requiresHITL", result.requiresHITL());
        payload.put("hitlReason", result.hitlReason());
        payload.put("adjustmentAdvice", result.adjustmentAdvice());
        payload.put("fallback", result.fallback());
        payload.put("telemetry", result.telemetry());
        payload.put("summary", buildSummary(result));
        return JsonUtils.toJson(payload);
    }

    private static String buildSummary(BudgetCalculationResult result) {
        String pct = result.overrunRate()
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .toPlainString();
        StringBuilder sb = new StringBuilder();
        sb.append("总费用 ¥").append(fmt(result.totalCost()))
          .append("（交通 ¥").append(fmt(result.transport()))
          .append(" / 住宿 ¥").append(fmt(result.hotel()))
          .append(" / 餐饮 ¥").append(fmt(result.food()))
          .append(" / 门票 ¥").append(fmt(result.ticket())).append("）")
          .append("，超支率 ").append(pct).append("%");
        if (result.requiresHITL()) {
            sb.append("，已触发人工介入：").append(result.hitlReason());
            List<String> advice = result.adjustmentAdvice();
            if (advice != null && !advice.isEmpty()) {
                sb.append("，建议：").append(String.join("；", advice));
            }
        } else {
            sb.append("，预算达标，无需人工介入");
        }
        return sb.toString();
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
