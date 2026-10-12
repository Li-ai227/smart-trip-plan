package com.smart.trip.skills.supervisor;

import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.trace.TraceDelegationRecord;
import com.smart.trip.skills.trace.TracePersistenceService;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import java.time.Instant;

/**
 * native 成功路径的子 Agent 委派审计（C-017 FIX-3）。
 *
 * <p>native 模式下四个子 Agent 由 {@code agent_spawn} 驱动，不经过
 * {@code TripOrchestrationService#dispatchXHS} 等方法，因此没有逐步耗时可用——
 * {@code durationMs} 固定为 0，仅记录任务摘要、响应摘要与是否降级，
 * 保证 {@code trip_agent_delegation_log} 在 native 成功场景下也有数据可查，
 * 与确定性/降级路（{@code TripOrchestrationService#traceDelegation}）产出的数据对齐。</p>
 */
final class NativeDelegationTracer {

    private NativeDelegationTracer() {
    }

    /**
     * @param tracePersistence 审计追踪持久化，可为 null（null 时跳过审计）
     * @param traceId          全链路追踪 ID，可为空白（空白时跳过审计）
     * @param planId           方案 ID
     * @param ctx              已收口的 native run artifact 容器
     */
    static void trace(TracePersistenceService tracePersistence, String traceId, String planId,
                       SupervisorNativeRunContext ctx) {
        if (tracePersistence == null || traceId == null || traceId.isBlank()) {
            return;
        }
        try {
            traceOne(tracePersistence, traceId, planId, TripOrchestrationService.XHS_URI,
                    "XHS_ANALYSIS", xhsSummary(ctx.xhsResult()),
                    ctx.xhsResult() != null && ctx.xhsResult().fallback());
            traceOne(tracePersistence, traceId, planId, TripOrchestrationService.ROUTE_URI,
                    "ROUTE_PLANNING", routeSummary(ctx.routeResult()),
                    ctx.routeResult() != null && ctx.routeResult().fallback());
            traceOne(tracePersistence, traceId, planId, TripOrchestrationService.ITINERARY_URI,
                    "ITINERARY_DESIGN", itinerarySummary(ctx.itineraryResult()),
                    ctx.itineraryResult() != null && ctx.itineraryResult().fallback());
            traceOne(tracePersistence, traceId, planId, TripOrchestrationService.BUDGET_URI,
                    "COST_CALCULATION", budgetSummary(ctx.budgetResult()),
                    ctx.budgetResult() != null && ctx.budgetResult().fallback());
        } catch (Exception ex) {
            // 审计失败不影响主链路
        }
    }

    private static void traceOne(TracePersistenceService tracePersistence, String traceId,
                                  String planId, String subAgentUri, String taskType,
                                  String responseSummary, boolean fallback) {
        tracePersistence.saveDelegation(new TraceDelegationRecord(
                traceId, planId, TripOrchestrationService.SUPERVISOR_URI, subAgentUri, taskType,
                "native agent_spawn", responseSummary, 0L, fallback, "", Instant.now()));
    }

    private static String xhsSummary(XHSAnalysisResult xhs) {
        return xhs != null ? "candidates=" + xhs.candidates().size() : "null";
    }

    private static String routeSummary(RoutePlanResult route) {
        return route != null ? "routes=" + route.routes().size() : "null";
    }

    private static String itinerarySummary(ItineraryDesignResult itinerary) {
        return itinerary != null ? "days=" + itinerary.days().size() : "null";
    }

    private static String budgetSummary(BudgetCalculationResult budget) {
        return budget != null ? "totalCost=" + budget.totalCost() : "null";
    }
}
