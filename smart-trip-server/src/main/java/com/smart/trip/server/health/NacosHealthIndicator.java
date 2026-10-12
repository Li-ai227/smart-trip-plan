package com.smart.trip.server.health;

import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Nacos 健康指示器（C-003 AC-5）。
 *
 * <p>聚合进 {@code /actuator/health} 的 {@code nacos} 组件：可达则 UP，不可达或探测异常则 DOWN，
 * 并在 detail 中标明组件地址与失败原因，便于定位（NFR 可观测）。注册逻辑不在此处（归 C-005），
 * 本类仅就 Nacos 连通性做健康判定。</p>
 */
public class NacosHealthIndicator implements HealthIndicator {

    private final NacosProbe probe;
    private final String serverAddr;

    /**
     * 构造 Nacos 健康指示器。
     *
     * @param probe      连通性探针，不可为 null
     * @param serverAddr Nacos 服务地址（host:port），用于健康详情展示
     */
    public NacosHealthIndicator(NacosProbe probe, String serverAddr) {
        this.probe = Objects.requireNonNull(probe, "probe 不可为 null");
        this.serverAddr = serverAddr;
    }

    @Override
    public Health health() {
        try {
            if (probe.isReachable()) {
                return Health.up().withDetail("nacosServerAddr", serverAddr).build();
            }
            return Health.down()
                    .withDetail("nacosServerAddr", serverAddr)
                    .withDetail("reason", "Nacos 不可达")
                    .build();
        } catch (RuntimeException ex) {
            return Health.down(ex)
                    .withDetail("nacosServerAddr", serverAddr)
                    .build();
        }
    }
}
