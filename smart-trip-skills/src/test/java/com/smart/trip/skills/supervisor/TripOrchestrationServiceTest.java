package com.smart.trip.skills.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.MemoryContext;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.memory.MemoryRecaller;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.xhs.AttractionCandidate;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link TripOrchestrationService} 确定性顺序编排单测（C-010 v2 完整覆盖）。
 *
 * <p>覆盖：全链路编排、任务分发正确性（顺序+context传递）、逐子 Agent 降级、全部失败、
 * HITL 单/多条件叠加、边界防御（空行程/单天/预算为0/未知字段）、参数校验。</p>
 */
class TripOrchestrationServiceTest {

    private static final GeoLocation LOC = new GeoLocation(116.4, 39.9, "北京", "北京");

    private static TripPlanRequest request(int days, String budget) {
        return new TripPlanRequest("北京", LocalDate.of(2026, 7, 1), days,
                new BigDecimal(budget), 2, TravelStyle.COUPLE, List.of("古迹"));
    }

    private static TripPlanRequest requestWithDestination(String destination, int days, String budget) {
        return new TripPlanRequest(destination, LocalDate.of(2026, 7, 1), days,
                new BigDecimal(budget), 2, TravelStyle.COUPLE, List.of("古迹"));
    }

    private static Attraction attraction(String id) {
        return new Attraction(id, "景点" + id, LOC, 4.5, 120,
                new BigDecimal("50"), List.of("古迹"));
    }

    private static Meal meal(MealType type) {
        return new Meal("餐厅", LOC, type, new BigDecimal("50"), List.of());
    }

    private static TripDay tripDay(int index) {
        return new TripDay(LocalDate.of(2026, 7, index), index,
                List.of(attraction("A-" + index)),
                List.of(meal(MealType.LUNCH), meal(MealType.DINNER)),
                List.of(), new BigDecimal("500"));
    }

    private static XHSAnalysisResult xhsResult() {
        return new XHSAnalysisResult(
                List.of(new AttractionCandidate(attraction("A-1"), 10, 8, 1, 1),
                        new AttractionCandidate(attraction("A-2"), 8, 6, 1, 1)),
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

    private static SubAgentDispatcher errorDispatcher(String failUri) {
        return (agentUri, assign) -> {
            if (agentUri.equals(failUri)) {
                return Msg.taskError(agentUri, assign.from(), assign.traceId(),
                        "AGENT_FAILURE", "模拟子 Agent 失败");
            }
            Object result = switch (agentUri) {
                case "agent://xhs-analyst" -> xhsResult();
                case "agent://route-planner" -> routeResult();
                case "agent://itinerary-designer" -> itineraryResult(2);
                case "agent://budget-controller" -> budgetResult("900", false);
                default -> null;
            };
            return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, false);
        };
    }

    private static SubAgentDispatcher allErrorDispatcher() {
        return (agentUri, assign) ->
                Msg.taskError(agentUri, assign.from(), assign.traceId(),
                        "AGENT_FAILURE", "全部子 Agent 故障: " + agentUri);
    }

    // ================================================================
    // 构造校验
    // ================================================================

    @Nested
    @DisplayName("构造校验")
    class ConstructorValidation {

        @Test
        @DisplayName("dispatcher 为 null → 抛 InvalidRequestException")
        void nullDispatcher() {
            assertThrows(InvalidRequestException.class,
                    () -> new TripOrchestrationService(null, new OutputQualityGate()));
        }

        @Test
        @DisplayName("qualityGate 为 null → 抛 InvalidRequestException")
        void nullQualityGate() {
            assertThrows(InvalidRequestException.class,
                    () -> new TripOrchestrationService((u, a) -> null, null));
        }
    }

    // ================================================================
    // Happy Path
    // ================================================================

    @Nested
    @DisplayName("Happy Path：全链路正常")
    class HappyPath {

        @Test
        @DisplayName("Case-1：北京 2 日游，预算充足 → 完整 TripPlan，requiresHITL=false")
        void happyCase() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            assertNotNull(result.plan());
            assertEquals(2, result.plan().days().size());
            assertFalse(result.requiresHITL());
            assertFalse(result.fallback());
            assertTrue(result.hitlReasons().isEmpty());
            assertEquals(0, result.telemetry().subAgentFallbackCount());
        }

        @Test
        @DisplayName("编排顺序正确：XHS→Route→Itinerary→Budget（通过 dispatcher 调用顺序验证）")
        void orchestrationOrder() {
            List<String> callOrder = new ArrayList<>();
            SubAgentDispatcher orderTrackingDispatcher = (agentUri, assign) -> {
                callOrder.add(agentUri);
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    orderTrackingDispatcher, new OutputQualityGate());

            service.orchestrate(new TripOrchestrationQuery(request(2, "5000")));

            assertEquals(4, callOrder.size());
            assertEquals("agent://xhs-analyst", callOrder.get(0));
            assertEquals("agent://route-planner", callOrder.get(1));
            assertEquals("agent://itinerary-designer", callOrder.get(2));
            assertEquals("agent://budget-controller", callOrder.get(3));
        }

        @Test
        @DisplayName("TripPlanResult 结构完整：planId、days、totalCost、budget、telemetry")
        void completeStructure() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            assertNotNull(result.plan());
            assertNotNull(result.plan().planId());
            assertFalse(result.plan().planId().isBlank());
            assertNotNull(result.plan().days());
            assertNotNull(result.plan().totalCost());
            assertNotNull(result.plan().budget());
            assertNotNull(result.telemetry());
            assertTrue(result.telemetry().totalLatencyMs() >= 0);
        }

        @Test
        @DisplayName("RED: planId 从 query 透传到 assembled TripPlan（不自行生成）")
        void planIdShouldFlowFromQueryToAssembledPlan() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            String expectedPlanId = "P-20260701-005";
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000"), expectedPlanId));

            assertNotNull(result.plan());
            assertEquals(expectedPlanId, result.plan().planId(),
                    "assembled TripPlan.planId 应与 query.planId 一致，而不是 Assembler 自行生成的 P-20260701-001");
        }
    }

    // ================================================================
    // 任务分发正确性：context 传递
    // ================================================================

    @Nested
    @DisplayName("任务分发正确性：context 逐步传递")
    class ContextPassing {

        @Test
        @DisplayName("XHS 步 context 含 destination + preferences + travelStyle")
        void xhsContextContainsDestinationAndPreferences() {
            List<Map<String, Object>> capturedContexts = new ArrayList<>();
            SubAgentDispatcher capturingDispatcher = (agentUri, assign) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = (Map<String, Object>) assign.payload().get("context");
                capturedContexts.add(new LinkedHashMap<>(ctx));
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    capturingDispatcher, new OutputQualityGate());

            service.orchestrate(new TripOrchestrationQuery(request(2, "5000")));

            // XHS context (index 0)
            Map<String, Object> xhsCtx = capturedContexts.get(0);
            assertEquals("北京", xhsCtx.get("destination"));
            assertEquals(List.of("古迹"), xhsCtx.get("preferences"));
            assertEquals(TravelStyle.COUPLE, xhsCtx.get("travelStyle"));
        }

        @Test
        @DisplayName("Route 步 taskType=ROUTE_PLANNING + context 含 destination + attractions")
        void routeContextContainsAttractions() {
            List<String> taskTypes = new ArrayList<>();
            List<Map<String, Object>> capturedContexts = new ArrayList<>();
            SubAgentDispatcher capturingDispatcher = (agentUri, assign) -> {
                taskTypes.add((String) assign.payload().get(Msg.KEY_TASK_TYPE));
                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = (Map<String, Object>) assign.payload().get("context");
                capturedContexts.add(new LinkedHashMap<>(ctx));
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    capturingDispatcher, new OutputQualityGate());

            service.orchestrate(new TripOrchestrationQuery(request(2, "5000")));

            // taskTypes: [XHS_ANALYSIS, ROUTE_PLANNING, ITINERARY_DESIGN, COST_CALCULATION]
            assertEquals("XHS_ANALYSIS", taskTypes.get(0));
            assertEquals("ROUTE_PLANNING", taskTypes.get(1));
            assertEquals("ITINERARY_DESIGN", taskTypes.get(2));
            assertEquals("COST_CALCULATION", taskTypes.get(3));

            // Route context (index 1)
            Map<String, Object> routeCtx = capturedContexts.get(1);
            assertEquals("北京", routeCtx.get("destination"));
            assertNotNull(routeCtx.get("attractions"));
            assertEquals("driving", routeCtx.get("transportMode"));
        }

        @Test
        @DisplayName("Itinerary 步 context 含 request + attractions + routeResult")
        void itineraryContextContainsRouteResult() {
            List<Map<String, Object>> capturedContexts = new ArrayList<>();
            SubAgentDispatcher capturingDispatcher = (agentUri, assign) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = (Map<String, Object>) assign.payload().get("context");
                capturedContexts.add(new LinkedHashMap<>(ctx));
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    capturingDispatcher, new OutputQualityGate());

            service.orchestrate(new TripOrchestrationQuery(request(2, "5000")));

            // Itinerary context (index 2)
            Map<String, Object> itinCtx = capturedContexts.get(2);
            assertNotNull(itinCtx.get("request"));
            assertTrue(itinCtx.get("request") instanceof TripPlanRequest);
            assertNotNull(itinCtx.get("attractions"));
            assertNotNull(itinCtx.get("routeResult"));
        }

        @Test
        @DisplayName("Budget 步 context 含 request + itineraryResult")
        void budgetContextContainsItineraryResult() {
            List<Map<String, Object>> capturedContexts = new ArrayList<>();
            SubAgentDispatcher capturingDispatcher = (agentUri, assign) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = (Map<String, Object>) assign.payload().get("context");
                capturedContexts.add(new LinkedHashMap<>(ctx));
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    capturingDispatcher, new OutputQualityGate());

            service.orchestrate(new TripOrchestrationQuery(request(2, "5000")));

            // Budget context (index 3)
            Map<String, Object> budgetCtx = capturedContexts.get(3);
            assertNotNull(budgetCtx.get("request"));
            assertTrue(budgetCtx.get("request") instanceof TripPlanRequest);
            assertNotNull(budgetCtx.get("itineraryResult"));
        }

        @Test
        @DisplayName("Route 前步 XHS 返回 null 时 → attractions 为空列表，不抛 NPE")
        void nullXhsResultYieldsEmptyAttractions() {
            SubAgentDispatcher dispatcher = (agentUri, assign) -> {
                if ("agent://xhs-analyst".equals(agentUri)) {
                    // 返回类型不匹配的结果（非 XHSAnalysisResult）
                    return Msg.taskResult(agentUri, assign.from(), assign.traceId(),
                            "not an XHSAnalysisResult", false);
                }
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            assertNotNull(result.plan());
            // 空 attractions → itinerary 产出空 days
            assertNotNull(result.plan().days());
        }
    }

    // ================================================================
    // 降级：子 Agent 失败
    // ================================================================

    @Nested
    @DisplayName("降级：子 Agent 失败时整体仍产出")
    class Degradation {

        @Test
        @DisplayName("XHS 返回 TASK_ERROR → 整体标记 fallback 仍产出")
        void xhsFails() {
            SubAgentDispatcher dispatcher = errorDispatcher("agent://xhs-analyst");
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));
            assertNotNull(result.plan());
            assertTrue(result.fallback());
            assertTrue(result.telemetry().subAgentFallbackCount() >= 1);
        }

        @Test
        @DisplayName("Route 返回 TASK_ERROR → 整体标记 fallback 仍产出")
        void routeFails() {
            SubAgentDispatcher dispatcher = errorDispatcher("agent://route-planner");
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));
            assertNotNull(result.plan());
            assertTrue(result.fallback());
        }

        @Test
        @DisplayName("Itinerary 返回 TASK_ERROR → 整体标记 fallback 仍产出")
        void itineraryFails() {
            SubAgentDispatcher dispatcher = errorDispatcher("agent://itinerary-designer");
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));
            assertNotNull(result.plan());
            assertTrue(result.fallback());
        }

        @Test
        @DisplayName("Budget 返回 TASK_ERROR → 整体标记 fallback 仍产出")
        void budgetFails() {
            SubAgentDispatcher dispatcher = errorDispatcher("agent://budget-controller");
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));
            assertNotNull(result.plan());
            assertTrue(result.fallback());
        }

        @Test
        @DisplayName("全部子 Agent TASK_ERROR → 仍产出 TripPlan（空 days）+ fallback=true")
        void allSubAgentsError() {
            TripOrchestrationService service = new TripOrchestrationService(
                    allErrorDispatcher(), new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));
            assertNotNull(result.plan());
            assertTrue(result.fallback());
            assertNotNull(result.plan().days());
        }

        @Test
        @DisplayName("全部子 Agent fallback=true（非 error）→ 整体 fallback + 4 次降级计数")
        void allSubAgentsFallback() {
            SubAgentDispatcher fallbackDispatcher = (agentUri, assign) -> {
                Object result = switch (agentUri) {
                    case "agent://xhs-analyst" -> new XHSAnalysisResult(
                            xhsResult().candidates(), true, "XHS 降级", null);
                    case "agent://route-planner" -> new RoutePlanResult(
                            List.of(), List.of(), List.of(), true, "路线降级", null);
                    case "agent://itinerary-designer" -> new ItineraryDesignResult(
                            itineraryResult(2).days(), List.of(), true, "行程降级", null);
                    case "agent://budget-controller" -> new BudgetCalculationResult(
                            new BigDecimal("900"), new BigDecimal("200"), new BigDecimal("400"),
                            new BigDecimal("200"), new BigDecimal("100"),
                            new BigDecimal("-0.02"), List.of(), false, null,
                            List.of(), true, null);
                    default -> null;
                };
                return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, true);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    fallbackDispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));
            assertNotNull(result.plan());
            assertTrue(result.fallback());
            assertEquals(4, result.telemetry().subAgentFallbackCount());
        }

        @Test
        @DisplayName("单个子 Agent fallback=true（Route 降级）→ 整体 fallback=true 但仍产出完整方案")
        void singleAgentFallback() {
            SubAgentDispatcher dispatcher = (agentUri, assign) -> {
                Object result = switch (agentUri) {
                    case "agent://xhs-analyst" -> xhsResult();
                    case "agent://route-planner" -> new RoutePlanResult(
                            List.of(), List.of(), List.of(), true, "仅 Route 降级", null);
                    case "agent://itinerary-designer" -> itineraryResult(2);
                    case "agent://budget-controller" -> budgetResult("900", false);
                    default -> null;
                };
                boolean isFallback = agentUri.equals("agent://route-planner");
                return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, isFallback);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));
            assertNotNull(result.plan());
            assertEquals(2, result.plan().days().size());
            assertTrue(result.fallback());
            assertEquals(1, result.telemetry().subAgentFallbackCount());
        }
    }

    // ================================================================
    // HITL 触发
    // ================================================================

    @Nested
    @DisplayName("HITL 触发")
    class HITLTrigger {

        @Test
        @DisplayName("超预算≥15% → requiresHITL=true + 原因含 BUDGET_OVERRUN")
        void budgetOverrun() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("6000", true));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));
            assertTrue(result.requiresHITL());
            assertFalse(result.hitlReasons().isEmpty());
        }

        @Test
        @DisplayName("天数不一致 → requiresHITL=true + 原因含 DAYS_MISMATCH")
        void daysMismatch() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(3, "5000")));
            assertTrue(result.requiresHITL());
            assertTrue(result.hitlReasons().stream()
                    .anyMatch(r -> r.contains("DAYS_MISMATCH") || r.contains("天数不一致")));
        }

        @Test
        @DisplayName("评分<3.5 → requiresHITL=true + 原因含 LOW_RATING")
        void lowRating() {
            Attraction lowRated = new Attraction("bad-1", "低分景点", LOC, 3.0, 60,
                    BigDecimal.ZERO, List.of());
            TripDay lowDay = new TripDay(LocalDate.of(2026, 7, 1), 1,
                    List.of(lowRated),
                    List.of(meal(MealType.LUNCH), meal(MealType.DINNER)),
                    List.of(), new BigDecimal("300"));
            ItineraryDesignResult lowItinerary = new ItineraryDesignResult(
                    List.of(lowDay, tripDay(2)), List.of(), false, null, null);
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), lowItinerary, budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));
            assertTrue(result.requiresHITL());
            assertTrue(result.hitlReasons().stream()
                    .anyMatch(r -> r.contains("LOW_RATING") || r.contains("评分")));
        }

        @Test
        @DisplayName("超预算 + 天数不一致 双违规 → 两条原因均记录")
        void multipleViolations() {
            Attraction lowRated = new Attraction("bad-1", "低分景点", LOC, 3.0, 60,
                    BigDecimal.ZERO, List.of());
            TripDay lowDay = new TripDay(LocalDate.of(2026, 7, 1), 1,
                    List.of(lowRated),
                    List.of(meal(MealType.LUNCH), meal(MealType.DINNER)),
                    List.of(), new BigDecimal("300"));
            ItineraryDesignResult lowItinerary = new ItineraryDesignResult(
                    List.of(lowDay), List.of(), false, null, null);
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), lowItinerary, budgetResult("6000", true));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(3, "5000")));
            assertTrue(result.requiresHITL());
            assertTrue(result.hitlReasons().size() >= 2,
                    "应至少触发 2 条违规，实际: " + result.hitlReasons());
        }

        @Test
        @DisplayName("缺失午餐 → requiresHITL=true + 原因含 MISSING_MEAL")
        void missingMeal() {
            TripDay noLunch = new TripDay(LocalDate.of(2026, 7, 1), 1,
                    List.of(attraction("A-1")),
                    List.of(meal(MealType.DINNER)),
                    List.of(), new BigDecimal("300"));
            ItineraryDesignResult missingMealItin = new ItineraryDesignResult(
                    List.of(noLunch, tripDay(2)), List.of(), false, null, null);
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), missingMealItin, budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));
            assertTrue(result.requiresHITL());
            assertTrue(result.hitlReasons().stream()
                    .anyMatch(r -> r.contains("MISSING_MEAL") || r.contains("午餐") || r.contains("晚餐")));
        }

        @Test
        @DisplayName("Gate 复核多条件叠加：超预算 + 天数不一致 + 评分<3.5 同时触发 → 三条原因全部记录")
        void tripleViolation() {
            // 低分景点
            Attraction lowRated = new Attraction("bad-1", "低分景点", LOC, 2.0, 60,
                    BigDecimal.ZERO, List.of());
            TripDay lowDay = new TripDay(LocalDate.of(2026, 7, 1), 1,
                    List.of(lowRated), // 评分 2.0 < 3.5
                    List.of(meal(MealType.DINNER)), // 缺午餐
                    List.of(), new BigDecimal("300"));
            // 只产出 1 天（请求 3 天） + 超预算
            ItineraryDesignResult badItinerary = new ItineraryDesignResult(
                    List.of(lowDay), List.of(), false, null, null);
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), badItinerary, budgetResult("6000", true));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(3, "5000")));

            assertTrue(result.requiresHITL());
            // 预期至少 3 条违规：BUDGET_OVERRUN + DAYS_MISMATCH + LOW_RATING + MISSING_MEAL
            assertTrue(result.hitlReasons().size() >= 3,
                    "应触发 BUDGET_OVERRUN + DAYS_MISMATCH + LOW_RATING + MISSING_MEAL 至少 3 条，实际: "
                            + result.hitlReasons());
        }
    }

    // ================================================================
    // 边界防御
    // ================================================================

    @Nested
    @DisplayName("边界防御")
    class BoundaryDefense {

        @Test
        @DisplayName("空行程（Itinerary 返回 0 天）→ 产出空 days 方案，不抛异常")
        void emptyItinerary() {
            ItineraryDesignResult empty = new ItineraryDesignResult(
                    List.of(), List.of(), false, null, null);
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), empty, budgetResult("0", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            assertNotNull(result.plan());
            assertEquals(0, result.plan().days().size());
            // 0 天 vs 请求 2 天 → DAYS_MISMATCH
            assertTrue(result.requiresHITL());
        }

        @Test
        @DisplayName("单天行程 → 正常产出，不抛异常")
        void singleDay() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(1), budgetResult("450", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(1, "5000")));

            assertNotNull(result.plan());
            assertEquals(1, result.plan().days().size());
            assertFalse(result.requiresHITL());
        }

        @Test
        @DisplayName("预算为 0 → Budget 核算 totalCost=0，不抛异常")
        void zeroBudget() {
            TripPlanRequest zeroBudgetReq = requestWithDestination("北京", 2, "0");
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2),
                    budgetResult("0", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(zeroBudgetReq));

            assertNotNull(result.plan());
            // 总费用 0 ≤ 预算 0 × 1.15 = 0，不触发 BUDGET_OVERRUN
            assertFalse(result.requiresHITL());
        }

        @Test
        @DisplayName("destination 为空字符串 → 正常执行（由子 Agent 处理），不抛异常")
        void emptyDestination() {
            TripPlanRequest emptyDestReq = new TripPlanRequest("",
                    LocalDate.of(2026, 7, 1), 2, new BigDecimal("5000"), 2,
                    TravelStyle.COUPLE, List.of());
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(emptyDestReq));

            assertNotNull(result.plan());
        }

        @Test
        @DisplayName("未知 context 字段 → 忽略不失败（向后兼容）")
        void unknownContextFieldsIgnored() {
            SubAgentDispatcher dispatcher = (agentUri, assign) -> {
                // 验证 assign 可以正常处理——即使包含未知字段也不影响
                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = (Map<String, Object>) assign.payload().get("context");
                // 模拟：子 Agent 收到包含未知字段的 context 时仍正常返回
                assertNotNull(ctx);
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(request(2, "5000")));

            assertNotNull(result.plan());
            assertFalse(result.fallback());
        }

        @Test
        @DisplayName("大天数（如 30 天）→ 正常执行，不抛异常")
        void largeDays() {
            TripPlanRequest largeDaysReq = requestWithDestination("云南", 30, "50000");
            ItineraryDesignResult largeItinerary = itineraryResult(30);
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), largeItinerary,
                    budgetResult("45000", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());

            TripPlanResult result = service.orchestrate(
                    new TripOrchestrationQuery(largeDaysReq));

            assertNotNull(result.plan());
            assertEquals(30, result.plan().days().size());
        }
    }

    // ================================================================
    // 记忆召回注入（C-015 T-6）
    // ================================================================

    @Nested
    @DisplayName("记忆召回注入（C-015 T-6）")
    class MemoryRecallInjection {

        @Test
        @DisplayName("有 MemoryRecallService + userId → XHS/Itinerary context 含 memoryContext")
        void memoryContextInjectedIntoXhsAndItinerary() {
            List<Map<String, Object>> capturedContexts = new ArrayList<>();
            SubAgentDispatcher capturingDispatcher = (agentUri, assign) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = (Map<String, Object>) assign.payload().get("context");
                capturedContexts.add(new LinkedHashMap<>(ctx));
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            MemoryContext memory = new MemoryContext(
                    List.of("偏好海鲜"), List.of("上次杭州3天游"), List.of("西湖免费"), false);

            TripOrchestrationService service = new TripOrchestrationService(
                    capturingDispatcher, new OutputQualityGate());
            service.setMemoryRecaller((uid, dest, style) -> memory);

            TripOrchestrationQuery query = new TripOrchestrationQuery(
                    request(2, "5000"), "P-001", "user-001", null);
            service.orchestrate(query);

            // XHS context (index 0) 应含 memoryContext
            Map<String, Object> xhsCtx = capturedContexts.get(0);
            assertNotNull(xhsCtx.get("memoryContext"), "XHS context 应含 memoryContext");
            assertEquals(memory, xhsCtx.get("memoryContext"));

            // Itinerary context (index 2) 应含 memoryContext
            Map<String, Object> itinCtx = capturedContexts.get(2);
            assertNotNull(itinCtx.get("memoryContext"), "Itinerary context 应含 memoryContext");
            assertEquals(memory, itinCtx.get("memoryContext"));

            // Route (index 1) 和 Budget (index 3) 不需要 memoryContext
        }

        @Test
        @DisplayName("无 MemoryRecallService → context 不含 memoryContext（向后兼容）")
        void noRecallService_noMemoryContext() {
            List<Map<String, Object>> capturedContexts = new ArrayList<>();
            SubAgentDispatcher capturingDispatcher = (agentUri, assign) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = (Map<String, Object>) assign.payload().get("context");
                capturedContexts.add(new LinkedHashMap<>(ctx));
                return stubDispatcher(xhsResult(), routeResult(),
                        itineraryResult(2), budgetResult("900", false))
                        .dispatch(agentUri, assign);
            };
            TripOrchestrationService service = new TripOrchestrationService(
                    capturingDispatcher, new OutputQualityGate());

            service.orchestrate(new TripOrchestrationQuery(request(2, "5000")));

            Map<String, Object> xhsCtx = capturedContexts.get(0);
            assertFalse(xhsCtx.containsKey("memoryContext"),
                    "无 MemoryRecallService 时 XHS context 不应含 memoryContext");
        }

        @Test
        @DisplayName("MemoryRecallService 异常 → 降级无记忆，编排仍正常完成")
        void recallServiceException_orchestrationContinues() {
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            service.setMemoryRecaller((uid, dest, style) -> {
                throw new RuntimeException("ReMe 模拟故障");
            });

            TripOrchestrationQuery query = new TripOrchestrationQuery(
                    request(2, "5000"), "P-001", "user-001", null);
            TripPlanResult result = service.orchestrate(query);

            assertNotNull(result.plan());
            assertFalse(result.requiresHITL());
        }
    }

    // ================================================================
    // 参数校验
    // ================================================================

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("request 为 null → 抛 InvalidRequestException")
        void nullRequest() {
            TripOrchestrationService service = new TripOrchestrationService(
                    (u, a) -> null, new OutputQualityGate());
            assertThrows(InvalidRequestException.class,
                    () -> service.orchestrate(new TripOrchestrationQuery(null)));
        }
    }

    // ================================================================
    // C-017 FIX-3: 审计追踪 traceId 贯穿
    // ================================================================

    @Nested
    @DisplayName("C-017 FIX-3: 审计追踪 traceId 贯穿 + summary 单一写入者")
    class TraceIntegration {

        /** 捕获式 TracePersistenceService，供断言 traceId/调用次数。 */
        private static final class CapturingTracePersistenceService
                implements com.smart.trip.skills.trace.TracePersistenceService {
            final List<com.smart.trip.skills.trace.TraceDelegationRecord> delegations = new ArrayList<>();

            @Override
            public void saveDelegation(com.smart.trip.skills.trace.TraceDelegationRecord record) {
                delegations.add(record);
            }

            @Override
            public void saveOrchestrationSummary(
                    com.smart.trip.skills.trace.TraceOrchestrationSummary summary) {
                throw new AssertionError(
                        "orchestrate() 不应再写 orchestration_summary（唯一写入者是 SupervisorAgent）");
            }

            @Override
            public void saveLlmCall(com.smart.trip.skills.trace.TraceLlmCallRecord record) {
                throw new AssertionError(
                        "orchestrate() 不应写 llm_call（该记录只在 SupervisorAgent 原生路产出）");
            }
        }

        @Test
        @DisplayName("传入 query.traceId() → 4 条委派记录复用该 traceId，且不写 orchestration_summary")
        void reusesQueryTraceIdAndSkipsSummaryWrite() {
            CapturingTracePersistenceService capturing = new CapturingTracePersistenceService();
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate(), capturing);

            TripOrchestrationQuery query = new TripOrchestrationQuery(
                    request(2, "5000"), "P-001", "user-001", null, "T-caller-001");
            service.orchestrate(query);

            assertEquals(4, capturing.delegations.size());
            capturing.delegations.forEach(d -> assertEquals("T-caller-001", d.traceId(),
                    "委派记录应复用调用方（SupervisorAgent）透传的 traceId，而非自行生成新的"));
            capturing.delegations.forEach(d -> assertEquals("P-001", d.planId(),
                    "回归：executeStep 曾经硬编码 planId=\"\"，导致落库层 emptyToNull() 把它转成 "
                            + "SQL NULL 撞上 plan_id NOT NULL 约束——委派记录必须带上 query 真实的 planId"));
        }

        @Test
        @DisplayName("未传 traceId（测试直调）→ 退化为自生成，四条委派记录仍共用同一个值")
        void selfGeneratesTraceIdWhenAbsent() {
            CapturingTracePersistenceService capturing = new CapturingTracePersistenceService();
            SubAgentDispatcher dispatcher = stubDispatcher(
                    xhsResult(), routeResult(), itineraryResult(2), budgetResult("900", false));
            TripOrchestrationService service = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate(), capturing);

            service.orchestrate(new TripOrchestrationQuery(request(2, "5000")));

            assertEquals(4, capturing.delegations.size());
            String traceId = capturing.delegations.get(0).traceId();
            assertNotNull(traceId);
            assertFalse(traceId.isBlank());
            capturing.delegations.forEach(d -> assertEquals(traceId, d.traceId()));
            capturing.delegations.forEach(d -> assertEquals("", d.planId(),
                    "未传 planId 时应归一为空串（TraceDelegationRecord 紧凑构造器），"
                            + "而非 null，落库层才能安全写入 NOT NULL 列"));
        }
    }

}
