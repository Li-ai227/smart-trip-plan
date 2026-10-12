package com.smart.trip.agent.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.itinerary.ItineraryDesignService;
import com.smart.trip.skills.itinerary.MealRecommender;
import com.smart.trip.skills.itinerary.StubMealRecommender;
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
 * {@link ItineraryAgent} A2A 收发契约测试（C-008 T-5）。
 */
class ItineraryAgentTest {

    private static final String SUPERVISOR = "agent://supervisor";
    private static final String TRACE = "T-itin-001";
    private static final GeoLocation LOC_A = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");
    private static final GeoLocation LOC_B = new GeoLocation(104.1461, 30.7327, "大熊猫基地", "成都");
    private static final Attraction A1 = new Attraction("A-001", "宽窄巷子", LOC_A,
            4.5, 120, BigDecimal.TEN, List.of());
    private static final Attraction A2 = new Attraction("A-002", "大熊猫基地", LOC_B,
            4.8, 90, BigDecimal.TEN, List.of());

    private static final TripPlanRequest REQUEST = new TripPlanRequest(
            "成都", LocalDate.of(2026, 7, 1), 2,
            new BigDecimal("5000"), 2, TravelStyle.FAMILY, List.of());

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AgentMetrics metrics = new AgentMetrics(registry);

    private ItineraryAgent agentWith(MealRecommender recommender) {
        ItineraryDesignService service = new ItineraryDesignService(recommender);
        return new ItineraryAgent(service, metrics);
    }

    private static Msg assign(Map<String, Object> payload) {
        return Msg.taskAssign(SUPERVISOR, "agent://" + ItineraryAgent.AGENT_ID, TRACE, payload);
    }

    private static Map<String, Object> itineraryPayload(Map<String, Object> context) {
        return Map.of(Msg.KEY_TASK_TYPE, ItineraryAgent.TASK_TYPE,
                ItineraryAgent.KEY_CONTEXT, context);
    }

    @Test
    @DisplayName("标识规范化为 agent://itinerary-designer")
    void agentUriNormalized() {
        ItineraryAgent agent = agentWith(new StubMealRecommender());
        assertEquals("agent://itinerary-designer", agent.agentUri());
    }

    @Nested
    @DisplayName("Happy Path：TASK_ASSIGN → TASK_RESULT")
    class HappyPath {

        @Test
        @DisplayName("2 景点 2 天 → TASK_RESULT，fallback=false，traceId 透传")
        void returnsItinerary() {
            ItineraryAgent agent = agentWith(new StubMealRecommender());
            Msg result = agent.receive(assign(itineraryPayload(Map.of(
                    ItineraryAgent.CTX_REQUEST, REQUEST,
                    ItineraryAgent.CTX_ATTRACTIONS, List.of(A1, A2)))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertEquals(TRACE, result.traceId());
            assertEquals("agent://itinerary-designer", result.from());
            assertEquals(SUPERVISOR, result.to());
            assertFalse(result.isFallback());
            ItineraryDesignResult plan = assertInstanceOf(ItineraryDesignResult.class,
                    result.payload().get(Msg.KEY_RESULT));
            assertFalse(plan.days().isEmpty());
        }

        @Test
        @DisplayName("记录 agent.call.latency 指标")
        void recordsLatency() {
            ItineraryAgent agent = agentWith(new StubMealRecommender());
            agent.receive(assign(itineraryPayload(Map.of(
                    ItineraryAgent.CTX_REQUEST, REQUEST,
                    ItineraryAgent.CTX_ATTRACTIONS, List.of(A1, A2)))));

            var timer = registry.find(AgentMetrics.METRIC_CALL_LATENCY)
                    .tag("agent", "agent://itinerary-designer")
                    .tag("taskType", ItineraryAgent.TASK_TYPE)
                    .timer();
            assertNotNull(timer);
            assertEquals(1, timer.count());
        }
    }

    @Nested
    @DisplayName("降级：餐饮推荐为空 → fallback=true")
    class Degradation {

        @Test
        @DisplayName("MealRecommender 返回空 → TASK_RESULT fallback=true")
        void emptyMeal_fallback() {
            MealRecommender empty = q -> List.of();
            ItineraryAgent agent = agentWith(empty);
            Msg result = agent.receive(assign(itineraryPayload(Map.of(
                    ItineraryAgent.CTX_REQUEST, REQUEST,
                    ItineraryAgent.CTX_ATTRACTIONS, List.of(A1, A2)))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertTrue(result.isFallback());
        }
    }

    @Nested
    @DisplayName("错误：非法请求 → TASK_ERROR")
    class ErrorHandling {

        @Test
        @DisplayName("taskType 不匹配 → TASK_ERROR")
        void wrongTaskType() {
            ItineraryAgent agent = agentWith(new StubMealRecommender());
            Msg result = agent.receive(assign(Map.of(
                    Msg.KEY_TASK_TYPE, "UNKNOWN")));

            assertEquals(MessageType.TASK_ERROR, result.type());
            assertEquals(ErrorCode.INVALID_REQUEST.name(),
                    result.payload().get(Msg.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("缺少 request → TASK_ERROR")
        void missingRequest() {
            ItineraryAgent agent = agentWith(new StubMealRecommender());
            Msg result = agent.receive(assign(itineraryPayload(Map.of(
                    ItineraryAgent.CTX_ATTRACTIONS, List.of(A1, A2)))));

            assertEquals(MessageType.TASK_ERROR, result.type());
        }

        @Test
        @DisplayName("null assign → 抛 InvalidRequestException")
        void nullAssign_throws() {
            ItineraryAgent agent = agentWith(new StubMealRecommender());
            assertThrows(com.smart.trip.common.exception.InvalidRequestException.class,
                    () -> agent.receive(null));
        }

        @Test
        @DisplayName("TASK_RESULT 类型 assign → 抛 InvalidRequestException（仅受理 TASK_ASSIGN）")
        void wrongMessageType_throws() {
            ItineraryAgent agent = agentWith(new StubMealRecommender());
            Msg badType = Msg.taskResult(SUPERVISOR, "agent://itinerary-designer", TRACE,
                    null, false);
            assertThrows(com.smart.trip.common.exception.InvalidRequestException.class,
                    () -> agent.receive(badType));
        }

        @Test
        @DisplayName("designService 抛 RuntimeException → TASK_ERROR + AGENT_FAILURE")
        void runtimeExceptionInDesign_yieldsAgentFailureCode() {
            MealRecommender throwing = q -> {
                throw new RuntimeException("模拟未预期异常");
            };
            ItineraryDesignService service = new ItineraryDesignService(throwing);
            ItineraryAgent agent = new ItineraryAgent(service, metrics);
            Msg result = agent.receive(assign(itineraryPayload(Map.of(
                    ItineraryAgent.CTX_REQUEST, REQUEST,
                    ItineraryAgent.CTX_ATTRACTIONS, List.of(A1, A2)))));

            assertEquals(MessageType.TASK_ERROR, result.type());
            assertEquals("AGENT_FAILURE", result.payload().get(Msg.KEY_ERROR_CODE));
            assertEquals("模拟未预期异常", result.payload().get(Msg.KEY_ERROR_MESSAGE));
        }
    }
}
