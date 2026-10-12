package com.smart.trip.skills.registry;

import com.smart.trip.skills.a2a.AgentId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 Agent 注册表（{@link AgentRegistry} 本地实现，C-005 AC-3）。
 *
 * <p>线程安全（{@link ConcurrentHashMap}）。既可独立用于单机/测试，也作为
 * {@link ResilientAgentRegistry} 在主注册（Nacos）不可达时的本地缓存路由（边界 #3）。</p>
 *
 * @deprecated 自 C-005-B 起，迁移到框架 {@code io.agentscope.core.a2a.server.registry.AgentRegistry}
 *     + {@code AgentRegistryService}（ADR-008）。框架注册表由 {@code AgentScopeA2aServer} 统一管理。
 */
@Deprecated(since = "C-005-B", forRemoval = true)
public final class InMemoryAgentRegistry implements AgentRegistry {

    private final Map<String, AgentRegistration> registrations = new ConcurrentHashMap<>();

    @Override
    public void register(AgentRegistration registration) {
        registrations.put(registration.agentId().id(), registration);
    }

    @Override
    public void deregister(AgentId agentId) {
        registrations.remove(agentId.id());
    }

    @Override
    public Optional<AgentRegistration> resolve(AgentId agentId) {
        return Optional.ofNullable(registrations.get(agentId.id()));
    }

    @Override
    public List<AgentRegistration> list() {
        return List.copyOf(registrations.values());
    }
}
