package com.smart.trip.skills.route;

import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RetryPolicy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RouteHarnessAgentFactory env-gated 集成测试（C-007 AC-12）。
 *
 * <p>验证真实 HarnessAgent（LLM + @Tool plan_routes + SKILL.md）端到端跑通路线规划。
 * 需要 {@code DEEPSEEK_API_KEY} 环境变量，无 key 时自动 skip，不伪装通过。</p>
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+",
        disabledReason = "需要 DEEPSEEK_API_KEY 环境变量才能运行真实 LLM 集成测试")
class RouteHarnessAgentFactoryIT {

    private static final GeoLocation LOC_A = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");
    private static final GeoLocation LOC_B = new GeoLocation(104.1461, 30.7327, "大熊猫基地", "成都");

    private RoutePlanningService service;

    @BeforeEach
    void setUp() {
        MapGeocoder geocoder = (addr, city) -> LOC_A;
        MapRouter router = (from, to) -> new Route(from, to, 10.0, 30, "驾车", false);
        GovernedExternalCaller caller = new GovernedExternalCaller(
                new RetryPolicy(0, 1), millis -> {});

        service = new RoutePlanningService(
                geocoder, router, null, name -> Optional.empty(), caller);
    }

    @Test
    void realLLM_callsPlanRoutes_returnsDeterministicResult() throws Exception {
        Path workspace = Path.of("target", "route-it-workspace");
        java.nio.file.Files.createDirectories(workspace);

        RouteHarnessAgentFactory factory =
                RouteHarnessAgentFactory.fromEnvironment(service, workspace);
        assertNotNull(factory, "应基于 DEEPSEEK_API_KEY 成功构建工厂");

        Attraction a1 = new Attraction("A-001", "宽窄巷子", LOC_A, 4.5, 120, BigDecimal.TEN, List.of());
        Attraction a2 = new Attraction("A-002", "大熊猫基地", LOC_B, 4.8, 90, BigDecimal.TEN, List.of());

        RoutePlanQuery query = RoutePlanQuery.of("成都", List.of(a1, a2));
        RoutePlanResult result = factory.run(query);

        // AC-12: 真实原生链路产出确定性路线（来自 @Tool plan_routes → RoutePlanningService）
        assertNotNull(result, "原生路应产出权威结果");
        assertFalse(result.fallback(), "确定性端口无降级");
        assertEquals(1, result.routes().size(), "2 景点 → 1 条路线");
        assertTrue(result.overLimitRoutes().isEmpty(), "30min < 120min");

        Route route = result.routes().get(0);
        assertEquals(10.0, route.distanceKm(), 0.1);
        assertEquals("驾车", route.transportMode());

        factory.close();
    }
}
