package com.smart.trip.skills.trace;

import java.time.Instant;

/**
 * LLM 调用审计记录（C-010 trace 方案）。
 *
 * <p>每次 HarnessAgent LLM 调用写入一条，通过 traceId 与其他日志关联。</p>
 *
 * @param traceId      全链路追踪 ID
 * @param planId       行程方案 ID
 * @param modelId      模型标识（deepseek-v4-pro 或 qwen3-max）
 * @param promptSummary 提示词摘要（已脱敏 + 截断）
 * @param outputSummary 输出摘要（已截断）
 * @param durationMs   耗时毫秒
 * @param tokenEstimate token 用量估算（输入+输出）
 * @param createdAt    创建时间
 */
public record TraceLlmCallRecord(
        String traceId,
        String planId,
        String modelId,
        String promptSummary,
        String outputSummary,
        long durationMs,
        int tokenEstimate,
        Instant createdAt) {

    /** 紧凑构造器：不可变字段归一。 */
    public TraceLlmCallRecord {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不可为空");
        }
        if (planId == null) {
            planId = "";
        }
        if (modelId == null) {
            modelId = "unknown";
        }
        if (promptSummary == null) {
            promptSummary = "";
        }
        if (outputSummary == null) {
            outputSummary = "";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** 文本字段最大长度（超过则截断）。 */
    public static final int MAX_TEXT_LENGTH = 3000;
}
