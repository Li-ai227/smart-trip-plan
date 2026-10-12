package com.smart.trip.skills.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.budget.DailyCostDetail;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TripPlanAssembler} 单测：验证 TripDay.dailyCost 与 BudgetCalculationResult 逐日明细对齐。
 *
 * <p>背景（用户报告）：Day 卡片展示的"当日小计"只含门票+餐饮（{@code ItineraryDesignService}
 * 产出），从不包含交通/住宿；而 {@code trip_budget_record.total_cost}（{@link BudgetCalculationService}
 * 产出）是门票+餐饮+交通+住宿的全量核算——两者互不知情，导致 Day1..DayN 加总永远小于 total_cost。
 * 本类验证 assemble() 用 budgetResult 的逐日明细回填 TripDay.dailyCost，使两者口径一致。</p>
 */
class TripPlanAssemblerTest {

    private static final GeoLocation LOC = new GeoLocation(104.05, 30.67, "测试", "成都");
    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    private static TripPlanRequest request() {
        return new TripPlanRequest("成都", START, 2, new BigDecimal("8000"), 2,
                TravelStyle.FAMILY, List.of());
    }

    private static TripDay day(int dayIndex, BigDecimal itineraryOnlyDailyCost) {
        Attraction a = new Attraction("A-" + dayIndex, "景点", LOC, 4.5, 120,
                new BigDecimal("50"), List.of());
        return new TripDay(START.plusDays(dayIndex - 1), dayIndex,
                List.of(a), List.of(), List.of(), itineraryOnlyDailyCost);
    }

    private static DailyCostDetail detail(int dayIndex, BigDecimal dailyTotal) {
        return new DailyCostDetail(dayIndex, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, dailyTotal);
    }

    @Test
    @DisplayName("回归：assemble() 用 budgetResult 逐日明细回填 dailyCost，使 Σdays.dailyCost == totalCost")
    void dailyCostReconciledWithBudgetDailyBreakdown() {
        // 编排阶段的 dailyCost 只含门票（50），不含交通/住宿——这是问题现状
        ItineraryDesignResult itinerary = new ItineraryDesignResult(
                List.of(day(1, new BigDecimal("50")), day(2, new BigDecimal("50"))),
                List.of(), false, null, null);

        // 预算核算阶段的逐日明细才含交通+住宿，dailyTotal 才是当日真实合计
        BudgetCalculationResult budget = new BudgetCalculationResult(
                new BigDecimal("1100"), new BigDecimal("200"), new BigDecimal("800"),
                new BigDecimal("0"), new BigDecimal("100"), new BigDecimal("-0.86"),
                List.of(detail(1, new BigDecimal("500")), detail(2, new BigDecimal("600"))),
                false, null, List.of(), false, null);

        TripPlan plan = TripPlanAssembler.assemble("P-001", request(), itinerary, budget);

        assertEquals(new BigDecimal("500"), plan.days().get(0).dailyCost(),
                "Day1 dailyCost 应替换为预算核算的当日真实合计（含交通+住宿），而非编排阶段只含门票的旧值");
        assertEquals(new BigDecimal("600"), plan.days().get(1).dailyCost());

        BigDecimal daySum = plan.days().stream()
                .map(TripDay::dailyCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, daySum.compareTo(plan.totalCost()),
                "Day 卡片加总应与 total_cost 口径一致：" + daySum + " vs " + plan.totalCost());
    }

    @Test
    @DisplayName("budgetResult 为 null（降级场景）→ 保留编排阶段原始 dailyCost，不抛异常")
    void nullBudgetResultKeepsItineraryDailyCost() {
        ItineraryDesignResult itinerary = new ItineraryDesignResult(
                List.of(day(1, new BigDecimal("50"))), List.of(), false, null, null);

        TripPlan plan = TripPlanAssembler.assemble("P-002", request(), itinerary, null);

        assertEquals(new BigDecimal("50"), plan.days().get(0).dailyCost());
    }

    @Test
    @DisplayName("dailyBreakdowns 缺失某天明细（数据不全）→ 该天保留原始 dailyCost，不抛异常")
    void missingDetailForSomeDayKeepsOriginal() {
        ItineraryDesignResult itinerary = new ItineraryDesignResult(
                List.of(day(1, new BigDecimal("50")), day(2, new BigDecimal("70"))),
                List.of(), false, null, null);
        // 只有 Day1 的明细，Day2 缺失（模拟异常/边界场景）
        BudgetCalculationResult budget = new BudgetCalculationResult(
                new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(detail(1, new BigDecimal("500"))),
                false, null, List.of(), false, null);

        TripPlan plan = TripPlanAssembler.assemble("P-003", request(), itinerary, budget);

        assertEquals(new BigDecimal("500"), plan.days().get(0).dailyCost());
        assertEquals(new BigDecimal("70"), plan.days().get(1).dailyCost(),
                "Day2 无对应明细时应保留编排阶段原始值，不应被置零或抛异常");
    }
}
