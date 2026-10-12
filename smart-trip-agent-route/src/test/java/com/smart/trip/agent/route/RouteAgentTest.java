package com.smart.trip.agent.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RateLimitRule;
import com.smart.trip.skills.governance.RateLimiter;
import com.smart.trip.skills.governance.RateLimitStore;
import com.smart.trip.skills.governance.RetryPolicy;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.MapGeocoder;
import com.smart.trip.skills.route.MapRouter;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.route.RoutePlanningService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RouteAgent} A2A 收发契约测试（C-007 T-4）。
 */
class RouteAgentTest {

    private static final String SUPERVISOR = "agent://supervisor";
    private static final String TRACE = "T-route-001";
    private static final GeoLocation LOC_A = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");
    private static final GeoLocation LOC_B = new GeoLocation(104.1461, 30.7327, "大熊猫基地", "成都");
    private static final Attraction A1 = new Attraction("A-001", "宽窄巷子", LOC_A,
            4.5, 120, BigDecimal.TEN, List.of());
    private static final Attraction A2 = new Attraction("A-002", "大熊猫基地", LOC_B,
            4.8, 90, BigDecimal.TEN, List.of());

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AgentMetrics metrics = new AgentMetrics(registry);

    private static final GovernedExternalCaller CALLER = new GovernedExternalCaller(
            new RetryPolicy(0, 1), millis -> { });

    private RouteAgent agentWith(MapRouter router) {
        MapGeocoder geocoder = (addr, city) -> LOC_A;
        RoutePlanningService service = new RoutePlanningService(
                geocoder, router, null, name -> Optional.empty(), CALLER);
        return new RouteAgent(service, metrics);
    }

    private static MapRouter stubRouter() {
        return (from, to) -> new Route(from, to, 10.0, 30, "驾车", false);
    }

    private static Msg assign(Map<String, Object> payload) {
        return Msg.taskAssign(SUPERVISOR, "agent://" + RouteAgent.AGENT_ID, TRACE, payload);
    }

    private static Map<String, Object> routePayload(Map<String, Object> context) {
        return Map.of(Msg.KEY_TASK_TYPE, RouteAgent.TASK_TYPE, RouteAgent.KEY_CONTEXT, context);
    }

    @Test
    @DisplayName("标识规范化为 agent://route-planner")
    void agentUriNormalized() {
        RouteAgent agent = agentWith(stubRouter());
        assertEquals("agent://route-planner", agent.agentUri());
    }

    @Nested
    @DisplayName("Happy Path：TASK_ASSIGN → TASK_RESULT")
    class HappyPath {

        @Test
        @DisplayName("2 景点 → 1 条路线，fallback=false，traceId 透传")
        void returnsRoutes() {
            RouteAgent agent = agentWith(stubRouter());
            Msg result = agent.receive(assign(routePayload(Map.of(
                    RouteAgent.CTX_DESTINATION, "成都",
                    RouteAgent.CTX_ATTRACTIONS, List.of(A1, A2)))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertEquals(TRACE, result.traceId());
            assertEquals("agent://route-planner", result.from());
            assertEquals(SUPERVISOR, result.to());
            assertFalse(result.isFallback());
            RoutePlanResult plan = assertInstanceOf(RoutePlanResult.class,
                    result.payload().get(Msg.KEY_RESULT));
            assertEquals(1, plan.routes().size());
        }

        @Test
        @DisplayName("记录 agent.call.latency 指标")
        void recordsLatency() {
            RouteAgent agent = agentWith(stubRouter());
            agent.receive(assign(routePayload(Map.of(
                    RouteAgent.CTX_DESTINATION, "成都",
                    RouteAgent.CTX_ATTRACTIONS, List.of(A1, A2)))));

            var timer = registry.find(AgentMetrics.METRIC_CALL_LATENCY)
                    .tag("agent", "agent://route-planner")
                    .tag("taskType", RouteAgent.TASK_TYPE)
                    .timer();
            assertNotNull(timer);
            assertEquals(1, timer.count());
        }
    }

    @Nested
    @DisplayName("降级：路线端口故障 → fallback=true")
    class Degradation {

        @Test
        @DisplayName("路线端口故障 → TASK_RESULT 仍返回，fallback=true")
        void routerFailure_fallback() {
            MapRouter failing = (from, to) -> {
                throw new RuntimeException("路线服务不可用");
            };
            RouteAgent agent = agentWith(failing);
            Msg result = agent.receive(assign(routePayload(Map.of(
                    RouteAgent.CTX_DESTINATION, "成都",
                    RouteAgent.CTX_ATTRACTIONS, List.of(A1, A2)))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertTrue(result.isFallback());
        }

        @Test
        @DisplayName("限频命中 → TASK_RESULT fallback=true，不抛裸异常（Case-6）")
        void rateLimitHit_fallback_true() {
            // 构造已耗尽限频器 → acquireOrThrow 抛 RateLimitedException
            RateLimitStore exhausted = (key, window) -> 31L;
            RateLimiter limiter = new RateLimiter(exhausted,
                    Map.of(RateLimiter.API_MAP,
                            new RateLimitRule(RateLimiter.API_MAP, 30, 60)));
            GovernedExternalCaller rateLimitedCaller = new GovernedExternalCaller(
                    new RetryPolicy(0, 1), millis -> {}, limiter);

            MapGeocoder geocoder = (addr, city) -> LOC_A;
            RoutePlanningService service = new RoutePlanningService(
                    geocoder, stubRouter(), null, name -> Optional.empty(),
                    rateLimitedCaller);
            RouteAgent agent = new RouteAgent(service, metrics);

            Msg result = agent.receive(assign(routePayload(Map.of(
                    RouteAgent.CTX_DESTINATION, "成都",
                    RouteAgent.CTX_ATTRACTIONS, List.of(A1, A2)))));

            // 限频命中不抛裸异常（AC-6），降级返回 haversine 估算 + fallback=true
            assertEquals(MessageType.TASK_RESULT, result.type());
            assertTrue(result.isFallback(),
                    "限频命中应产出 fallback=true 的 TASK_RESULT");
        }
    }

    @Nested
    @DisplayName("错误：非法请求 → TASK_ERROR")
    class ErrorHandling {

        @Test
        @DisplayName("taskType 不匹配 → TASK_ERROR")
        void wrongTaskType() {
            RouteAgent agent = agentWith(stubRouter());
            Msg result = agent.receive(assign(Map.of(
                    Msg.KEY_TASK_TYPE, "UNKNOWN")));

            assertEquals(MessageType.TASK_ERROR, result.type());
            assertEquals(ErrorCode.INVALID_REQUEST.name(),
                    result.payload().get(Msg.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("缺少 destination → TASK_ERROR")
        void missingDestination() {
            RouteAgent agent = agentWith(stubRouter());
            Msg result = agent.receive(assign(routePayload(Map.of(
                    RouteAgent.CTX_ATTRACTIONS, List.of(A1, A2)))));

            assertEquals(MessageType.TASK_ERROR, result.type());
        }

        @Test
        @DisplayName("null assign → 抛 InvalidRequestException")
        void nullAssign_throws() {
            RouteAgent agent = agentWith(stubRouter());
            assertThrows(com.smart.trip.common.exception.InvalidRequestException.class,
                    () -> agent.receive(null));
        }

        @Test
        @DisplayName("TASK_RESULT 类型 assign → 抛 InvalidRequestException（仅受理 TASK_ASSIGN）")
        void wrongMessageType_throws() {
            RouteAgent agent = agentWith(stubRouter());
            Msg badType = Msg.taskResult(SUPERVISOR, "agent://route-planner", TRACE,
                    null, false);
            assertThrows(com.smart.trip.common.exception.InvalidRequestException.class,
                    () -> agent.receive(badType));
        }
    }
}
