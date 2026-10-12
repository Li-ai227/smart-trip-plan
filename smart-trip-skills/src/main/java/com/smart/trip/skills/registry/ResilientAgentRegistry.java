package com.smart.trip.skills.registry;

import com.smart.trip.skills.a2a.AgentId;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Optional;

/**
 * 带降级的 Agent 注册表（C-005 AC-3 / 边界 #3）。
 *
 * <p>包装一个主注册（如 Nacos 命名服务）与一个本地缓存（{@link InMemoryAgentRegistry}）：
 * 写操作以本地为必达、主注册为 best-effort；读操作优先主注册，主注册不可达时降级到本地缓存路由，
 * 保证 A2A 在 Nacos 故障时仍可解析既有 Agent 而非裸崩（边界 #3）。主注册异常以 WARNING 记录，
 * 不向上传播（降级语义），不静默吞没（编码规范 §7）。</p>
 *
 * @deprecated 自 C-005-B 起，迁移到框架 {@code io.agentscope.core.a2a.server.registry.AgentRegistryService}
 *     + {@link com.smart.trip.skills.a2a.server.FrameworkRegistryBridge}（ADR-008）。
 *     框架 {@code AgentRegistryService} 原生支持多注册表遍历。
 */
@Deprecated(since = "C-005-B", forRemoval = true)
public final class ResilientAgentRegistry implements AgentRegistry {

    private static final Logger LOG = System.getLogger(ResilientAgentRegistry.class.getName());

    private final AgentRegistry primary;
    private final AgentRegistry local;

    /**
     * 构造带降级的注册表。
     *
     * @param primary 主注册（如 Nacos），不可为 null
     * @param local   本地缓存注册（降级路由），不可为 null
     */
    public ResilientAgentRegistry(AgentRegistry primary, AgentRegistry local) {
        this.primary = primary;
        this.local = local;
    }

    @Override
    public void register(AgentRegistration registration) {
        local.register(registration);
        try {
            primary.register(registration);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "主注册不可达，注册降级为仅本地缓存: {0}", registration.agentId().uri(), ex);
        }
    }

    @Override
    public void deregister(AgentId agentId) {
        local.deregister(agentId);
        try {
            primary.deregister(agentId);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "主注册不可达，注销降级为仅本地缓存: {0}", agentId.uri(), ex);
        }
    }

    @Override
    public Optional<AgentRegistration> resolve(AgentId agentId) {
        try {
            Optional<AgentRegistration> fromPrimary = primary.resolve(agentId);
            return fromPrimary.isPresent() ? fromPrimary : local.resolve(agentId);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "主注册不可达，解析降级到本地缓存: {0}", agentId.uri(), ex);
            return local.resolve(agentId);
        }
    }

    @Override
    public List<AgentRegistration> list() {
        try {
            return primary.list();
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "主注册不可达，列举降级到本地缓存", ex);
            return local.list();
        }
    }
}
