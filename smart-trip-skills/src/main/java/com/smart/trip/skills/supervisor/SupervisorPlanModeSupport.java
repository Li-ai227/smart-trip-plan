package com.smart.trip.skills.supervisor;

import com.smart.trip.common.util.JsonUtils;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.workspace.plan.PlanModeManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link SupervisorHarnessAgentFactory} 的计划模式（C-018 + T-14 重写）纯 Java 装配辅助。
 *
 * <p>T-14（2026-07-11）按官方 programmatic API 重写：删除自研的
 * PERMISSION_ASKING → ToolUseBlock 提取 → ConfirmResult 构造管道，
 * 改为 {@code agent.enterPlanMode(ctx)}/{@code agent.exitPlanMode(ctx)}。
 * 详见 {@code .harness/changes/C-018-supervisor-plan-mode/change.md} SPIKE-2。</p>
 */
final class SupervisorPlanModeSupport {

    /**
     * 计划模式规划态系统提示词（T-14 重写）。
     *
     * <p>不再要求 LLM 调用 {@code plan_exit}——LLM 只需调用 {@code plan_write}
     * 写出执行计划然后停止。计划确认改用前端 /intervene API + 程序化
     * {@code agent.exitPlanMode(ctx)}。</p>
     */
    static final String PLAN_SYS_PROMPT =
            "你是智能旅游规划系统的主管 Agent（Supervisor），当前处于计划模式（只读规划阶段），"
                    + "任务是起草执行计划而不是直接执行。\n"
                    + "① 阅读下方旅行规划请求（目的地/天数/预算/人数/风格/偏好）。\n"
                    + "② 调用 plan_write 写出接下来将按顺序执行的四步计划：xhs-analyst 分析候选景点 → "
                    + "route-planner 规划路线 → itinerary-designer 编排行程 → budget-controller 核算费用，"
                    + "说明每一步的输入输出与关键不变量（评分≥3.5、预算红线≤预算×1.15）。\n"
                    + "③ 计划写完后<b>直接结束</b>，不要调用 plan_exit、不要等待、不要反复修改。\n"
                    + "【严禁】禁止调用 agent_spawn 或任何业务工具——规划阶段的 Toolkit 里没有这些工具，"
                    + "调用只会失败或产生一个拿不到真实数据的空转子 Agent，不要尝试。\n"
                    + "【严禁】禁止读取文件系统/源码——你的任务是基于下方请求内容起草计划，不需要探索代码库。";

    /** 计划草案文件名（{@code PlanModeManager} 固定写作 {@code <planDir>/PLAN.md}）。 */
    private static final String PLAN_DRAFT_FILE_NAME = "PLAN.md";

    private SupervisorPlanModeSupport() {
    }

    /**
     * 显式按 {@code planId} 隔离 Supervisor 会话。
     *
     * <p>以 {@code planId} 为 {@code sessionId} 构造 {@link RuntimeContext}，
     * 确保不同请求的 Agent 会话互不串扰（AgentScope 通过
     * {@code (userId, sessionId)} 路由 AgentStateStore 持久化）。</p>
     *
     * @param query 编排请求；{@code planId} 为 null 时退化为
     *              {@link RuntimeContext#empty()}
     */
    static RuntimeContext sessionContext(TripOrchestrationQuery query) {
        String planId = query.planId();
        if (planId == null || planId.isBlank()) {
            return RuntimeContext.empty();
        }
        RuntimeContext.Builder ctxBuilder = RuntimeContext.builder().sessionId(planId);
        if (query.userId() != null && !query.userId().isBlank()) {
            ctxBuilder.userId(query.userId());
        }
        return ctxBuilder.build();
    }

    /**
     * 计划模式规划态调用的用户提示词：请求内容 + 要求先 plan_write 后停止（T-14 重写）。
     *
     * <p>T-14：不再要求 LLM 调用 plan_exit——LLM 只需调用 plan_write 写出执行计划后直接结束。
     * 计划确认改用前端 /intervene API + 程序化 {@code agent.exitPlanMode(ctx)}。</p>
     *
     * @param query 编排请求（含 request/planId/userId）
     * @return 计划模式规划态用户消息
     */
    static Msg buildPlanPrompt(TripOrchestrationQuery query) {
        String requestJson = JsonUtils.toJson(query.request());
        String text = "请为以下旅行规划请求起草执行计划（当前是计划模式，只读，不要执行）：\n"
                + requestJson + "\n"
                + "调用 plan_write 写出四步执行计划（XHS→Route→Itinerary→Budget），"
                + "写完后直接结束，不要调用 plan_exit。";
        return Msg.builder().role(MsgRole.USER).textContent(text).build();
    }

    /**
     * 读取 workspace 下 {@code plans/PLAN.md} 草案全文。
     *
     * <p>文件缺失时不抛异常，返回空串——由上游 {@code runPlanningPhase()} 判断为
     * "LLM 未产出有效草案"并触发降级确定性直路 {@code fallback=true}。</p>
     *
     * @param runWorkspace 本次 run 的专属 workspace 根目录
     * @return 草案 Markdown 全文；文件缺失时返回空串
     * @throws IOException 读取文件时发生 IO 异常
     */
    static String readPlanDraft(Path runWorkspace) throws IOException {
        Path draftFile = runWorkspace
                .resolve(PlanModeManager.DEFAULT_PLAN_DIR)
                .resolve(PLAN_DRAFT_FILE_NAME);
        if (!Files.exists(draftFile)) {
            return "";
        }
        return Files.readString(draftFile);
    }
}
