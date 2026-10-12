package com.smart.trip.tests.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * E2E 认证 + 数据隔离（C-012 Case-4 / AC-7）。
 *
 * <p>验证：无 token → 401；跨用户 403；并发 3 planId + 3 独立用户状态互不串扰。</p>
 */
@DisplayName("E2E 认证与数据隔离")
class AuthIsolationE2eTest extends BaseE2eTest {

    @Test
    @DisplayName("Case-4a: 用户A 查询 用户B 的 planId → 403 FORBIDDEN（AC-7）")
    void crossUserAccess_returns403() {
        // 用户 A 注册 + 提交规划
        String userA = E2eTestHelper.newTestUsername();
        String tokenA = helper.registerAndLogin(userA);
        String planId = helper.submitPlan(tokenA, yunnan5DayRequest());

        // 用户 B 注册 + 登录
        String userB = E2eTestHelper.newTestUsername();
        String tokenB = helper.registerAndLogin(userB);

        // 用户 B 查询用户 A 的 planId → 403
        ResponseEntity<Map> resp = helper.queryPlan(tokenB, planId);
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("Case-4b: 并发 3 planId × 3 用户 → 各自到终态，状态互不串扰（AC-7）")
    void concurrent3Plans_statesIsolated() throws Exception {
        int n = 3;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Callable<String[]>> tasks = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            tasks.add(() -> {
                String username = E2eTestHelper.newTestUsername();
                String token = helper.registerAndLogin(username);
                String planId = helper.submitPlan(token, yunnan5DayRequest());
                return new String[]{token, planId};
            });
        }

        List<Future<String[]>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        // 每个 planId 独立轮询至终态
        List<String[]> results = new ArrayList<>();
        for (Future<String[]> f : futures) {
            results.add(f.get());
        }

        List<String> statuses = new ArrayList<>();
        for (String[] tokenAndPlan : results) {
            String token = tokenAndPlan[0];
            String planId = tokenAndPlan[1];
            Map<String, Object> detail = helper.awaitPlanTerminal(token, planId, 90);
            statuses.add((String) detail.get("status"));
        }

        // 3 个规划各有终态
        assertThat(statuses).hasSize(n);
        statuses.forEach(s -> assertThat(s).isIn("review", "confirmed", "failed"));

        // 跨用户隔离：用户 0 查用户 1 的 planId → 403
        String tokenUser0 = results.get(0)[0];
        String planIdUser1 = results.get(1)[1];
        ResponseEntity<Map> crossResp = helper.queryPlan(tokenUser0, planIdUser1);
        assertThat(crossResp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("Case-6c: 无 token 访问 trip-plan 任意端点 → 401（AC-7）")
    void noToken_anyPlanEndpoint_returns401() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/trip-plan/nonexistent-plan-id",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }
}
