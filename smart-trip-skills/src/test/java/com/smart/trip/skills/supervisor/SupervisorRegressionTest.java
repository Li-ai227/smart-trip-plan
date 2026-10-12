package com.smart.trip.skills.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.PlanStatus;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.xhs.AttractionCandidate;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Supervisor 回归测试（C-010 v2）：编排顺序、PlanStatus 状态机流转、
 * Agent 降级聚合、Gate HITL 判定纳入回归。
 *
 * <p>这些测试保证后续重构不破坏核心业务契约。</p>
 */
class SupervisorRegressionTest {

    private static final GeoLocation LOC = new GeoLocation(116.4, 39.9, "北京", "北京");

    private static TripPlanRequest request(int days, String budget) {
        return new TripPlanRequest("北京", LocalDate.of(2026, 7, 1), days,
                new BigDecimal(budget), 2, TravelStyle.COUPLE, List.of("古迹"));
    }

    private static Attraction attraction(String id, double rating) {
        return new Attraction(id, "景点" + id, LOC, rating, 120,
                new BigDecimal("50"), List.of("古迹"));
    }

    private static Meal meal(MealType type) {
        return new Meal("餐厅", LOC, type, new BigDecimal("50"), List.of());
    }

    private static TripDay tripDay(int index) {
        return new TripDay(LocalDate.of(2026, 7, index), index,
                List.of(attraction("A-" + index, 4.5)),
                List.of(meal(MealType.LUNCH), meal(MealType.DINNER)),
                List.of(), new BigDecimal("500"));
    }

    private static XHSAnalysisResult xhsResult() {
        return new XHSAnalysisResult(
                List.of(new AttractionCandidate(attraction("A-1", 4.5), 10, 8, 1, 1),
                        new AttractionCandidate(attraction("A-2", 4.5), 8, 6, 1, 1)),
                false, null, null);
    }

    private static RoutePlanResult routeResult() {
        return new RoutePlanResult(List.of(), List.of(), List.of(), false, null, null);
    }

    private static ItineraryDesignResult itineraryResult(int days) {
        List<TripDay> dayList = new ArrayList<>();
        for (int i = 1; i <= days; i++) {
            dayList.add(tripDay(i));
        }
        return new ItineraryDesignResult(dayList, List.of(), false, null, null);
    }

    private static BudgetCalculationResult budgetResult(String totalCost, boolean hitl) {
        BigDecimal total = new BigDecimal(totalCost);
        BigDecimal overrun = hitl ? new BigDecimal("0.20") : new BigDecimal("-0.02");
        return new BudgetCalculationResult(
                total, new BigDecimal("200"), new BigDecimal("400"),
                new BigDecimal("200"), new BigDecimal("100"),
                overrun, List.of(), hitl,
                hitl ? "超支20%" : null, hitl ? List.of("降档住宿") : List.of(),
                false, null);
    }

    private static SubAgentDispatcher stubDispatcher(
            XHSAnalysisResult xhs, RoutePlanResult route,
            ItineraryDesignResult itinerary, BudgetCalculationResult budget) {
        return (agentUri, assign) -> {
            Object result = switch (agentUri) {
                case "agent://xhs-analyst" -> xhs;
                case "agent://route-planner" -> route;
                case "agent://itinerary-designer" -> itinerary;
                case "agent://budget-controller" -> budget;
                default -> null;
            };
            return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, false);
        };
    }

    // ================================================================
    // 编排顺序回归
    // ================================================================

    @Nested
    @DisplayName("编排顺序回归：XHS→Route→Itinerary→Budget")
    class OrchestrationOrderRegression {

        @Test
        @DisplayName("标准 2 日游 → 严格 XHS→Route→Itinerary→Budget 顺序")
        void standardTwoDayOrder() {
            List<String> callOrder = new ArrayList<>();
            SubAgentDispatcher orderTracking = (agentUri, assign) -> {
                callOrder.add(agentUri);
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    orderTracking, new OutputQualityGate());

            service.orchestrate(new TripOrchestrationQuery(request(2, "5000")));

            assertEquals(List.of(
                    "agent://xhs-analyst",
                    "agent://route-planner",
                    "agent://itinerary-designer",
                    "agent://budget-controller"
            ), callOrder);
        }

        @Test
        @DisplayName("中间步降级不跳过后续步 → 4 步全部执行")
        void degradationDoesNotSkipSteps() {
            List<String> callOrder = new ArrayList<>();
            SubAgentDispatcher orderTracking = (agentUri, assign) -> {
                callOrder.add(agentUri);
                if ("agent://route-planner".equals(agentUri)) {
                    return Msg.taskError(agentUri, assign.from(), assign.traceId(),
                            "AGENT_FAILURE", "Route 降级");
                }
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    orderTracking, new OutputQualityGate());

            service.orchestrate(new TripOrchestrationQuery(request(2, "5000")));

            // 即使 Route 失败，Itinerary 和 Budget 仍执行
            assertEquals(4, callOrder.size());
        }
    }

    // ================================================================
    // PlanStatus 状态机回归
    // ================================================================

    @Nested
    @DisplayName("PlanStatus 状态机回归")
    class PlanStatusRegression {

        @Test
        @DisplayName("确定性编排产出 TripPlan.status == REVIEW（确定性编排直接进 REVIEW）")
        void deterministicPathProducesReviewStatus() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            assertEquals(PlanStatus.REVIEW, result.plan().status(),
                    "确定性编排固定产出 REVIEW 状态");
        }

        @Test
        @DisplayName("HITL 触发时 → requiresHITL=true + status=REVIEW")
        void hitlTriggeredStatus() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(1), // 1天 vs 请求3天
                    budgetResult("6000", true)); // 超预算
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(3, "5000")));

            assertEquals(PlanStatus.REVIEW, result.plan().status());
            assertTrue(result.requiresHITL());
        }

        @Test
        @DisplayName("无 HITL 触发时 → requiresHITL=false + status=REVIEW")
        void noHitlStatus() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            assertEquals(PlanStatus.REVIEW, result.plan().status());
            assertFalse(result.requiresHITL());
        }
    }

    // ================================================================
    // Agent 降级聚合回归
    // ================================================================

    @Nested
    @DisplayName("Agent 降级聚合回归")
    class DegradationAggregationRegression {

        @Test
        @DisplayName("fallbackCount 精确计数：3 降级 + 1 正常 = 3")
        void preciseFallbackCount() {
            SubAgentDispatcher dispatcher = (agentUri, assign) -> {
                boolean isFallback = !"agent://budget-controller".equals(agentUri);
                Object result = switch (agentUri) {
                    case "agent://xhs-analyst" -> new XHSAnalysisResult(
                            xhsResult().candidates(), true, "XHS 降级", null);
                    case "agent://route-planner" -> new RoutePlanResult(
                            List.of(), List.of(), List.of(), true, "路线降级", null);
                    case "agent://itinerary-designer" -> new ItineraryDesignResult(
                            itineraryResult(2).days(), List.of(), true, "行程降级", null);
                    case "agent://budget-controller" -> budgetResult("900", false);
                    default -> null;
                };
                return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, isFallback);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            assertEquals(3, result.telemetry().subAgentFallbackCount());
            assertTrue(result.fallback());
        }

        @Test
        @DisplayName("全正常 → fallback=false + subAgentFallbackCount=0")
        void allNormal() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            assertFalse(result.fallback());
            assertEquals(0, result.telemetry().subAgentFallbackCount());
        }
    }

    // ================================================================
    // Gate HITL 判定回归
    // ================================================================

    @Nested
    @DisplayName("Gate HITL 判定回归")
    class GateHITLRegression {

        @Test
        @DisplayName("预算刚好在红线（totalCost == budget × 1.15）→ 不触发 BUDGET_OVERRUN")
        void budgetExactlyAtCeiling() {
            // budget=5000, 红线=5750, totalCost=5750 → 不超（≤）
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2),
                    budgetResult("5750", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            // 刚好在红线：不触发超预算
            boolean hasBudgetViolation = result.hitlReasons().stream()
                    .anyMatch(r -> r.contains("超预算"));
            assertFalse(hasBudgetViolation,
                    "totalCost=5750 ≤ budget×1.15=5750，不应触发 BUDGET_OVERRUN");
        }

        @Test
        @DisplayName("预算超红线（totalCost == budget × 1.15 + 1，即 5751）→ 触发 BUDGET_OVERRUN")
        void budgetOverCeiling() {
            // budget=5000, 红线=5750, totalCost=5751 → 超红线
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2),
                    budgetResult("5751", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            boolean hasBudgetViolation = result.hitlReasons().stream()
                    .anyMatch(r -> r.contains("超预算"));
            assertTrue(hasBudgetViolation,
                    "totalCost=5751 > budget×1.15=5750，应触发 BUDGET_OVERRUN，实际 hitlReasons="
                            + result.hitlReasons());
        }

        @Test
        @DisplayName("所有景点评分 ≥3.5 → 不触发 LOW_RATING")
        void allHighRatings() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            boolean hasRatingViolation = result.hitlReasons().stream()
                    .anyMatch(r -> r.contains("评分"));
            assertFalse(hasRatingViolation);
        }

        @Test
        @DisplayName("每日含午晚餐 → 不触发 MISSING_MEAL")
        void allMealsPresent() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            boolean hasMealViolation = result.hitlReasons().stream()
                    .anyMatch(r -> r.contains("午餐") || r.contains("晚餐"));
            assertFalse(hasMealViolation);
        }
    }

    // ================================================================
    // 端到端回归：编排 + 降级 + HITL 全链路
    // ================================================================

    @Nested
    @DisplayName("端到端回归：编排 + 降级 + HITL 全链路")
    class EndToEndRegression {

        @Test
        @DisplayName("混合场景：XHS 正常 + Route 降级 + Itinerary 正常 + Budget 超支 HITL → "
                + "requiresHITL=true（超预算触发）")
        void mixedScenario() {
            SubAgentDispatcher dispatcher = (agentUri, assign) -> {
                Object result = switch (agentUri) {
                    case "agent://xhs-analyst" -> xhsResult();
                    case "agent://route-planner" -> new RoutePlanResult(
                            List.of(), List.of(), List.of(), true, "地图 API 降级", null);
                    case "agent://itinerary-designer" -> itineraryResult(2);
                    case "agent://budget-controller" -> budgetResult("6000", true);
                    default -> null;
                };
                boolean isFallback = "agent://route-planner".equals(agentUri);
                return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, isFallback);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            assertNotNull(result.plan());
            // 预算超支 → requiresHITL=true
            assertTrue(result.requiresHITL());
            // HITL 原因包含超预算（Gate 中文消息）
            assertTrue(result.hitlReasons().stream()
                    .anyMatch(r -> r.contains("超预算")),
                    "应包含超预算违规，实际 hitlReasons=" + result.hitlReasons());
        }
    }
}
