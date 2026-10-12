package com.smart.trip.server.admin;

/**
 * Agent 状态对外视图（C-005 AC-3 / 接口协议.md §1.5 {@code GET /api/v1/admin/agent/status}）。
 *
 * <p>由 {@link AgentStatusService} 从注册表映射而来，作为该管理端点的数据源；端点本体归 C-011。</p>
 *
 * @param agentUri Agent 标识 URI（{@code agent://<id>}）
 * @param status   运行状态名（{@code AgentStatus} 名称）
 * @param address  实例地址（{@code host:port}）
 */
public record AgentStatusView(String agentUri, String status, String address) {
}
