package com.smart.trip.skills.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link BudgetCalculationTools} 确定性单测（C-009 增量 B / AC-11）：验证 {@code @Tool calculate_budget}
 * 的产物与直接调用 {@link BudgetCalculationService} 完全一致（权威结果来自确定性核心，不依赖 LLM），
 * 且 {@link BudgetCalculationTools#lastResult()} 正确捕获。
 *
 * <p>不涉及任何 LLM/HarnessAgent——本层只锁工具门面的确定性正确性。</p>
 */
class BudgetCalculationToolsTest {

    private static final GeoLocation LOC = new GeoLocation(104.05, 30.67, "宽窄巷子", "成都");
    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    private final BudgetCalculationService service = new BudgetCalculationService();

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
    @DisplayName("构造器拒绝 null service")
    void rejectsNullService() {
        assertThrows(IllegalArgumentException.class, () -> new BudgetCalculationTools(null));
    }

    @Test
    @DisplayName("lastResult 初始为 null")
    void lastResultInitiallyNull() {
        BudgetCalculationTools tools = new BudgetCalculationTools(service);
        assertNull(tools.lastResult());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Happy Path：工具产物 == 确定性 Service 产物，lastResult 一致")
    void toolOutputMatchesDeterministicService() {
        TripPlanRequest request = new TripPlanRequest(
                "成都", START, 1, new BigDecimal("8000"), 2,
                TravelStyle.FAMILY, List.of());
        ItineraryDesignResult itinerary = new ItineraryDesignResult(
                List.of(tripDay()), List.of(), false, null, null);

        BudgetCalculationResult direct = service.calculate(
                new BudgetCalculationQuery(request, itinerary));

        BudgetCalculationTools tools = new BudgetCalculationTools(service);
        String requestJson = JsonUtils.toJson(request);
        String itineraryJson = JsonUtils.toJson(itinerary);
        String json = tools.calculateBudget(requestJson, itineraryJson);

        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
        assertNotNull(payload.get("totalCost"));
        assertNotNull(payload.get("overrunRate"));
        assertEquals(direct.requiresHITL(), payload.get("requiresHITL"));
        assertEquals(direct.fallback(), payload.get("fallback"));

        BudgetCalculationResult toolResult = tools.lastResult();
        assertNotNull(toolResult);
        assertEquals(0, direct.totalCost().compareTo(toolResult.totalCost()));
        assertEquals(direct.requiresHITL(), toolResult.requiresHITL());
        assertEquals(direct.fallback(), toolResult.fallback());
    }

    @Test
    @DisplayName("工具产物含四类拆分字段")
    void toolOutputHasBreakdown() {
        TripPlanRequest request = new TripPlanRequest(
                "成都", START, 1, new BigDecimal("8000"), 2,
                TravelStyle.FAMILY, List.of());
        ItineraryDesignResult itinerary = new ItineraryDesignResult(
                List.of(tripDay()), List.of(), false, null, null);

        BudgetCalculationTools tools = new BudgetCalculationTools(service);
        String json = tools.calculateBudget(
                JsonUtils.toJson(request), JsonUtils.toJson(itinerary));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
        assertNotNull(payload.get("transport"));
        assertNotNull(payload.get("hotel"));
        assertNotNull(payload.get("food"));
        assertNotNull(payload.get("ticket"));
        assertTrue(payload.containsKey("adjustmentAdvice"));
        assertTrue(payload.containsKey("hitlReason"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("工具产物含可读说明 summary，LLM 可直接输出无需额外计算")
    void toolOutputContainsCompleteSummary() {
        TripPlanRequest request = new TripPlanRequest(
                "成都", START, 1, new BigDecimal("8000"), 2,
                TravelStyle.FAMILY, List.of());
        ItineraryDesignResult itinerary = new ItineraryDesignResult(
                List.of(tripDay()), List.of(), false, null, null);

        BudgetCalculationTools tools = new BudgetCalculationTools(service);
        String json = tools.calculateBudget(
                JsonUtils.toJson(request), JsonUtils.toJson(itinerary));

        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);

        // 必须字段全部存在
        assertNotNull(payload.get("totalCost"), "必须含 totalCost");
        assertNotNull(payload.get("transport"), "必须含 transport");
        assertNotNull(payload.get("hotel"), "必须含 hotel");
        assertNotNull(payload.get("food"), "必须含 food");
        assertNotNull(payload.get("ticket"), "必须含 ticket");
        assertNotNull(payload.get("overrunRate"), "必须含 overrunRate");
        assertTrue(payload.containsKey("requiresHITL"), "必须含 requiresHITL");

        // 可读说明：LLM 直接输出，不需要再做任何计算
        Object summary = payload.get("summary");
        assertNotNull(summary, "必须含可读说明 summary，供 LLM 直接输出");
        assertTrue(summary instanceof String, "summary 必须为字符串");
        assertFalse(((String) summary).isBlank(), "summary 不得为空字符串");
        // summary 应包含总费用信息
        assertTrue(((String) summary).contains("总费用"), "summary 应描述总费用");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("HITL 触发时 summary 含介入原因与调整建议")
    void summaryContainsHitlReasonWhenTriggered() {
        // 预算很低以触发 HITL（overrunRate >= 15%）
        TripPlanRequest request = new TripPlanRequest(
                "成都", START, 1, new BigDecimal("100"), 2,
                TravelStyle.FAMILY, List.of());
        ItineraryDesignResult itinerary = new ItineraryDesignResult(
                List.of(tripDay()), List.of(), false, null, null);

        BudgetCalculationTools tools = new BudgetCalculationTools(service);
        String json = tools.calculateBudget(
                JsonUtils.toJson(request), JsonUtils.toJson(itinerary));

        Map<String, Object> payload = JsonUtils.fromJson(json, Map.class);
        assertTrue((Boolean) payload.get("requiresHITL"), "低预算应触发 HITL");

        String summary = (String) payload.get("summary");
        assertNotNull(summary, "HITL 场景下 summary 也必须存在");
        assertTrue(summary.contains("人工介入"), "HITL summary 应提及人工介入");
    }
}
