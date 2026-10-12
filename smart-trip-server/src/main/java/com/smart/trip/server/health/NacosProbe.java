package com.smart.trip.server.health;

/**
 * Nacos 连通性探针抽象（C-003）。
 *
 * <p>把"如何探测 Nacos 可达"与"健康判定逻辑"解耦，便于 {@link NacosHealthIndicator}
 * 在单测中注入桩实现（编码规范 §8.2 可测试性设计）。生产实现见 {@link SocketNacosProbe}。</p>
 */
public interface NacosProbe {

    /**
     * 探测 Nacos 是否可达。
     *
     * @return 可达返回 {@code true}，否则 {@code false}
     */
    boolean isReachable();
}
