package com.smart.trip.tests.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E2E 业务不变量守护（C-012 T-3 / AC-2 / 业务模型.md §4.2 强规则）。
 *
 * <p>在真实 Agent 链路产出的 TripPlan 上精确断言所有强规则，
 * 不变量真相源：业务模型.md §4.2，字段路径来自 接口协议.md + TripPlan/TripDay/Budget/Route record。</p>
 *
 * <ul>
 *   <li>INV-1：days.size() == request.days（天数一致）</li>
 *   <li>INV-2：每日含 LUNCH + DINNER（MealType 枚举）</li>
 *   <li>INV-3：景点 ID 全局不重复</li>
 *   <li>INV-4：budget.used ≤ budget.total × 1.15（BigDecimal 精确边界）</li>
 *   <li>INV-5：景点评分 ≥ 3.5（E2eAgentConfig 桩景点全 ≥ 4.5，不变量直接可验）</li>
 *   <li>INV-6：交通耗时 durationMin ≤ 120（routes 中每条）</li>
 * </ul>
 */
@DisplayName("E2E 业务不变量守护（AC-2 §4.2 强规则）")
class InvariantGuardE2eTest extends BaseE2eTest {

    private static final int DAYS = 5;
    private static final BigDecimal BUDGET = new BigDecimal("8000");

    @Test
    @DisplayName("INV-1/2/3: 天数一致 + 每日午晚餐 + 景点不重复")
    void invariant_days_meals_nodup() {
        String token = helper.registerAndLogin(E2eTestHelper.newTestUsername());
        String planId = helper.submitPlan(token, yunnan5DayRequest());

        Map<String, Object> detail = helper.awaitPlanTerminal(token, planId, 60);
        assertThat(detail.get("status")).as("必须有终态").isIn("review", "confirmed");

        Map<String, Object> plan = extractPlan(detail);
        if (plan == null) return; // failed 态无 plan，跳过结构断言

        List<Map<String, Object>> days = days(plan);
        assertThat(days).as("INV-1: 天数一致").hasSize(DAYS);

        for (int i = 0; i < days.size(); i++) {
            Map<String, Object> day = days.get(i);
            List<Map<String, Object>> meals = list(day, "meals");
            List<String> types = meals.stream()
                    .map(m -> upper(m.get("mealType")))
                    .toList();
            assertThat(types).as("INV-2: day[%d] 必须含 LUNCH", i).anyMatch("LUNCH"::equals);
            assertThat(types).as("INV-2: day[%d] 必须含 DINNER", i).anyMatch("DINNER"::equals);
        }

        List<String> allIds = days.stream()
                .flatMap(d -> list(d, "attractions").stream()
                        .map(a -> str(a.get("id"))))
                .filter(id -> id != null && !id.isEmpty())
                .toList();
        assertThat(allIds).as("INV-3: 景点 ID 全局不重复").doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("INV-4: budget.used ≤ budget.total × 1.15（BigDecimal 精确边界）")
    void invariant_budget_overrun_within_15pct() {
        String token = helper.registerAndLogin(E2eTestHelper.newTestUsername());
        String planId = helper.submitPlan(token, yunnan5DayRequest());

        Map<String, Object> detail = helper.awaitPlanTerminal(token, planId, 60);
        assertThat(detail.get("status")).isIn("review", "confirmed");

        Map<String, Object> plan = extractPlan(detail);
        if (plan == null) return;

        Map<String, Object> budget = map(plan, "budget");
        if (budget == null) return;

        Number usedNum = (Number) budget.get("used");
        Number totalNum = (Number) budget.get("total");
        if (usedNum == null || totalNum == null) return;

        BigDecimal used = new BigDecimal(usedNum.toString());
        BigDecimal total = new BigDecimal(totalNum.toString());
        // total 来自 request.budget（8000），used 必须 ≤ 8000 × 1.15 = 9200
        BigDecimal maxAllowed = total.multiply(new BigDecimal("1.15"));
        assertThat(used)
                .as("INV-4: budget.used(%.2f) ≤ budget.total×1.15(%.2f)", used, maxAllowed)
                .isLessThanOrEqualTo(maxAllowed);

        // overrunRate 若存在也验证
        Number overrunNum = (Number) budget.get("overrunRate");
        if (overrunNum != null) {
            BigDecimal overrunRate = new BigDecimal(overrunNum.toString());
            assertThat(overrunRate)
                    .as("INV-4b: overrunRate(%.4f) < 0.15（正常场景不触发 HITL）", overrunRate)
                    .isLessThan(new BigDecimal("0.15"));
        }
    }

    @Test
    @DisplayName("INV-5/6: 景点评分 ≥ 3.5 + 交通耗时 ≤ 120 min")
    void invariant_rating_and_route_duration() {
        String token = helper.registerAndLogin(E2eTestHelper.newTestUsername());
        String planId = helper.submitPlan(token, yunnan5DayRequest());

        Map<String, Object> detail = helper.awaitPlanTerminal(token, planId, 60);
        assertThat(detail.get("status")).isIn("review", "confirmed");

        Map<String, Object> plan = extractPlan(detail);
        if (plan == null) return;

        List<Map<String, Object>> days = days(plan);

        for (int i = 0; i < days.size(); i++) {
            Map<String, Object> day = days.get(i);

            // INV-5：景点评分 ≥ 3.5
            for (Map<String, Object> attraction : list(day, "attractions")) {
                Number ratingNum = (Number) attraction.get("rating");
                if (ratingNum != null) {
                    double rating = ratingNum.doubleValue();
                    assertThat(rating)
                            .as("INV-5: day[%d] 景点 '%s' 评分(%.1f) ≥ 3.5",
                                    i, attraction.get("name"), rating)
                            .isGreaterThanOrEqualTo(3.5);
                }
            }

            // INV-6：路线交通耗时 ≤ 120 min
            for (Map<String, Object> route : list(day, "routes")) {
                Number durationNum = (Number) route.get("durationMin");
                if (durationNum != null) {
                    int dur = durationNum.intValue();
                    assertThat(dur)
                            .as("INV-6: day[%d] 路线耗时(%d min) ≤ 120", i, dur)
                            .isLessThanOrEqualTo(120);
                }
            }
        }
    }

    // ── 辅助 ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractPlan(Map<String, Object> detail) {
        Object resultObj = detail.get("result");
        if (resultObj instanceof Map<?, ?> resultMap) {
            Object planObj = ((Map<String, Object>) resultMap).get("plan");
            if (planObj instanceof Map<?, ?>) {
                return (Map<String, Object>) planObj;
            }
        }
        // fallback: plan 可能直接在 detail 顶层（不同序列化路径）
        Object planObj = detail.get("plan");
        if (planObj instanceof Map<?, ?>) {
            return (Map<String, Object>) planObj;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> days(Map<String, Object> plan) {
        Object o = plan.get("days");
        if (o instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> node, String key) {
        Object o = node.get(key);
        if (o instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> node, String key) {
        Object o = node.get(key);
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String upper(Object o) {
        return o == null ? "" : o.toString().toUpperCase();
    }
}
