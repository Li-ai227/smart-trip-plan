/**
 * Supervisor 编排能力（C-010）：确定性顺序编排 + 原生 HarnessAgent 接缝。
 *
 * <p>核心类：{@link com.smart.trip.skills.supervisor.TripOrchestrationService}（确定性编排），
 * {@link com.smart.trip.skills.supervisor.TripOrchestrationTools}（{@code @Tool} 工具门面），
 * {@link com.smart.trip.skills.supervisor.SupervisorAgentRunner}（原生接缝），
 * {@link com.smart.trip.skills.supervisor.SupervisorHarnessAgentFactory}（HarnessAgent 工厂）。</p>
 */
package com.smart.trip.skills.supervisor;
