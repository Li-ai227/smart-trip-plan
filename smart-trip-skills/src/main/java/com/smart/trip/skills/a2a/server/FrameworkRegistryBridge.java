package com.smart.trip.skills.a2a.server;

import io.a2a.spec.AgentCard;
import io.agentscope.core.a2a.server.registry.AgentRegistry;
import io.agentscope.core.a2a.server.transport.TransportProperties;
import java.util.List;

/**
 * 自建→框架注册表桥接（C-005 增量 B AC-11 / D-11）。
 *
 * <p>实现框架 {@link AgentRegistry} 接口，作为 {@code AgentScopeA2aServer.Builder.withAgentRegistry()}
 * 的适配层。并存期间委托注册调用到内部 delegate（可为另一个框架 {@code AgentRegistry} 实现，
 * 如 Nacos 适配）；无 delegate 时仅作占位（registryName 仍可用，register 为 no-op）。</p>
 */
public final class FrameworkRegistryBridge implements AgentRegistry {

    private static final String REGISTRY_NAME = "trip-plan";

    private final AgentRegistry delegate;

    /**
     * 以指定 delegate 构建桥接。
     *
     * @param delegate 委托的框架 AgentRegistry 实现
     */
    public FrameworkRegistryBridge(AgentRegistry delegate) {
        this.delegate = delegate;
    }

    /**
     * 无 delegate 构建（占位模式，register 为 no-op）。
     */
    public FrameworkRegistryBridge() {
        this.delegate = null;
    }

    @Override
    public String registryName() {
        return REGISTRY_NAME;
    }

    @Override
    public void register(AgentCard agentCard, List<TransportProperties> transportProperties) {
        if (delegate != null) {
            delegate.register(agentCard, transportProperties);
        }
    }
}
