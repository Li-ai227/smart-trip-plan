package com.smart.trip.tests.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.trip.agent.budget.BudgetAgent;
import com.smart.trip.agent.supervisor.SupervisorAgent;
import com.smart.trip.agent.itinerary.ItineraryAgent;
import com.smart.trip.agent.route.RouteAgent;
import com.smart.trip.agent.xhs.XHSAnalysisAgent;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.skills.budget.BudgetAgentRunner;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.budget.BudgetCalculationService;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.skills.supervisor.SubAgentDispatcher;
import com.smart.trip.skills.supervisor.TripOrchestrationService;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;

/**
 * E2E HITL（C-012 Case-2 / AC-3）：超预算≥15% → review + requiresHumanIntervention=true → CONFIRM → confirmed。
 *
 * <p><b>TDD</b>：先红——BudgetAgent 注入超预算桩；就绪后：HITL 状态机流转全绿。</p>
 */
@DisplayName("E2E HITL：超预算场景状态机流转")
@ContextConfiguration(classes = HitlE2eTest.HitlAgentConfig.class)
class HitlE2eTest extends BaseE2eTest {

    @Test
    @DisplayName("Case-2: 超预算≥15% → status=review + requiresHumanIntervention=true → CONFIRM → confirmed")
    void overBudget15pct_triggersHitl_confirmLeadsToConfirmed() {
        String username = E2eTestHelper.newTestUsername();
        String token = helper.registerAndLogin(username);

        // budget=8000，HITL 桩注入 totalCost=9600 (overrunRate=0.20，严格超 ×1.15 边界)
        Map<String, Object> request = new java.util.LinkedHashMap<>(yunnan5DayRequest());
        request.put("budget", 8000);

        String planId = helper.submitPlan(token, request);
        assertThat(planId).isNotBlank();

        // 轮询至终态
        Map<String, Object> detail = helper.awaitPlanTerminal(token, planId, 60);

        // HITL 触发
        assertThat(detail.get("status")).isEqualTo("review");
        assertThat(detail.get("requiresHumanIntervention")).isEqualTo(true);

        // CONFIRM → confirmed
        Map<String, Object> afterConfirm = helper.intervene(token, planId, "CONFIRM");
        assertThat(afterConfirm.get("status")).isEqualTo("confirmed");
        assertThat(afterConfirm.get("requiresHumanIntervention")).isEqualTo(false);
    }

    @Test
    @DisplayName("Case-2b: 已 confirmed 再 intervene → 400 非法转换")
    void alreadyConfirmed_intervene_returns400() {
        String username = E2eTestHelper.newTestUsername();
        String token = helper.registerAndLogin(username);

        String planId = helper.submitPlan(token, yunnan5DayRequest());
        helper.awaitPlanTerminal(token, planId, 60);

        // 第一次 CONFIRM
        try {
            helper.intervene(token, planId, "CONFIRM");
        } catch (Exception ignored) {
        }

        // 第二次 intervene → 400
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        org.springframework.http.ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/trip-plan/" + planId + "/intervene",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(Map.of("action", "CONFIRM"), headers),
                Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    /**
     * 超预算 15%（含等号）专用 Agent 配置：BudgetAgent 注入返回 overrunRate=0.15 的桩服务。
     *
     * <p>以 {@code @Primary} 覆盖 {@link E2eAgentConfig} 中的 SubAgentDispatcher。</p>
     */
    @TestConfiguration
    static class HitlAgentConfig {

        /** 直接构建整条 HITL 链路（超预算 Budget 桩），以 @Primary 覆盖 E2eAgentConfig 的 supervisorAgent。 */
        @Bean
        @Primary
        public SupervisorAgent supervisorAgent(AgentMetrics metrics, OutputQualityGate qualityGate) {
            XHSAnalysisAgent xhs = E2eAgentConfig.buildXhsAgent(
                    E2eAgentConfig.normalXhsSource(), metrics);
            RouteAgent route = E2eAgentConfig.buildRouteAgent(
                    E2eAgentConfig.normalGeocoder(), E2eAgentConfig.normalRouter(), metrics);
            ItineraryAgent itin = E2eAgentConfig.buildItineraryAgent(metrics);

            BudgetAgentRunner overBudgetRunner = query -> {
                BigDecimal requestBudget = query.request().budget();
                // totalCost > budget×1.15 (OutputQualityGate 严格大于，存根必须严格超过边界)
                BigDecimal totalCost = requestBudget.multiply(new BigDecimal("1.20"));
                BigDecimal overrunRate = new BigDecimal("0.20");
                BigDecimal transport = totalCost.multiply(new BigDecimal("0.3"));
                BigDecimal hotel = totalCost.multiply(new BigDecimal("0.4"));
                BigDecimal food = totalCost.multiply(new BigDecimal("0.2"));
                BigDecimal ticket = totalCost.multiply(new BigDecimal("0.1"));
                return new BudgetCalculationResult(
                        totalCost, transport, hotel, food, ticket,
                        overrunRate,
                        java.util.List.of(),
                        true,
                        "超支率 0.2000（≥15%），住宿费用占比最高",
                        java.util.List.of("建议降档住宿标准"),
                        false,
                        new BudgetCalculationResult.Telemetry(0, true));
            };
            BudgetAgent budget = new BudgetAgent(overBudgetRunner, new BudgetCalculationService(), metrics);

            SubAgentDispatcher dispatcher = E2eAgentConfig.buildDispatcher(xhs, route, itin, budget);
            TripOrchestrationService orchestrationService =
                    new TripOrchestrationService(dispatcher, qualityGate);
            return new SupervisorAgent(orchestrationService, metrics);
        }
    }
}
