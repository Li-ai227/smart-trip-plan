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
 * {@link BudgetAgent} A2A 收发契约测试（C-009 T-4）。
 */
class BudgetAgentTest {

    private static final String SUPERVISOR = "agent://supervisor";
    private static final String TRACE = "T-budget-001";
    private static final GeoLocation LOC = new GeoLocation(104.05, 30.67, "宽窄巷子", "成都");
    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    private static final TripPlanRequest REQUEST = new TripPlanRequest(
            "成都", START, 2, new BigDecimal("8000"), 2, TravelStyle.FAMILY, List.of());

    private static final Attraction A1 = new Attraction("A-001", "宽窄巷子", LOC,
            4.5, 120, new BigDecimal("80"), List.of());

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AgentMetrics metrics = new AgentMetrics(registry);
    private final BudgetCalculationService service = new BudgetCalculationService();

    private BudgetAgent agent() {
        return new BudgetAgent(service, metrics);
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

    private static TripDay tripDay(int dayIndex) {
        return new TripDay(START.plusDays(dayIndex - 1), dayIndex,
                List.of(A1),
                List.of(new Meal("午餐", LOC, MealType.LUNCH, new BigDecimal("60"), List.of())),
                List.of(new Route(LOC, LOC, 15.0, 30, "驾车", false)),
                BigDecimal.ZERO);
    }

    private static ItineraryDesignResult itinerary(List<TripDay> days) {
        return new ItineraryDesignResult(days, List.of(), false, null, null);
    }

    @Test
    @DisplayName("标识规范化为 agent://budget-controller")
    void agentUriNormalized() {
        assertEquals("agent://budget-controller", agent().agentUri());
    }

    @Nested
    @DisplayName("Happy Path：TASK_ASSIGN → TASK_RESULT")
    class HappyPath {

        @Test
        @DisplayName("正常费用核算 → TASK_RESULT，traceId 透传")
        void returnsBudgetResult() {
            ItineraryDesignResult itin = itinerary(List.of(tripDay(1), tripDay(2)));
            Msg result = agent().receive(assign(budgetPayload(REQUEST, itin)));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertEquals(TRACE, result.traceId());
            assertEquals(BudgetAgent.AGENT_URI, result.from());
            assertInstanceOf(BudgetCalculationResult.class,
                    result.payload().get(Msg.KEY_RESULT));
        }

        @Test
        @DisplayName("结果含四类费用拆分且 totalCost = 四类之和")
        void resultBreakdownComplete() {
            ItineraryDesignResult itin = itinerary(List.of(tripDay(1)));
            Msg result = agent().receive(assign(budgetPayload(REQUEST, itin)));

            BudgetCalculationResult r = (BudgetCalculationResult) result.payload()
                    .get(Msg.KEY_RESULT);
            BigDecimal sum = r.transport().add(r.hotel()).add(r.food()).add(r.ticket());
            assertEquals(0, sum.compareTo(r.totalCost()));
        }

        @Test
        @DisplayName("预算内 → requiresHITL=false")
        void withinBudget() {
            ItineraryDesignResult itin = itinerary(List.of(tripDay(1)));
            Msg result = agent().receive(assign(budgetPayload(REQUEST, itin)));

            BudgetCalculationResult r = (BudgetCalculationResult) result.payload()
                    .get(Msg.KEY_RESULT);
            assertFalse(r.requiresHITL());
        }
    }

    @Nested
    @DisplayName("A2A 契约校验")
    class ContractValidation {

        @Test
        @DisplayName("null 消息 → InvalidRequestException")
        void nullMessage() {
            assertThrows(InvalidRequestException.class, () -> agent().receive(null));
        }

        @Test
        @DisplayName("非 TASK_ASSIGN 类型 → InvalidRequestException")
        void wrongType() {
            Msg result = Msg.taskResult(SUPERVISOR, BudgetAgent.AGENT_URI, TRACE, "x", false);
            assertThrows(InvalidRequestException.class, () -> agent().receive(result));
        }

        @Test
        @DisplayName("Case-8: taskType=ITINERARY_DESIGN → TASK_ERROR")
        void wrongTaskType() {
            Map<String, Object> payload = Map.of(
                    Msg.KEY_TASK_TYPE, "ITINERARY_DESIGN",
                    BudgetAgent.KEY_CONTEXT, Map.of());
            Msg result = agent().receive(assign(payload));

            assertEquals(MessageType.TASK_ERROR, result.type());
        }

        @Test
        @DisplayName("context.request 缺失 → TASK_ERROR")
        void missingRequest() {
            ItineraryDesignResult itin = itinerary(List.of(tripDay(1)));
            Map<String, Object> payload = Map.of(
                    Msg.KEY_TASK_TYPE, BudgetAgent.TASK_TYPE,
                    BudgetAgent.KEY_CONTEXT, Map.of(
                            BudgetAgent.CTX_ITINERARY_RESULT, itin));
            Msg result = agent().receive(assign(payload));

            assertEquals(MessageType.TASK_ERROR, result.type());
        }

        @Test
        @DisplayName("context.itineraryResult 缺失 → TASK_ERROR")
        void missingItinerary() {
            Map<String, Object> payload = Map.of(
                    Msg.KEY_TASK_TYPE, BudgetAgent.TASK_TYPE,
                    BudgetAgent.KEY_CONTEXT, Map.of(
                            BudgetAgent.CTX_REQUEST, REQUEST));
            Msg result = agent().receive(assign(payload));

            assertEquals(MessageType.TASK_ERROR, result.type());
        }

        @Test
        @DisplayName("context 含未知字段 → 忽略不失败")
        void unknownFieldIgnored() {
            ItineraryDesignResult itin = itinerary(List.of(tripDay(1)));
            Map<String, Object> context = Map.of(
                    BudgetAgent.CTX_REQUEST, REQUEST,
                    BudgetAgent.CTX_ITINERARY_RESULT, itin,
                    "unknownField", "someValue");
            Map<String, Object> payload = Map.of(
                    Msg.KEY_TASK_TYPE, BudgetAgent.TASK_TYPE,
                    BudgetAgent.KEY_CONTEXT, context);
            Msg result = agent().receive(assign(payload));

            assertEquals(MessageType.TASK_RESULT, result.type());
        }
    }

    @Nested
    @DisplayName("HITL 触发")
    class HITLTrigger {

        @Test
        @DisplayName("超支 ≥15% → requiresHITL=true + 调整建议")
        void overBudgetTriggersHITL() {
            // budget=1000, make costs exceed by ≥15%
            TripPlanRequest smallBudget = new TripPlanRequest(
                    "成都", START, 2, new BigDecimal("1000"), 2,
                    TravelStyle.FAMILY, List.of());
            ItineraryDesignResult itin = itinerary(List.of(tripDay(1), tripDay(2)));

            BudgetAgent a = agent();
            Msg result = a.receive(assign(budgetPayload(smallBudget, itin)));

            BudgetCalculationResult r = (BudgetCalculationResult) result.payload()
                    .get(Msg.KEY_RESULT);
            assertTrue(r.requiresHITL());
            assertNotNull(r.hitlReason());
            assertFalse(r.adjustmentAdvice().isEmpty());
        }
    }

    @Nested
    @DisplayName("生命周期状态")
    class Lifecycle {

        @Test
        @DisplayName("初始状态为 IDLE，处理后为 DONE")
        void statusTransitions() {
            BudgetAgent a = agent();
            assertEquals(com.smart.trip.common.enums.AgentStatus.IDLE, a.status());

            ItineraryDesignResult itin = itinerary(List.of(tripDay(1)));
            a.receive(assign(budgetPayload(REQUEST, itin)));

            assertEquals(com.smart.trip.common.enums.AgentStatus.DONE, a.status());
        }
    }
}
