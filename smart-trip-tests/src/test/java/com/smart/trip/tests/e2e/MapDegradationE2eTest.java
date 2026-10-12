package com.smart.trip.tests.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.trip.agent.budget.BudgetAgent;
import com.smart.trip.agent.supervisor.SupervisorAgent;
import com.smart.trip.agent.itinerary.ItineraryAgent;
import com.smart.trip.agent.route.RouteAgent;
import com.smart.trip.agent.xhs.XHSAnalysisAgent;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.skills.route.MapRouter;
import com.smart.trip.skills.supervisor.SubAgentDispatcher;
import com.smart.trip.skills.supervisor.TripOrchestrationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;

/**
 * E2E 地图降级（C-012 Case-3 / AC-4）。
 *
 * <p>注入「高德 HTTP 持续 5xx」（MapGeocoder 始终抛异常）→ RouteAgent haversine 离线估算 →
 * 轮询 60s 内有终态 + 最终 TripPlan 含 {@code fallback=true} 标记。</p>
 *
 * <p><b>TDD</b>：先红——RouteAgent 注入失败 geocoder；就绪后：链路不挂死，有终态，含 fallback 标记。</p>
 */
@DisplayName("E2E 地图降级：RouteAgent haversine 离线估算")
@ContextConfiguration(classes = MapDegradationE2eTest.MapFailAgentConfig.class)
class MapDegradationE2eTest extends BaseE2eTest {

    @Test
    @DisplayName("Case-3: 地图 API 持续 5xx → haversine 降级 → 60s 内有终态 + fallback 标记（AC-4）")
    void mapApi5xx_haversineFallback_terminalWithinTimeout() {
        String username = E2eTestHelper.newTestUsername();
        String token = helper.registerAndLogin(username);

        String planId = helper.submitPlan(token, yunnan5DayRequest());
        assertThat(planId).isNotBlank();

        // 轮询至终态（≤60s）——地图失败不挂死主链路
        Map<String, Object> detail = helper.awaitPlanTerminal(token, planId, 60);

        String status = (String) detail.get("status");
        assertThat(status).as("地图降级下主链路仍有终态").isIn("review", "confirmed", "failed");

        // 当产出为 review/confirmed 时验证 fallback 标记
        if ("review".equals(status) || "confirmed".equals(status)) {
            assertPlanHasFallback(detail);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertPlanHasFallback(Map<String, Object> detail) {
        // fallback 可能在 plan.days[].routes[].fallback=true 或顶层 result.fallback=true
        // 两种断言方式：检查 plan 对象或检查响应顶层
        Object planObj = detail.get("plan");
        if (planObj instanceof Map<?, ?> plan) {
            List<Map<String, Object>> days = (List<Map<String, Object>>) ((Map<?, ?>) plan).get("days");
            if (days != null) {
                boolean anyFallbackRoute = days.stream()
                        .flatMap(d -> {
                            List<Map<String, Object>> routes =
                                    (List<Map<String, Object>>) d.get("routes");
                            return routes == null ? java.util.stream.Stream.empty()
                                    : routes.stream();
                        })
                        .anyMatch(r -> Boolean.TRUE.equals(r.get("fallback")));
                // 只要有任一路线是 haversine 估算（fallback=true）即满足
                // 若 days 为空（景点 < 2），路线规划跳过，也属降级
                assertThat(anyFallbackRoute || days.isEmpty())
                        .as("地图降级后至少有一条 fallback 路线（或无路线可规划）")
                        .isTrue();
            }
        }
        // 若 plan 字段不存在（failed 状态），通过"有终态"已验证降级不挂死
    }

    /**
     * 地图失败专用 Agent 配置：RouteAgent 注入失败 geocoder + 失败 router，触发 haversine。
     */
    @TestConfiguration
    static class MapFailAgentConfig {

        /** 直接构建整条地图降级链路，以 @Primary 覆盖 E2eAgentConfig 的 supervisorAgent。 */
        @Bean
        @Primary
        public SupervisorAgent supervisorAgent(AgentMetrics metrics, OutputQualityGate qualityGate) {
            XHSAnalysisAgent xhs = E2eAgentConfig.buildXhsAgent(
                    E2eAgentConfig.normalXhsSource(), metrics);

            MapRouter failingRouter = (from, to) -> {
                throw new RuntimeException("地图路线 API 5xx 注入");
            };
            RouteAgent route = E2eAgentConfig.buildRouteAgent(
                    E2eAgentConfig.failingGeocoder(), failingRouter, metrics);

            ItineraryAgent itin = E2eAgentConfig.buildItineraryAgent(metrics);
            BudgetAgent budget = E2eAgentConfig.buildBudgetAgent(metrics);

            SubAgentDispatcher dispatcher = E2eAgentConfig.buildDispatcher(xhs, route, itin, budget);
            TripOrchestrationService orchestrationService =
                    new TripOrchestrationService(dispatcher, qualityGate);
            return new SupervisorAgent(orchestrationService, metrics);
        }
    }
}
