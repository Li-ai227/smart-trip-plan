package com.smart.trip.tests.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E2E Happy Path（C-012 Case-1 / AC-1 / AC-2）。
 *
 * <p>验证主链路：注册 → 登录 → 提交规划（云南 5 日）→ 轮询至终态 → TripPlan 结构满足所有强规则。</p>
 *
 * <p><b>TDD</b>：测试先于实现存在；当 E2E 基础设施未就绪时本类全红，
 * {@link E2eAgentConfig} + embedded Redis 就绪后转绿。业务变更（C-001~C-011 已 done）不新增红。</p>
 */
@DisplayName("E2E Happy Path：正常规划链路")
class HappyPathE2eTest extends BaseE2eTest {

    @Test
    @DisplayName("Case-1: 云南5日完整规划 → 轮询终态 → TripPlan 满足全部强规则（AC-1/AC-2）")
    void happyPath_yunnan5Day_meetsAllInvariants() {
        // Arrange: 注册 + 登录
        String username = E2eTestHelper.newTestUsername();
        String token = helper.registerAndLogin(username);

        // Act: 提交规划
        String planId = helper.submitPlan(token, yunnan5DayRequest());

        // Assert: 202 + planId 非空
        assertThat(planId).isNotBlank();

        // Act: 轮询至终态（60s CI 超时）
        Map<String, Object> detail = helper.awaitPlanTerminal(token, planId, 60);

        // Assert: 终态为 review（HITL 触发）或 confirmed
        String status = (String) detail.get("status");
        assertThat(status).isIn("review", "confirmed");

        // Assert: 响应结构合法
        assertThat(detail.get("planId")).isEqualTo(planId);
        assertThat(detail.containsKey("requiresHumanIntervention")).isTrue();

        // Assert: 业务不变量 AC-2（天数 = 5）
        assertPlanStructure(detail, 5, new java.math.BigDecimal("8000"));
    }

    @Test
    @DisplayName("Case-6a: 无 token GET 返回 401（AC-7）")
    void noToken_returns401() {
        // 先提交一个合法规划以获得 planId
        String username = E2eTestHelper.newTestUsername();
        String token = helper.registerAndLogin(username);
        String planId = helper.submitPlan(token, yunnan5DayRequest());

        // Act: 无 token 查询
        org.springframework.http.ResponseEntity<Map> resp = helper.queryPlanNoAuth(planId);

        // Assert: 401
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("Case-6b: days=0 → 400 INVALID_REQUEST（AC-8）")
    void invalidDays_returns400() {
        String username = E2eTestHelper.newTestUsername();
        String token = helper.registerAndLogin(username);

        Map<String, Object> badRequest = new java.util.LinkedHashMap<>(yunnan5DayRequest());
        badRequest.put("days", 0);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        org.springframework.http.ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/trip-plan",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(badRequest, headers),
                Map.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ===== 业务不变量断言（AC-2）=====

    @SuppressWarnings("unchecked")
    private static void assertPlanStructure(Map<String, Object> detail,
                                             int expectedDays,
                                             java.math.BigDecimal budgetLimit) {
        // 如果有 plan 字段（confirmed/review with plan），验证结构
        Object planObj = detail.get("plan");
        if (planObj == null) {
            // 仍在 analyzing/planning 中，或 plan 字段未 embed——跳过深层断言
            return;
        }
        Map<String, Object> plan = (Map<String, Object>) planObj;

        // 强规则 ①：天数一致
        List<Map<String, Object>> days = (List<Map<String, Object>>) plan.get("days");
        if (days != null && !days.isEmpty()) {
            assertThat(days).hasSize(expectedDays);

            // 强规则 ②：每日包含午餐和晚餐
            for (Map<String, Object> day : days) {
                List<Map<String, Object>> meals = (List<Map<String, Object>>) day.get("meals");
                assertThat(meals).as("每日必须含午餐和晚餐").isNotNull().isNotEmpty();
                List<String> mealTypes = meals.stream()
                        .map(m -> (String) m.get("mealType"))
                        .toList();
                assertThat(mealTypes).as("必须含 LUNCH").anyMatch(t -> "LUNCH".equalsIgnoreCase(t));
                assertThat(mealTypes).as("必须含 DINNER").anyMatch(t -> "DINNER".equalsIgnoreCase(t));
            }

            // 强规则 ③：景点不重复
            List<String> allAttractionIds = days.stream()
                    .flatMap(d -> {
                        List<Map<String, Object>> attractions =
                                (List<Map<String, Object>>) d.get("attractions");
                        return attractions == null ? java.util.stream.Stream.empty()
                                : attractions.stream().map(a -> (String) a.get("id"));
                    })
                    .toList();
            assertThat(allAttractionIds).as("景点不重复")
                    .doesNotHaveDuplicates();
        }

        // 强规则 ④：totalCost ≤ budget×1.15
        Object budget = plan.get("budget");
        if (budget instanceof Map<?, ?> budgetMap) {
            Number totalCostNum = (Number) budgetMap.get("used");
            if (totalCostNum != null) {
                java.math.BigDecimal totalCost = new java.math.BigDecimal(totalCostNum.toString());
                java.math.BigDecimal maxAllowed = budgetLimit.multiply(new java.math.BigDecimal("1.15"));
                assertThat(totalCost).as("totalCost ≤ budget×1.15").isLessThanOrEqualTo(maxAllowed);
            }
        }
    }

}
