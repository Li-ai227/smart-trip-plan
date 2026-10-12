package com.smart.trip.skills.registry;

import com.smart.trip.skills.a2a.AgentId;
import java.util.List;
import java.util.Optional;

/**
 * Agent 注册发现抽象（C-005 AC-3 / 工程结构.md §2.1 显式通信）。
 *
 * <p>统一"注册 / 注销 / 按 {@code agent://<id>} 解析定位 / 列举"四类能力。生产实现可接 Nacos
 * 命名服务，本地实现见 {@link InMemoryAgentRegistry}，并由 {@link ResilientAgentRegistry}
 * 在主注册不可达时降级（边界 #3）。{@code /api/v1/admin/agent/status} 的数据源即来自 {@link #list()}。</p>
 */
public interface AgentRegistry {

    /**
     * 注册（或覆盖）一个 Agent 实例。
     *
     * @param registration 注册条目，不可为 null
     */
    void register(AgentRegistration registration);

    /**
     * 注销一个 Agent 实例。
     *
     * @param agentId Agent 标识，不可为 null
     */
    void deregister(AgentId agentId);

    /**
     * 按标识解析定位 Agent。
     *
     * @param agentId Agent 标识，不可为 null
     * @return 命中返回注册条目，否则为空
     */
    Optional<AgentRegistration> resolve(AgentId agentId);

    /**
     * 列举当前所有注册的 Agent。
     *
     * @return 注册条目列表（可空集合而非 null）
     */
    List<AgentRegistration> list();
}
