package com.smart.trip.agent.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RetryPolicy;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.RouteAgentRunner;
import com.smart.trip.skills.route.RoutePlanQuery;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.route.RoutePlanningService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RouteAgent} 原生路（增量 C）契约测试：注入桩 {@link RouteAgentRunner}（确定性、无真实 LLM），
 * 验证 ①原生路成功 → 透传权威路线、{@code fallback=false}；②原生路抛异常 → 降级回确定性直路、
 * {@code fallback=true}、不抛裸异常；③原生路返回 {@code null}（LLM 未调用工具）→ 同样降级回直路；
 * ④runner 为 null → 纯确定性直路（增量 A/B 行为）。
 *
 * <p>不触网、不依赖 LLM 密钥——真实 HarnessAgent 链路由 skills 的 env-gated 集成测试覆盖。</p>
 */
class RouteAgentNativeTest {

    private static final String SUPERVISOR = "agent://supervisor";
    private static final String TRACE = "T-native-route-1";
    private static final GeoLocation LOC_A = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");
    private static final GeoLocation LOC_B = new GeoLocation(104.1461, 30.7327, "大熊猫基地", "成都");
    private static final Attraction A1 = new Attraction("A-001", "宽窄巷子", LOC_A,
            4.5, 120, BigDecimal.TEN, List.of());
    private static final Attraction A2 = new Attraction("A-002", "大熊猫基地", LOC_B,
            4.8, 90, BigDecimal.TEN, List.of());

    private final AgentMetrics metrics = new AgentMetrics(new SimpleMeterRegistry());

    private static final GovernedExternalCaller CALLER = new GovernedExternalCaller(
            new RetryPolicy(0, 1), millis -> { });

    /** 确定性兜底 Service：桩端口。 */
    private RoutePlanningService deterministicService() {
        return new RoutePlanningService(
                (addr, city) -> LOC_A,
                (from, to) -> new Route(from, to, 10.0, 30, "驾车", false),
                null, name -> Optional.empty(), CALLER);
    }

    private Msg assign() {
        return Msg.taskAssign(SUPERVISOR, "agent://" + RouteAgent.AGENT_ID, TRACE,
                Map.of(Msg.KEY_TASK_TYPE, RouteAgent.TASK_TYPE,
                        RouteAgent.KEY_CONTEXT,
                        Map.of(RouteAgent.CTX_DESTINATION, "成都",
                                RouteAgent.CTX_ATTRACTIONS, List.of(A1, A2))));
    }

    @Test
    @DisplayName("原生路成功：透传 HarnessAgent 产出的权威路线，fallback=false")
    void nativePathSuccess() {
        Route nativeRoute = new Route(LOC_A, LOC_B, 15.0, 25, "驾车", false);
        RoutePlanResult nativeResult = new RoutePlanResult(
                List.of(nativeRoute), List.of(), List.of(), false, null, null);
        RouteAgentRunner runner = query -> nativeResult;

        RouteAgent agent = new RouteAgent(runner, deterministicService(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertFalse(result.isFallback());
        assertEquals(TRACE, result.traceId());
        RoutePlanResult plan = assertInstanceOf(RoutePlanResult.class,
                result.payload().get(Msg.KEY_RESULT));
        assertEquals(1, plan.routes().size());
        assertEquals(15.0, plan.routes().get(0).distanceKm());
    }

    @Test
    @DisplayName("原生路抛异常：降级回确定性直路，fallback=true，不抛裸异常")
    void nativePathFailureFallsBackToDeterministic() {
        RouteAgentRunner runner = query -> {
            throw new TimeoutException("llm slow / no key");
        };

        RouteAgent agent = new RouteAgent(runner, deterministicService(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertTrue(result.isFallback(), "原生路失败应显式降级 fallback=true");
        RoutePlanResult plan = assertInstanceOf(RoutePlanResult.class,
                result.payload().get(Msg.KEY_RESULT));
        assertFalse(plan.routes().isEmpty());
    }

    @Test
    @DisplayName("原生路返回 null（LLM 未调用工具）：降级回确定性直路，fallback=true")
    void nativePathNullFallsBack() {
        RouteAgentRunner runner = query -> null;

        RouteAgent agent = new RouteAgent(runner, deterministicService(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertTrue(result.isFallback());
        RoutePlanResult plan = assertInstanceOf(RoutePlanResult.class,
                result.payload().get(Msg.KEY_RESULT));
        assertFalse(plan.routes().isEmpty());
    }

    @Test
    @DisplayName("runner 为 null（未注入）：纯确定性直路（增量 A/B 行为），fallback=false")
    void nullRunnerDeterministicPath() {
        RouteAgent agent = new RouteAgent(deterministicService(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertFalse(result.isFallback());
        RoutePlanResult plan = assertInstanceOf(RoutePlanResult.class,
                result.payload().get(Msg.KEY_RESULT));
        assertEquals(1, plan.routes().size());
    }
}
