package com.smart.trip.server.config;

import com.smart.trip.skills.registry.AgentRegistry;
import com.smart.trip.skills.registry.InMemoryAgentRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A2A 基座装配（C-005 AC-3）。
 *
 * <p>装配 {@link AgentRegistry} 默认实现，作为 {@code /api/v1/admin/agent/status} 数据源底座。
 * 默认提供进程内 {@link InMemoryAgentRegistry}（亦为 Nacos 不可达时的本地降级路由，边界 #3）；
 * 接入 Nacos 命名服务的主注册与 {@code ResilientAgentRegistry} 包装归后续运行时集成，可经
 * {@link ConditionalOnMissingBean} 覆盖。本类仅做装配，不承载业务规则（R6）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class A2aConfig {

    /**
     * 装配默认 Agent 注册表（进程内实现）。
     *
     * @return 默认 {@link AgentRegistry} Bean
     */
    @Bean
    @ConditionalOnMissingBean(AgentRegistry.class)
    public AgentRegistry agentRegistry() {
        return new InMemoryAgentRegistry();
    }
}
