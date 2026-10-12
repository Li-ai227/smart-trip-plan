package com.smart.trip.server.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * {@link NacosHealthIndicator} 健康判定测试（C-003 AC-5）。
 *
 * <p>用桩 {@link NacosProbe} 验证：可达→UP；不可达→DOWN 且标明组件地址；探测异常→DOWN。
 * 对应"Nacos 不可达时健康检查 DOWN 且标明组件"的边界与降级要求。</p>
 */
class NacosHealthIndicatorTest {

    private static final String ADDR = "127.0.0.1:8848";

    @Test
    @DisplayName("Nacos 可达 → UP 且含地址详情")
    void reachableProbeYieldsUp() {
        NacosHealthIndicator indicator = new NacosHealthIndicator(() -> true, ADDR);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(ADDR, health.getDetails().get("nacosServerAddr"));
    }

    @Test
    @DisplayName("Nacos 不可达 → DOWN 且标明组件与原因")
    void unreachableProbeYieldsDown() {
        NacosHealthIndicator indicator = new NacosHealthIndicator(() -> false, ADDR);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(ADDR, health.getDetails().get("nacosServerAddr"));
        assertTrue(health.getDetails().containsKey("reason"));
    }

    @Test
    @DisplayName("探测抛异常 → DOWN（降级不裸崩）")
    void throwingProbeYieldsDown() {
        NacosHealthIndicator indicator = new NacosHealthIndicator(() -> {
            throw new IllegalStateException("boom");
        }, ADDR);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(ADDR, health.getDetails().get("nacosServerAddr"));
    }
}
