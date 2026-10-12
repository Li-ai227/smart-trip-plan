package com.smart.trip.tests.e2e;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * E2E 测试工具（C-012 T-1）。
 *
 * <p>封装：注册→登录（JWT token）、提交规划、轮询直到终态、清理。
 * 所有操作经 HTTP 层，验证完整链路行为。</p>
 */
public final class E2eTestHelper {

    private static final Set<String> TERMINAL_STATUSES = Set.of("review", "confirmed", "failed");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final String baseUrl;

    public E2eTestHelper(RestTemplate restTemplate, StringRedisTemplate redisTemplate, int port) {
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.baseUrl = "http://localhost:" + port;
    }

    /** 生成 test-{uuid} 格式用户名（C-012 设计约束）。 */
    public static String newTestUsername() {
        return "test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 注册用户并登录，返回 JWT accessToken。
     *
     * @param username test-{uuid} 格式用户名
     * @return accessToken 字符串
     */
    @SuppressWarnings("unchecked")
    public String registerAndLogin(String username) {
        String password = "Pass1234!";
        Map<String, String> regBody = Map.of(
                "username", username,
                "password", password,
                "nickname", username);
        ResponseEntity<Map> regResp = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/register", regBody, Map.class);
        if (regResp.getStatusCode() != HttpStatus.CREATED) {
            throw new IllegalStateException("注册失败: " + regResp.getStatusCode()
                    + " " + regResp.getBody());
        }

        Map<String, String> loginBody = Map.of("username", username, "password", password);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/login", loginBody, Map.class);
        if (loginResp.getStatusCode() != HttpStatus.OK) {
            throw new IllegalStateException("登录失败: " + loginResp.getStatusCode());
        }
        return (String) loginResp.getBody().get("accessToken");
    }

    /**
     * 提交规划请求，返回 planId。
     *
     * @param token   JWT accessToken
     * @param request 规划请求体（Map）
     * @return planId 字符串
     */
    @SuppressWarnings("unchecked")
    public String submitPlan(String token, Map<String, Object> request) {
        HttpHeaders headers = bearerHeaders(token);
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/api/v1/trip-plan",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Map.class);
        if (resp.getStatusCode() != HttpStatus.ACCEPTED) {
            throw new IllegalStateException("提交规划失败: " + resp.getStatusCode()
                    + " body=" + resp.getBody());
        }
        return (String) resp.getBody().get("planId");
    }

    /**
     * 轮询 GET /api/v1/trip-plan/{planId}，直到 status 进入终态（review/confirmed/failed）。
     *
     * @param token      JWT accessToken
     * @param planId     方案 ID
     * @param timeoutSec 最大等待秒数
     * @return 终态响应体
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> awaitPlanTerminal(String token, String planId, int timeoutSec) {
        HttpHeaders headers = bearerHeaders(token);
        final Map<String, Object>[] result = new Map[1];

        await()
                .atMost(timeoutSec, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    ResponseEntity<Map> resp = restTemplate.exchange(
                            baseUrl + "/api/v1/trip-plan/" + planId,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            Map.class);
                    if (resp.getStatusCode() == HttpStatus.OK) {
                        Map<String, Object> body = resp.getBody();
                        String status = (String) body.get("status");
                        if (TERMINAL_STATUSES.contains(status)) {
                            result[0] = body;
                            return true;
                        }
                    }
                    return false;
                });

        return result[0];
    }

    /**
     * 发送 HITL 介入请求。
     *
     * @param token  JWT accessToken
     * @param planId 方案 ID
     * @param action CONFIRM / REJECT / MODIFY
     * @return 响应体
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> intervene(String token, String planId, String action) {
        HttpHeaders headers = bearerHeaders(token);
        Map<String, Object> body = Map.of("action", action);
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/api/v1/trip-plan/" + planId + "/intervene",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("HITL intervene 失败: " + resp.getStatusCode()
                    + " body=" + resp.getBody());
        }
        return resp.getBody();
    }

    /**
     * 查询方案（带 token），不抛异常（直接返回 ResponseEntity 含状态码）。
     *
     * @param token  JWT accessToken
     * @param planId 方案 ID
     * @return ResponseEntity（保留状态码供断言）
     */
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map> queryPlan(String token, String planId) {
        HttpHeaders headers = bearerHeaders(token);
        return restTemplate.exchange(
                baseUrl + "/api/v1/trip-plan/" + planId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
    }

    /**
     * 查询方案（无 token，用于测试 401）。
     *
     * @param planId 方案 ID
     * @return ResponseEntity
     */
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map> queryPlanNoAuth(String planId) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/trip-plan/" + planId,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                Map.class);
    }

    /** @AfterEach 清理：flushAll embedded Redis（数据隔离）。 */
    public void flushRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private static HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
