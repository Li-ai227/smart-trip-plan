package com.smart.trip.common.enums;

/**
 * 行程方案生命周期状态（数据模型.md §3 / 接口协议.md §1.2 / 业务模型.md §6.1）。
 */
public enum PlanStatus {

    /** XHS / 初始理解阶段。 */
    ANALYZING,

    /** 路线与任务分解阶段。 */
    PLANNING,

    /** 计划模式草案已生成，等待人工确认后再委派子 Agent（C-018）。 */
    PLAN_REVIEW,

    /** 行程编排阶段。 */
    ASSEMBLING,

    /** 预算核算阶段。 */
    BUDGETING,

    /** 已生成草案，等待 HITL 或最终确认。 */
    REVIEW,

    /** 已确认可交付。 */
    CONFIRMED,

    /** 主链路失败，需重试或人工排查。 */
    FAILED
}
