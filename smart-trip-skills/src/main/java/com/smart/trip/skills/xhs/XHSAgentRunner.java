package com.smart.trip.skills.xhs;

/**
 * XHS 原生 Agent 运行器接缝（C-006 增量 C）：驱动 {@code HarnessAgent}（主 LLM deepseek-v4-pro，
 * 降级 qwen3-max）跑通一次 XHS 分析,返回**确定性权威结果** {@link XHSAnalysisResult}。
 *
 * <p>把"调用真实 LLM 的 HarnessAgent"与"A2A 适配层 {@code XHSAnalysisAgent}"解耦：Agent 只依赖本接缝,
 * 单测可注入桩(确定性、无网络),真实实现 {@code XHSHarnessAgentFactory} 经 env key 接 LLM（ADR-009）。</p>
 *
 * <p>实现约定：候选正确性来自确定性 {@link XHSNoteAnalyzer}（经 {@code @Tool} 暴露并捕获),而非解析
 * LLM 自由文本——业务不变量(评分≥3.5、负面剔除、降级)绝不交给 LLM 裁决。运行失败（无 key/超时/
 * LLM 未调用工具）应抛异常或返回 {@code null},由 Agent 降级回确定性直路。</p>
 */
@FunctionalInterface
public interface XHSAgentRunner {

    /**
     * 驱动一次原生 HarnessAgent 分析。
     *
     * @param query 分析请求(目的地 + 偏好等),非 null
     * @return 确定性权威结果;{@code null} 表示 LLM 未产出权威结果(由调用方降级)
     * @throws Exception 运行失败(驱动调用方降级回确定性直路)
     */
    XHSAnalysisResult run(XHSAnalysisQuery query) throws Exception;
}
