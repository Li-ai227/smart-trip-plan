package com.smart.trip.server.config;

import com.smart.trip.server.health.NacosHealthIndicator;
import com.smart.trip.server.health.SocketNacosProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 健康检查装配（C-003 AC-5）。
 *
 * <p>注册 {@link NacosHealthIndicator}（健康组件 id 为 {@code nacos}）。Redis 侧由
 * spring-boot-starter-data-redis 的原生 {@code RedisHealthIndicator} 自动装配。
 * 测试 profile 通过 {@code trip.nacos-health.enabled=false} 关闭真实探测以替身化（AC-6）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class HealthConfig {

    /**
     * 构造 Nacos 健康指示器及其 TCP 连通性探针。
     *
     * @param serverAddr    Nacos 地址（host:port），默认本地 8848，可经 {@code NACOS_SERVER_ADDR} 覆盖
     * @param timeoutMillis 探测连接超时（毫秒）
     * @return Nacos 健康指示器
     */
    @Bean
    @ConditionalOnProperty(prefix = "trip.nacos-health", name = "enabled", matchIfMissing = true)
    public NacosHealthIndicator nacosHealthIndicator(
            @Value("${trip.nacos.server-addr:127.0.0.1:8848}") String serverAddr,
            @Value("${trip.nacos.connect-timeout-millis:1000}") int timeoutMillis) {
        return new NacosHealthIndicator(new SocketNacosProbe(serverAddr, timeoutMillis), serverAddr);
    }
}
