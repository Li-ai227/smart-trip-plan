package com.smart.trip.common.a2a;

/**
 * A2A 消息类型（接口协议.md §2.2）。
 *
 * <p>枚举值为协同链路契约的一部分，新增需走 ADR/变更卡；删除或改语义属破坏性变更（§2.3）。</p>
 */
public enum MessageType {

    /** 委派任务：Supervisor → SubAgent（§2.1）。必含 traceId/timeoutMs/payload。 */
    TASK_ASSIGN,

    /** 返回结果：SubAgent → Supervisor（§2.2）。降级路径必须显式 {@code fallback=true}。 */
    TASK_RESULT,

    /** 可识别错误：子 Agent 失败时返回，禁止静默超时（§2 测试关注点）。 */
    TASK_ERROR,

    /** 心跳：用于存活探测。 */
    HEARTBEAT,

    /** 广播：一对多通知。 */
    BROADCAST
}
