package com.smart.trip.skills.supervisor;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.PlanStatus;
import com.smart.trip.common.model.Budget;
import com.smart.trip.common.model.BudgetBreakdown;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.common.util.IdUtils;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.budget.DailyCostDetail;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C-012-C：TripPlan 组装器，从 typed artifact 构建 {@link TripPlan}。
 *
 * <p>不触发任何 Agent dispatch，仅做内存数据组装。供以下两条路径共享：
 * <ul>
 *   <li>Deterministic path：{@link TripOrchestrationService#orchestrate} 在 dispatch 四子 Agent 后调用</li>
 *   <li>Native path：{@link SupervisorHarnessAgentFactory#run} 在收集四个 subagent artifact 后调用</li>
 * </ul>
 *
 * <p>抽出此类的目的是避免 native finalizer 为了组装 TripPlan 而调用
 * {@code TripOrchestrationService.orchestrate()}——后者会重新 dispatch 四个 Agent。</p>
 */
public final class TripPlanAssembler {

    private TripPlanAssembler() {
        // 工具类，禁止实例化
    }

    /**
     * 从 itinerary + budget typed result 组装 {@link TripPlan}。
     *
     * <p>planId 由调用方传入（由 {@code TripPlanFacade.submitPlan()} 在提交时生成），
     * Assembler 本身不再自行生成 planId，避免与 Redis 中的 planId 不一致。</p>
     *
     * @param planId          方案 ID，由上游透传；为 null 时回退 IdUtils 生成（仅向后兼容）
     * @param request         用户规划请求，非 null
     * @param itineraryResult 行程编排结果，可为 null（null 时 days 为空列表）
     * @param budgetResult    预算核算结果，可为 null（null 时费用归零）
     * @return 组装后的 TripPlan（status=REVIEW）
     */
    public static TripPlan assemble(
            String planId,
            TripPlanRequest request,
            ItineraryDesignResult itineraryResult,
            BudgetCalculationResult budgetResult) {
        List<TripDay> days = itineraryResult != null
                ? new ArrayList<>(itineraryResult.days()) : new ArrayList<>();
        reconcileDailyCost(days, budgetResult);
        BigDecimal totalCost = budgetResult != null ? budgetResult.totalCost() : BigDecimal.ZERO;
        BigDecimal overrunRate = budgetResult != null ? budgetResult.overrunRate() : BigDecimal.ZERO;
        BudgetBreakdown breakdown = budgetResult != null
                ? new BudgetBreakdown(budgetResult.transport(), budgetResult.hotel(),
                budgetResult.food(), budgetResult.ticket())
                : new BudgetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
        Budget budget = new Budget(request.budget(), totalCost, overrunRate, breakdown);
        boolean hitl = budgetResult != null && budgetResult.requiresHITL();
        String resolvedPlanId = (planId != null && !planId.isBlank())
                ? planId : IdUtils.newPlanId(request.startDate(), 1);
        return new TripPlan(resolvedPlanId, request, days,
                totalCost, budget, PlanStatus.REVIEW, hitl);
    }

    /**
     * 向后兼容：无 planId 时回退 IdUtils 生成。
     *
     * @deprecated 请使用 {@link #assemble(String, TripPlanRequest, ItineraryDesignResult, BudgetCalculationResult)}
     */
    @Deprecated
    public static TripPlan assemble(
            TripPlanRequest request,
            ItineraryDesignResult itineraryResult,
            BudgetCalculationResult budgetResult) {
        return assemble(null, request, itineraryResult, budgetResult);
    }

    /**
     * 用预算核算的逐日明细回填 TripDay.dailyCost，使 Day 卡片口径与 total_cost 一致。
     *
     * <p>{@link com.smart.trip.skills.itinerary.ItineraryDesignService#computeDailyCost}
     * 只算门票+餐饮，不含交通和住宿；而 {@link com.smart.trip.skills.budget.BudgetCalculationService}
     * 的 {@link DailyCostDetail#dailyTotal()} 是四类费用的当日合计。
     * 此处以预算核算为准，替换编排阶段的不完整值。</p>
     */
    private static void reconcileDailyCost(List<TripDay> days, BudgetCalculationResult budgetResult) {
        if (budgetResult == null || days.isEmpty()) {
            return;
        }
        List<DailyCostDetail> details = budgetResult.dailyBreakdowns();
        if (details == null || details.isEmpty()) {
            return;
        }
        Map<Integer, DailyCostDetail> indexMap = details.stream()
                .collect(Collectors.toMap(DailyCostDetail::dayIndex, Function.identity()));
        days.replaceAll(day -> {
            DailyCostDetail detail = indexMap.get(day.dayIndex());
            if (detail != null) {
                return new TripDay(day.date(), day.dayIndex(), day.attractions(),
                        day.meals(), day.routes(), detail.dailyTotal());
            }
            return day;
        });
    }
}
