package com.smart.trip.server.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.skills.a2a.AgentId;
import com.smart.trip.skills.registry.AgentRegistration;
import com.smart.trip.skills.registry.InMemoryAgentRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentStatusService} 单测：为 {@code GET /api/v1/admin/agent/status} 提供数据源（C-005 AC-3）。
 *
 * <p>接口本体归 C-011；本测试验证数据源把注册表映射为对外可读的 {@link AgentStatusView}。</p>
 */
class AgentStatusServiceTest {

    @Test
    @DisplayName("把注册表内 Agent 映射为状态视图（uri/status/address）")
    void mapsRegistrationsToViews() {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(new AgentRegistration(
                AgentId.of("route-planner"), "10.0.0.1:8080", AgentStatus.IDLE));
        registry.register(new AgentRegistration(
                AgentId.of("budget-controller"), "10.0.0.2:8080", AgentStatus.ACTING));

        AgentStatusService service = new AgentStatusService(registry);
        List<AgentStatusView> views = service.currentStatus();

        assertEquals(2, views.size());
        AgentStatusView route = views.stream()
                .filter(v -> v.agentUri().equals("agent://route-planner"))
                .findFirst().orElseThrow();
        assertEquals("IDLE", route.status());
        assertEquals("10.0.0.1:8080", route.address());
    }

    @Test
    @DisplayName("空注册表返回空视图列表，不抛错")
    void emptyRegistryReturnsEmpty() {
        AgentStatusService service = new AgentStatusService(new InMemoryAgentRegistry());
        assertTrue(service.currentStatus().isEmpty());
    }
}
