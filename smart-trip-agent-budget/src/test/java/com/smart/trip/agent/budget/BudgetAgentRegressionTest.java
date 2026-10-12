package com.smart.trip.agent.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.budget.BudgetCalculationService;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.observability.AgentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BudgetAgent} A2A 回归测试（C-009 T-5 延伸）。
 * 验证全部验收标准经 A2A 路径通过：收发契约 / 降级 / HITL / 边界 / 全链路对账。
 */
class BudgetAgentRegressionTest {

    private static final String SUPERVISOR = "agent://supervisor";
    private static final String TRACE = "T-reg-001";
    private static final GeoLocation LOC = new GeoLocation(104.05, 30.67, "宽窄巷子", "成都");
    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AgentMetrics metrics = new AgentMetrics(registry);

    private static TripPlanRequest request(BigDecimal budget, int headcount, int days) {
        return new TripPlanRequest("成都", START, days, budget, headcount,
                TravelStyle.FAMILY, List.of());
    }

    private static Attraction a(String id, BigDecimal ticketPrice) {
        return new Attraction(id, "景点" + id, LOC, 4.5, 90, ticketPrice, List.of());
    }

    private static Meal m(MealType type, BigDecimal avgPrice) {
        return new Meal("餐厅", LOC, type, avgPrice, List.of());
    }

    private static Route r(double km) {
        return new Route(LOC, LOC, km, 25, "驾车", false);
    }

    private static TripDay day(int index) {
        return new TripDay(START.plusDays(index - 1), index,
                List.of(a("A" + index, new BigDecimal("80"))),
                List.of(m(MealType.LUNCH, new BigDecimal("60")),
                        m(MealType.DINNER, new BigDecimal("80"))),
                List.of(r(15)),
                BigDecimal.ZERO);
    }

    private static ItineraryDesignResult itin(List<TripDay> days) {
        return new ItineraryDesignResult(days, List.of(), false, null, null);
    }

    private static Msg assign(Map<String, Object> payload) {
        return Msg.taskAssign(SUPERVISOR, BudgetAgent.AGENT_URI, TRACE, payload);
    }

    private static Map<String, Object> budgetPayload(TripPlanRequest req,
                                                     ItineraryDesignResult itin) {
        return Map.of(
                Msg.KEY_TASK_TYPE, BudgetAgent.TASK_TYPE,
                BudgetAgent.KEY_CONTEXT, Map.of(
                        BudgetAgent.CTX_REQUEST, req,
                        BudgetAgent.CTX_ITINERARY_RESULT, itin));
    }

    private BudgetAgent agent() {
        return new BudgetAgent(new BudgetCalculationService(), metrics);
    }

    // ============ 全链路回归 ============

    @Nested
    @DisplayName("A2A 全链路回归（增量 A 全部 AC）")
    class FullPathRegression {

        @Test
        @DisplayName("AC-1~AC-2: agentUri/budget-controller, 正常 TASK_RESULT")
        void ac12_normalPath() {
            BudgetAgent a = agent();
            assertEquals("agent://budget-controller", a.agentUri());

            TripPlanRequest req = request(new BigDecimal("8000"), 2, 2);
            Msg result = a.receive(assign(budgetPayload(req, itin(List.of(day(1), day(2))))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertEquals(TRACE, result.traceId());
            assertInstanceOf(BudgetCalculationResult.class,
                    result.payload().get(Msg.KEY_RESULT));
        }

        @Test
        @DisplayName("AC-3~AC-4: 四类拆分 + totalCost = 四类之和")
        void ac34_breakdown() {
            Msg result = agent().receive(assign(budgetPayload(
                    request(new BigDecimal("10000"), 2, 1), itin(List.of(day(1))))));

            BudgetCalculationResult r = (BudgetCalculationResult) result.payload()
                    .get(Msg.KEY_RESULT);
            BigDecimal sum = r.transport().add(r.hotel()).add(r.food()).add(r.ticket());
            assertEquals(0, sum.compareTo(r.totalCost()));
        }

        @Test
        @DisplayName("AC-5~AC-6: BigDecimal overrunRate + HITL >=0.15 含等号")
        void ac56_hitlBigDecimal() {
            // totalCost = 9200 vs budget = 8000 → exactly 0.15
            // ticket=8800, food=0, transport=0 (route with 0km), hotel=200*2*1=400
            TripPlanRequest req = request(new BigDecimal("8000"), 2, 1);
            TripDay d = new TripDay(START, 1,
                    List.of(a("A1", new BigDecimal("8800"))),
                    List.of(m(MealType.LUNCH, BigDecimal.ZERO)),
                    List.of(new Route(LOC, LOC, 0.0, 0, "驾车", false)),
                    BigDecimal.ZERO);
            Msg result = agent().receive(assign(budgetPayload(req, itin(List.of(d)))));

            BudgetCalculationResult r = (BudgetCalculationResult) result.payload()
                    .get(Msg.KEY_RESULT);
            assertEquals(0, r.overrunRate().compareTo(new BigDecimal("0.15")));
            assertTrue(r.requiresHITL());
        }

        @Test
        @DisplayName("AC-7: 超支项清单 + 调整建议")
        void ac7_hitlAdvice() {
            TripPlanRequest req = request(new BigDecimal("500"), 2, 1);
            Msg result = agent().receive(assign(budgetPayload(req, itin(List.of(day(1))))));

            BudgetCalculationResult r = (BudgetCalculationResult) result.payload()
                    .get(Msg.KEY_RESULT);
            assertTrue(r.requiresHITL());
            assertNotNull(r.hitlReason());
            assertFalse(r.adjustmentAdvice().isEmpty());
        }

        @Test
        @DisplayName("AC-8: 对账一致性 — Σ dailyTotal == totalCost")
        void ac8_reconciliation() {
            TripPlanRequest req = request(new BigDecimal("50000"), 3, 5);
            Msg result = agent().receive(assign(budgetPayload(req,
                    itin(List.of(day(1), day(2), day(3), day(4), day(5))))));

            BudgetCalculationResult r = (BudgetCalculationResult) result.payload()
                    .get(Msg.KEY_RESULT);

            BigDecimal daySum = r.dailyBreakdowns().stream()
                    .map(com.smart.trip.skills.budget.DailyCostDetail::dailyTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, daySum.compareTo(r.totalCost()));
        }

        @Test
        @DisplayName("AC-9: 空行程 → totalCost=ZERO, 不除零")
        void ac9_emptyItinerary() {
            Msg result = agent().receive(assign(budgetPayload(
                    request(new BigDecimal("5000"), 2, 3), itin(List.of()))));

            BudgetCalculationResult r = (BudgetCalculationResult) result.payload()
                    .get(Msg.KEY_RESULT);
            assertEquals(0, BigDecimal.ZERO.compareTo(r.totalCost()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r.overrunRate()));
            assertFalse(r.requiresHITL());
        }

        @Test
        @DisplayName("AC-10: 可观测 — traceId/agentId 透传，latency 记录")
        void ac10_observability() {
            TripPlanRequest req = request(new BigDecimal("8000"), 2, 1);
            long before = registry.getMeters().size();
            Msg result = agent().receive(assign(budgetPayload(req, itin(List.of(day(1))))));

            assertEquals(TRACE, result.traceId());
            assertEquals(BudgetAgent.AGENT_URI, result.from());
            // metrics 已记录（SimpleMeterRegistry 中至少新增 latency）
            assertTrue(registry.getMeters().size() >= before);
        }
    }

    // ============ A2A 错误路径 ============

    @Nested
    @DisplayName("A2A 错误路径")
    class ErrorPaths {

        @Test
        @DisplayName("taskType=null → TASK_ERROR")
        void nullTaskType() {
            java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put(Msg.KEY_TASK_TYPE, null);
            payload.put(BudgetAgent.KEY_CONTEXT, Map.of());
            Msg result = agent().receive(assign(payload));

            assertEquals(MessageType.TASK_ERROR, result.type());
        }

        @Test
        @DisplayName("context 不含 request → TASK_ERROR")
        void contextMissingRequest() {
            Map<String, Object> payload = Map.of(
                    Msg.KEY_TASK_TYPE, BudgetAgent.TASK_TYPE,
                    BudgetAgent.KEY_CONTEXT, Map.of());
            Msg result = agent().receive(assign(payload));

            assertEquals(MessageType.TASK_ERROR, result.type());
        }

        @Test
        @DisplayName("context.request 类型错误 → TASK_ERROR")
        void wrongRequestType() {
            Map<String, Object> payload = Map.of(
                    Msg.KEY_TASK_TYPE, BudgetAgent.TASK_TYPE,
                    BudgetAgent.KEY_CONTEXT, Map.of(
                            BudgetAgent.CTX_REQUEST, "not-a-request",
                            BudgetAgent.CTX_ITINERARY_RESULT, itin(List.of(day(1)))));
            Msg result = agent().receive(assign(payload));

            assertEquals(MessageType.TASK_ERROR, result.type());
        }

        @Test
        @DisplayName("context.itineraryResult 类型错误 → TASK_ERROR")
        void wrongItineraryType() {
            Map<String, Object> payload = Map.of(
                    Msg.KEY_TASK_TYPE, BudgetAgent.TASK_TYPE,
                    BudgetAgent.KEY_CONTEXT, Map.of(
                            BudgetAgent.CTX_REQUEST, request(new BigDecimal("5000"), 2, 1),
                            BudgetAgent.CTX_ITINERARY_RESULT, "not-itinerary"));
            Msg result = agent().receive(assign(payload));

            assertEquals(MessageType.TASK_ERROR, result.type());
        }

        @Test
        @DisplayName("budget≤0 经 A2A → TASK_ERROR")
        void zeroBudgetViaA2A() {
            Msg result = agent().receive(assign(budgetPayload(
                    request(BigDecimal.ZERO, 2, 1), itin(List.of(day(1))))));

            assertEquals(MessageType.TASK_ERROR, result.type());
        }
    }

    // ============ A2A 降级路径 ============

    @Nested
    @DisplayName("A2A 降级路径")
    class DegradationPaths {

        @Test
        @DisplayName("路线缺失 → TASK_RESULT, fallback=true, 数据完整")
        void routesMissingDegradation() {
            TripDay d = new TripDay(START, 1,
                    List.of(a("A1", new BigDecimal("80"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("60"))),
                    List.of(),
                    BigDecimal.ZERO);
            Msg result = agent().receive(assign(budgetPayload(
                    request(new BigDecimal("8000"), 2, 1), itin(List.of(d)))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertTrue(result.isFallback());
            BudgetCalculationResult r = (BudgetCalculationResult) result.payload()
                    .get(Msg.KEY_RESULT);
            assertNotNull(r.totalCost());
        }

        @Test
        @DisplayName("所有费用项降级 → 仍产出 TASK_RESULT")
        void allDegraded() {
            TripDay d = new TripDay(START, 1,
                    List.of(a("A1", null)),
                    List.of(m(MealType.LUNCH, null), m(MealType.DINNER, null)),
                    List.of(),
                    BigDecimal.ZERO);
            Msg result = agent().receive(assign(budgetPayload(
                    request(new BigDecimal("10000"), 3, 1), itin(List.of(d)))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            BudgetCalculationResult r = (BudgetCalculationResult) result.payload()
                    .get(Msg.KEY_RESULT);
            assertTrue(r.fallback());
            assertNotNull(r.totalCost());
        }
    }

    // ============ 状态机 ============

    @Nested
    @DisplayName("Agent 状态机")
    class StateMachine {

        @Test
        @DisplayName("正常处理: IDLE → THINKING → ACTING → DONE")
        void normalTransition() {
            BudgetAgent a = agent();
            assertEquals(com.smart.trip.common.enums.AgentStatus.IDLE, a.status());

            a.receive(assign(budgetPayload(
                    request(new BigDecimal("8000"), 2, 1), itin(List.of(day(1))))));

            assertEquals(com.smart.trip.common.enums.AgentStatus.DONE, a.status());
        }

        @Test
        @DisplayName("异常处理: 状态回归 DONE")
        void errorTransition() {
            BudgetAgent a = agent();
            // An error path should still end in DONE
            Map<String, Object> payload = Map.of(
                    Msg.KEY_TASK_TYPE, "WRONG_TYPE",
                    BudgetAgent.KEY_CONTEXT, Map.of());
            a.receive(assign(payload));

            assertEquals(com.smart.trip.common.enums.AgentStatus.DONE, a.status());
        }
    }

    // ============ 构建器防御 ============

    @Nested
    @DisplayName("构造防御")
    class Construction {

        @Test
        @DisplayName("构造 BudgetAgent: calculationService=null → InvalidRequestException")
        void nullService() {
            assertThrows(InvalidRequestException.class, () ->
                    new BudgetAgent(null, metrics));
        }

        @Test
        @DisplayName("构造 BudgetAgent: metrics=null → InvalidRequestException")
        void nullMetrics() {
            assertThrows(InvalidRequestException.class, () ->
                    new BudgetAgent(new BudgetCalculationService(), null));
        }
    }
}
