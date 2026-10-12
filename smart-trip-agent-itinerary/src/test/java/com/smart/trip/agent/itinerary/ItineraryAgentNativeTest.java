package com.smart.trip.agent.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.itinerary.ItineraryAgentRunner;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.itinerary.ItineraryDesignService;
import com.smart.trip.skills.itinerary.StubMealRecommender;
import com.smart.trip.skills.observability.AgentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ItineraryAgent} 原生路（增量 B）契约测试：注入桩 {@link ItineraryAgentRunner}
 * （确定性、无真实 LLM），验证：
 * ① 原生路成功 → 透传权威行程、{@code fallback=false}；
 * ② 原生路抛异常 → 降级回确定性直路、{@code fallback=true}、不抛裸异常；
 * ③ 原生路返回 {@code null}（LLM 未调用工具）→ 同样降级回直路；
 * ④ runner 为 null → 纯确定性直路（增量 A 行为）。
 *
 * <p>不触网、不依赖 LLM 密钥——真实 HarnessAgent 链路由 skills 的 env-gated 集成测试覆盖。</p>
 */
class ItineraryAgentNativeTest {

    private static final String SUPERVISOR = "agent://supervisor";
    private static final String TRACE = "T-native-itin-1";
    private static final GeoLocation LOC_A = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");
    private static final GeoLocation LOC_B = new GeoLocation(104.1461, 30.7327, "大熊猫基地", "成都");
    private static final Attraction A1 = new Attraction("A-001", "宽窄巷子", LOC_A,
            4.5, 120, BigDecimal.TEN, List.of());
    private static final Attraction A2 = new Attraction("A-002", "大熊猫基地", LOC_B,
            4.8, 90, BigDecimal.TEN, List.of());

    private static final TripPlanRequest REQUEST = new TripPlanRequest(
            "成都", LocalDate.of(2026, 7, 1), 2,
            new BigDecimal("5000"), 2, TravelStyle.FAMILY, List.of());

    private final AgentMetrics metrics = new AgentMetrics(new SimpleMeterRegistry());

    private ItineraryDesignService deterministicService() {
        return new ItineraryDesignService(new StubMealRecommender());
    }

    private Msg assign() {
        return Msg.taskAssign(SUPERVISOR, "agent://" + ItineraryAgent.AGENT_ID, TRACE,
                Map.of(Msg.KEY_TASK_TYPE, ItineraryAgent.TASK_TYPE,
                        ItineraryAgent.KEY_CONTEXT,
                        Map.of(ItineraryAgent.CTX_REQUEST, REQUEST,
                                ItineraryAgent.CTX_ATTRACTIONS, List.of(A1, A2))));
    }

    @Test
    @DisplayName("原生路成功：透传 HarnessAgent 产出的权威行程，fallback=false")
    void nativePathSuccess() {
        ItineraryAgentRunner runner = query -> deterministicService().design(query);
        ItineraryAgent agent = new ItineraryAgent(runner, deterministicService(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertFalse(result.isFallback(), "原生路成功应 fallback=false");
        assertEquals(TRACE, result.traceId());
        assertEquals("agent://itinerary-designer", result.from());
        ItineraryDesignResult plan = assertInstanceOf(ItineraryDesignResult.class,
                result.payload().get(Msg.KEY_RESULT));
        assertFalse(plan.days().isEmpty());
    }

    @Test
    @DisplayName("原生路抛异常：降级回确定性直路，fallback=true，不抛裸异常（AC-10）")
    void nativePathFailureFallsBackToDeterministic() {
        ItineraryAgentRunner runner = query -> {
            throw new TimeoutException("llm slow / no key");
        };
        ItineraryAgent agent = new ItineraryAgent(runner, deterministicService(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertTrue(result.isFallback(), "原生路失败应显式降级 fallback=true");
        ItineraryDesignResult plan = assertInstanceOf(ItineraryDesignResult.class,
                result.payload().get(Msg.KEY_RESULT));
        assertFalse(plan.days().isEmpty(), "降级后仍应有行程数据");
    }

    @Test
    @DisplayName("原生路返回 null（LLM 未调用工具）：降级回确定性直路，fallback=true（AC-10）")
    void nativePathNullFallsBack() {
        ItineraryAgentRunner runner = query -> null;
        ItineraryAgent agent = new ItineraryAgent(runner, deterministicService(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertTrue(result.isFallback(), "LLM 未调用工具应降级 fallback=true");
        ItineraryDesignResult plan = assertInstanceOf(ItineraryDesignResult.class,
                result.payload().get(Msg.KEY_RESULT));
        assertFalse(plan.days().isEmpty());
    }

    @Test
    @DisplayName("runner 为 null（未注入）：纯确定性直路（增量 A 行为），不触发 LLM（AC-10）")
    void nullRunnerDeterministicPath() {
        ItineraryAgent agent = new ItineraryAgent(deterministicService(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertFalse(result.isFallback(), "纯确定性直路不应标记 fallback");
        ItineraryDesignResult plan = assertInstanceOf(ItineraryDesignResult.class,
                result.payload().get(Msg.KEY_RESULT));
        assertEquals(2, plan.days().size());
    }

    @Test
    @DisplayName("context 含未知字段 → 忽略而非失败（向后兼容，A2A §2.3）")
    void unknownContextFieldsIgnored() {
        ItineraryAgent agent = new ItineraryAgent(deterministicService(), metrics);
        Msg assign = Msg.taskAssign(SUPERVISOR, "agent://" + ItineraryAgent.AGENT_ID, TRACE,
                Map.of(Msg.KEY_TASK_TYPE, ItineraryAgent.TASK_TYPE,
                        ItineraryAgent.KEY_CONTEXT,
                        Map.of(ItineraryAgent.CTX_REQUEST, REQUEST,
                                ItineraryAgent.CTX_ATTRACTIONS, List.of(A1, A2),
                                "unknownField", "shouldBeIgnored",
                                "nestedExtra", Map.of("key", "value"))));
        Msg result = agent.receive(assign);

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertFalse(result.isFallback());
        ItineraryDesignResult plan = assertInstanceOf(ItineraryDesignResult.class,
                result.payload().get(Msg.KEY_RESULT));
        assertEquals(2, plan.days().size());
    }
}
