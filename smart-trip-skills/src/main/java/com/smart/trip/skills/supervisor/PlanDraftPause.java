package com.smart.trip.skills.supervisor;

/**
 * 计划模式暂停载荷（C-018 AC-3 / T-14）：Supervisor 原生路
 * {@code agent.call(...)} 非阻塞返回，携带待人工确认的计划草案。
 *
 * <p>T-14（2026-07-11）按官方 programmatic API 重写：不再通过
 * {@code plan_exit → ToolUseBlock → ConfirmResult} 管道，改为前端
 * /intervene API + {@code agent.exitPlanMode(ctx)} 程序化退出。
 * {@code pendingConfirmationJson} 在 T-14 后始终为空串（保留字段以维持
 * {@link SupervisorAgentRunner#resume} 签名兼容）。</p>
 *
 * @param draftMarkdown           {@code plan_write} 落盘的计划草案全文（workspace
 *                                {@code plans/PLAN.md}）
 * @param pendingConfirmationJson  T-14 后始终为空（保留以维持 API 兼容）
 */
public record PlanDraftPause(String draftMarkdown, String pendingConfirmationJson) {
}
