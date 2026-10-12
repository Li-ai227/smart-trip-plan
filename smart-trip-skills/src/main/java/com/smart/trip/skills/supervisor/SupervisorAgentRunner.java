package com.smart.trip.skills.supervisor;

/**
 * Supervisor 原生 Agent 运行器接缝（C-010 AC-3）：驱动 {@code HarnessAgent}（主 LLM deepseek-v4-pro，
 * 降级 qwen3-max）经 {@code agent_spawn} 委派四个子 Agent，返回<b>确定性权威结果</b> {@link TripPlanResult}。
 *
 * <p>把"调用真实 LLM 的 HarnessAgent"与"A2A 适配层 {@code SupervisorAgent}"解耦：Agent 只依赖本接缝，
 * 单测可注入桩（确定性、无网络），真实实现 {@code SupervisorHarnessAgentFactory} 经 env key 接 LLM。</p>
 *
 * <p>实现约定：编排正确性来自确定性 {@link TripOrchestrationService}（经 {@code @Tool} 暴露并捕获），
 * 而非解析 LLM 自由文本——业务不变量（编排顺序、OutputQualityGate 复核、HITL 触发）绝不交给 LLM 裁决。
 * 运行失败（无 key/超时/LLM 未调用工具）应抛异常或返回 {@code null}，
 * 由 Agent 降级回确定性直路。</p>
 *
 * <p>C-018 AC-4：实现可选择支持 {@link #resume} 以恢复因 {@code plan_exit} 暂停的计划模式执行；
 * 不支持 resume 的实现应返回 {@code null}。</p>
 */
public interface SupervisorAgentRunner {

    /**
     * 驱动一次原生 HarnessAgent 编排。
     *
     * @param query 编排请求，非 null
     * @return 确定性权威结果；{@code null} 表示 LLM 未产出权威结果（由调用方降级）
     * @throws Exception 运行失败（驱动调用方降级回确定性直路）
     */
    TripPlanResult run(TripOrchestrationQuery query) throws Exception;

    /**
     * 恢复因 {@code plan_exit} 暂停的计划模式执行（C-018 AC-4）。
     *
     * <p>默认实现返回 {@code null}——不支持计划模式恢复的实现无需覆盖。</p>
     *
     * @param query 与规划态相同的编排请求（同一 planId）
     * @param pause 规划态返回的暂停载荷
     * @return 确定性权威结果；{@code null} 表示恢复后仍未产出
     * @throws Exception 运行失败
     */
    default TripPlanResult resume(TripOrchestrationQuery query, PlanDraftPause pause)
            throws Exception {
        return null;
    }
}
