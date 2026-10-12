package com.smart.trip.skills.registry;

import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.skills.a2a.AgentId;

/**
 * Agent 注册条目（C-005 AC-3 / 工程结构.md §2.2）。
 *
 * @param agentId Agent 唯一标识（{@code agent://<id>}）
 * @param address 运行实例地址（{@code host:port}）
 * @param status  上报的运行状态（可观测）
 */
public record AgentRegistration(AgentId agentId, String address, AgentStatus status) {
}
