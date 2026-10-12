package com.smart.trip.server.config;

import com.smart.trip.skills.memory.MemoryClearService;
import com.smart.trip.skills.memory.MemoryRecaller;
import com.smart.trip.skills.memory.MemoryRecallService;
import com.smart.trip.skills.observability.AgentMetrics;
import io.agentscope.core.memory.reme.ReMeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * ReMe 记忆层 Bean 装配（C-015）。
 *
 * <p>装配 {@link ReMeClient} 和 {@link MemoryRecallService}，
 * 供 SupervisorAgent 在编排前调用记忆召回。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ReMeConfig {

    /**
     * ReMe 客户端（连接 ReMe Server）。
     * 环境变量 {@code REME_BASE_URL} 配置地址，默认 {@code http://127.0.0.1:8002}。
     *
     * @param environment Spring Environment，注入 {@code REME_BASE_URL}
     * @return ReMeClient 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ReMeClient remeClient(Environment environment) {
        String baseUrl = environment.getProperty("REME_BASE_URL", "http://127.0.0.1:8002");
        return new ReMeClient(baseUrl);
    }

    /**
     * 记忆召回服务（实现 {@link MemoryRecaller} 接口）。
     * 供 SupervisorAgent 在编排前调用，将 {@link com.smart.trip.common.dto.MemoryContext}
     * 注入编排查询。注入 {@link AgentMetrics} 以记录召回延迟/命中/降级指标。
     *
     * @param remeClient ReMe 客户端
     * @param agentMetrics 指标埋点
     * @return MemoryRecallService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MemoryRecaller memoryRecaller(ReMeClient remeClient, AgentMetrics agentMetrics) {
        return new MemoryRecallService(remeClient, agentMetrics);
    }

    /**
     * 记忆清除服务（C-015 AC-10）。
     * 通过 HTTP DELETE 调用 ReMe Server 清除用户 workspace 的全部记忆，支撑合规删除。
     *
     * @param environment Spring Environment，注入 {@code REME_BASE_URL}
     * @return MemoryClearService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MemoryClearService memoryClearService(Environment environment) {
        String baseUrl = environment.getProperty("REME_BASE_URL", "http://127.0.0.1:8002");
        return new MemoryClearService(baseUrl);
    }
}
