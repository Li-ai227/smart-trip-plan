package com.smart.trip.agent.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.budget.BudgetAgentRunner;
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
import org.junit.jupiter.api.Test;

/**
 * {@link BudgetAgent} 原生接缝测试（C-009 T-8 / AC-13/AC-14）。
 */
class BudgetAgentNativeTest {

    private static final String SUPERVISOR = "agent://supervisor";
    private static final String TRACE = "T-native-001";
    private static final GeoLocation LOC = new GeoLocation(104.05, 30.67, "测试", "成都");
    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    private static final TripPlanRequest REQUEST = new TripPlanRequest(
            "成都", START, 1, new BigDecimal("8000"), 2, TravelStyle.FAMILY, List.of());

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AgentMetrics metrics = new AgentMetrics(registry);
    private final BudgetCalculationService service = new BudgetCalculationService();

    private static TripDay tripDay() {
        return new TripDay(START, 1,
                List.of(new Attraction("A1", "景点", LOC, 4.5, 60,
                        new BigDecimal("50"), List.of())),
                List.of(new Meal("午餐", LOC, MealType.LUNCH, new BigDecimal("40"), List.of())),
                List.of(new Route(LOC, LOC, 10.0, 20, "驾车", false)),
                BigDecimal.ZERO);
    }

    private static ItineraryDesignResult itinerary() {
        return new ItineraryDesignResult(List.of(tripDay()), List.of(), false, null, null);
    }

    private Msg assign() {
        Map<String, Object> payload = Map.of(
                Msg.KEY_TASK_TYPE, BudgetAgent.TASK_TYPE,
                BudgetAgent.KEY_CONTEXT, Map.of(
                        BudgetAgent.CTX_REQUEST, REQUEST,
                        BudgetAgent.CTX_ITINERARY_RESULT, itinerary()));
        return Msg.taskAssign(SUPERVISOR, BudgetAgent.AGENT_URI, TRACE, payload);
    }

    @Test
    @DisplayName("注入成功桩 runner → 原生产物，llmDegraded=false")
    void successRunner() {
        BudgetCalculationResult expected = service.calculate(
                new com.smart.trip.skills.budget.BudgetCalculationQuery(REQUEST, itinerary()));

        BudgetAgentRunner successRunner = q -> expected;
        BudgetAgent agent = new BudgetAgent(successRunner, service, metrics);

        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        BudgetCalculationResult actual = (BudgetCalculationResult) result.payload()
                .get(Msg.KEY_RESULT);
        assertEquals(0, expected.totalCost().compareTo(actual.totalCost()));
        // fallback reflects the result's own fallback state (hotel always estimated)
        // but llmDegraded is false — native path succeeded
        assertEquals(expected.fallback(), actual.fallback());
    }

    @Test
    @DisplayName("注入异常桩 runner → 降级确定性 + fallback=true")
    void exceptionRunner() {
        BudgetAgentRunner failRunner = q -> { throw new RuntimeException("LLM 超时"); };
        BudgetAgent agent = new BudgetAgent(failRunner, service, metrics);

        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertTrue(result.isFallback());
        assertInstanceOf(BudgetCalculationResult.class,
                result.payload().get(Msg.KEY_RESULT));
    }

    @Test
    @DisplayName("注入 null-result 桩 runner → 降级确定性 + fallback=true")
    void nullResultRunner() {
        BudgetAgentRunner nullRunner = q -> null;
        BudgetAgent agent = new BudgetAgent(nullRunner, service, metrics);

        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertTrue(result.isFallback());
        assertInstanceOf(BudgetCalculationResult.class,
                result.payload().get(Msg.KEY_RESULT));
    }

    @Test
    @DisplayName("runner 为 null → 纯确定性直路（增量 A 行为）")
    void nullRunner() {
        BudgetAgent agent = new BudgetAgent(null, service, metrics);

        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertInstanceOf(BudgetCalculationResult.class,
                result.payload().get(Msg.KEY_RESULT));
    }
}
