package com.smart.trip.skills.trace;

import java.time.Instant;
import java.util.List;

/**
 * 编排汇总审计记录（C-010 trace 方案）。
 *
 * <p>每次编排完成写入一条，通过 traceId 与委派/LLM 日志关联。</p>
 *
 * @param traceId            全链路追踪 ID
 * @param planId             行程方案 ID
 * @param orchestrationPath  编排路径（NATIVE 或 DETERMINISTIC）
 * @param totalDurationMs    总耗时毫秒
 * @param fallbackCount      子 Agent 降级计数
 * @param hitlTriggered      是否触发 HITL
 * @param hitlReasons        HITL 触发原因（多条以逗号分隔）
 * @param finalPlanStatus    最终 PlanStatus
 * @param createdAt          创建时间
 */
public record TraceOrchestrationSummary(
        String traceId,
        String planId,
        String orchestrationPath,
        long totalDurationMs,
        int fallbackCount,
        boolean hitlTriggered,
        String hitlReasons,
        String finalPlanStatus,
        Instant createdAt) {

    /** 紧凑构造器：不可变字段归一。 */
    public TraceOrchestrationSummary {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不可为空");
        }
        if (planId == null) {
            planId = "";
        }
        if (orchestrationPath == null) {
            orchestrationPath = "UNKNOWN";
        }
        if (hitlReasons == null) {
            hitlReasons = "";
        }
        if (finalPlanStatus == null) {
            finalPlanStatus = "";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** 编排路径：原生 LLM 驱动。 */
    public static final String PATH_NATIVE = "NATIVE";

    /** 编排路径：确定性兜底。 */
    public static final String PATH_DETERMINISTIC = "DETERMINISTIC";

    /** 从 List 拼装 hitlReasons。 */
    public static String joinReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "";
        }
        return String.join(", ", reasons);
    }

    /** hitlReasons 最大长度。 */
    public static final int MAX_REASONS_LENGTH = 1000;
}
