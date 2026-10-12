package com.smart.trip.common.enums;

/**
 * Agent 运行状态（数据模型.md §3）。
 *
 * <p>对应 ReAct 循环阶段，用于可观测性与状态上报。</p>
 */
public enum AgentStatus {

    /** 空闲，未承接任务。 */
    IDLE,

    /** 思考中（推理/规划）。 */
    THINKING,

    /** 执行动作（调用 Tool / 委派）。 */
    ACTING,

    /** 观察结果。 */
    OBSERVING,

    /** 任务完成。 */
    DONE
}
