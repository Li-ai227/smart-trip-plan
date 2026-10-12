package com.smart.trip.server.admin;

import com.smart.trip.skills.registry.AgentRegistry;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Agent 状态查询服务（C-005 AC-3）。
 *
 * <p>为 {@code GET /api/v1/admin/agent/status} 提供数据源：把 {@link AgentRegistry} 中的注册条目
 * 映射为对外 {@link AgentStatusView}。仅做入口/门面所需的只读装配，不承载 Agent 业务规则
 * （工程结构.md §2.3 / R6）。端点本体（Controller）归 C-011。</p>
 */
@Service
public class AgentStatusService {

    private final AgentRegistry registry;

    /**
     * 构造状态查询服务。
     *
     * @param registry Agent 注册表，不可为 null
     */
    public AgentStatusService(AgentRegistry registry) {
        this.registry = registry;
    }

    /**
     * 返回当前所有 Agent 的状态视图。
     *
     * @return 状态视图列表（注册表为空时返回空列表）
     */
    public List<AgentStatusView> currentStatus() {
        return registry.list().stream()
                .map(reg -> new AgentStatusView(
                        reg.agentId().uri(), reg.status().name(), reg.address()))
                .toList();
    }
}
