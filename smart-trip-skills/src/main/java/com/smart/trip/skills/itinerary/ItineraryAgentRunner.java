package com.smart.trip.skills.itinerary;

/**
 * Itinerary 原生 Agent 运行器接缝（C-008 增量 B）：驱动 {@code HarnessAgent}
 * 跑通一次行程编排，返回确定性权威结果 {@link ItineraryDesignResult}。
 *
 * <p>把"调用真实 LLM 的 HarnessAgent"与"A2A 适配层 {@code ItineraryAgent}"解耦：
 * Agent 只依赖本接缝，单测可注入桩；真实实现由 env key 接 LLM。</p>
 *
 * <p>运行失败应抛异常或返回 {@code null}，由 Agent 降级回确定性直路。</p>
 */
@FunctionalInterface
public interface ItineraryAgentRunner {

    /**
     * 驱动一次原生 HarnessAgent 行程编排。
     *
     * @param query 编排请求，非 null
     * @return 确定性权威结果；{@code null} 表示未产出权威结果（由调用方降级）
     * @throws Exception 运行失败（驱动调用方降级回确定性直路）
     */
    ItineraryDesignResult run(ItineraryDesignQuery query) throws Exception;
}
