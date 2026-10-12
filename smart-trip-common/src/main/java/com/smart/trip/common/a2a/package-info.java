/**
 * A2A（Agent-to-Agent）协同消息契约（接口协议.md §2 / 变更 C-005）。
 *
 * <p>本包提供项目自建、不依赖 AgentScope 的不可变消息信封 {@link com.smart.trip.common.a2a.Msg}
 * 与类型枚举 {@link com.smart.trip.common.a2a.MessageType}，作为各 Agent 与 Server 复用的协同契约
 * 真相源（决策 D-1/D-2）。{@code BaseAgent}、Nacos 注册发现、重试/限频/降级治理引擎与 OutputQualityGate
 * 等运行时部分归 {@code smart-trip-skills}，由 C-005 后续 task 落地。</p>
 */
package com.smart.trip.common.a2a;
