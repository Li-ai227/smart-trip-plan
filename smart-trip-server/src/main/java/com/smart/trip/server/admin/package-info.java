/**
 * 管理与状态查询数据源（C-005 T-3 server 侧）。
 *
 * <p>{@code AgentStatusService} 把 {@code AgentRegistry} 映射为对外 {@code AgentStatusView}，
 * 作为 {@code GET /api/v1/admin/agent/status} 数据源；端点本体（Controller）归 C-011。
 * 仅做只读装配，不承载 Agent 业务规则（R6）。</p>
 */
package com.smart.trip.server.admin;
