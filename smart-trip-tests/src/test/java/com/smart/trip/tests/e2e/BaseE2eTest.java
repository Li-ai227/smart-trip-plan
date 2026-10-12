package com.smart.trip.tests.e2e;

import com.smart.trip.server.TripPlanServerApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * E2E 基础类（C-012 T-1）。
 *
 * <p>启动完整 Spring Boot 上下文（{@code RANDOM_PORT}），激活 e2e profile（embedded Redis）。
 * 每测试前重置 E2eTestHelper，每测试后 flushAll Redis（数据隔离）。</p>
 */
@SpringBootTest(
        classes = {TripPlanServerApplication.class, E2eAgentConfig.class, EmbeddedRedisConfig.class,
                E2eAuthConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
public abstract class BaseE2eTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    protected RestTemplate restTemplate;
    protected E2eTestHelper helper;

    @BeforeEach
    void setUpHelper() {
        restTemplate = new RestTemplate();
        // Do not throw on 4xx/5xx — tests assert status codes directly
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
        helper = new E2eTestHelper(restTemplate, redisTemplate, port);
    }

    @AfterEach
    void cleanRedis() {
        helper.flushRedis();
    }

    /** 云南 5 日行程请求（正常场景基准）。 */
    protected static java.util.Map<String, Object> yunnan5DayRequest() {
        return new java.util.LinkedHashMap<>(java.util.Map.of(
                "destination", "云南",
                "startDate", "2026-07-01",
                "days", 5,
                "budget", 8000,
                "headcount", 2,
                "travelStyle", "COUPLE",
                "preferences", java.util.List.of("自然风光", "历史文化")));
    }
}
