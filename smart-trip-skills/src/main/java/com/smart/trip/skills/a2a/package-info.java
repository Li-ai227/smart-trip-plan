/**
 * A2A 协同运行时基座与 Agent 标识（C-005 T-2）。
 *
 * <p>{@code BaseAgent} 统一收发/生命周期/失败转 TASK_ERROR，{@code AgentId} 承载 {@code agent://<id>}
 * 标识。仅依赖 common 契约（Msg/枚举/异常），不耦合任何具体 Agent（R2）。包名用 {@code a2a} 而非
 * {@code agent}，避免与 agent 模块共享 {@code ..agent..} 切片而误触 ArchUnit R2。</p>
 */
package com.smart.trip.skills.a2a;
