package com.smart.trip.skills.supervisor;

import com.smart.trip.common.a2a.Msg;

/**
 * 子 Agent 委派抽象（C-010 AC-5）：把 {@code TASK_ASSIGN} 发给指定子 Agent 并收回 {@code TASK_RESULT/TASK_ERROR}。
 *
 * <p>生产实现经 {@code AgentRegistry} 解析后调用 {@code receive(Msg)}；测试可注入桩。
 * Supervisor 仅依赖此接口，不直接 import 任何子 Agent 类（R1 禁止）。</p>
 */
@FunctionalInterface
public interface SubAgentDispatcher {

    /**
     * 向指定子 Agent 委派任务。
     *
     * @param agentUri 子 Agent URI（如 {@code agent://xhs-analyst}）
     * @param assign   委派消息（{@code TASK_ASSIGN}）
     * @return 子 Agent 返回的 {@code TASK_RESULT} 或 {@code TASK_ERROR}
     */
    Msg dispatch(String agentUri, Msg assign);
}
