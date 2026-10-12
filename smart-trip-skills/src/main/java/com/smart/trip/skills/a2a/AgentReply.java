package com.smart.trip.skills.a2a;

/**
 * 子 Agent 业务回调产出（C-005 AC-5）。
 *
 * <p>承载业务结果与降级标记：{@link BaseAgent} 据此装配 {@code TASK_RESULT}，
 * 降级产出必须显式 {@code fallback=true}（接口协议.md §2.2/§2.3）。</p>
 *
 * @param result   业务结果，可空（降级时可为默认值或 null）
 * @param fallback 是否为降级产出
 * @deprecated 自 C-005-B 起，迁移到框架 {@code io.agentscope.core.event.AgentEvent}（ADR-008）。
 *     HarnessAgent 的 call() 返回 {@code Flux<AgentEvent>}，业务结果经 AgentEvent 承载。
 */
@Deprecated(since = "C-005-B", forRemoval = true)
public record AgentReply(Object result, boolean fallback) {

    /**
     * 正常（非降级）产出。
     *
     * @param result 业务结果，可空
     * @return 标记 {@code fallback=false} 的回复
     */
    public static AgentReply ok(Object result) {
        return new AgentReply(result, false);
    }

    /**
     * 降级产出，显式标记 {@code fallback=true}。
     *
     * @param result 降级默认值，可空
     * @return 标记 {@code fallback=true} 的回复
     */
    public static AgentReply fallback(Object result) {
        return new AgentReply(result, true);
    }
}
