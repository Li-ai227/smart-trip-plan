package com.smart.trip.skills.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * BudgetHarnessAgentFactory env-gated 集成测试（C-009 AC-12）。
 *
 * <p>验证真实 HarnessAgent（LLM + @Tool calculate_budget + SKILL.md）端到端跑通费用核算。
 * 需要 {@code DEEPSEEK_API_KEY} 环境变量，无 key 时自动 skip，不伪装通过。</p>
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+",
        disabledReason = "需要 DEEPSEEK_API_KEY 环境变量才能运行真实 LLM 集成测试")
class BudgetHarnessAgentFactoryIT {

    private static final GeoLocation LOC = new GeoLocation(104.05, 30.67, "宽窄巷子", "成都");
    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    private static TripDay tripDay() {
        return new TripDay(START, 1,
                List.of(new Attraction("A-001", "宽窄巷子", LOC, 4.5, 120,
                        new BigDecimal("80"), List.of())),
                List.of(new Meal("午餐", LOC, MealType.LUNCH,
                        new BigDecimal("60"), List.of())),
                List.of(new Route(LOC, LOC, 15.0, 30, "驾车", false)),
                BigDecimal.ZERO);
    }

    @Test
    void realLLM_callsCalculateBudget_returnsDeterministicResult() throws Exception {
        Path workspace = Path.of("target", "budget-it-workspace");
        Files.createDirectories(workspace);

        BudgetCalculationService service = new BudgetCalculationService();
        BudgetHarnessAgentFactory factory =
                BudgetHarnessAgentFactory.fromEnvironment(service, workspace);
        assertNotNull(factory, "应基于 DEEPSEEK_API_KEY 成功构建工厂");

        TripPlanRequest request = new TripPlanRequest(
                "成都", START, 1, new BigDecimal("8000"), 2,
                TravelStyle.FAMILY, List.of());
        ItineraryDesignResult itinerary = new ItineraryDesignResult(
                List.of(tripDay()), List.of(), false, null, null);
        BudgetCalculationQuery query = new BudgetCalculationQuery(request, itinerary);

        BudgetCalculationResult result = factory.run(query);

        // AC-12: 真实原生链路产出确定性费用数据（来自 @Tool calculate_budget → BudgetCalculationService）
        assertNotNull(result, "原生路应产出权威结果");
        assertTrue(result.totalCost().signum() > 0, "总费用应 > 0");
        assertNotNull(result.totalCost());
        assertNotNull(result.overrunRate());
        assertFalse(result.requiresHITL(), "预算内不应触发 HITL");

        // 四类拆分完整
        assertEquals(0, result.transport().add(result.hotel())
                .add(result.food()).add(result.ticket()).compareTo(result.totalCost()));

        factory.close();
    }
}
