package com.smart.trip.skills.trace;

import java.time.Instant;

/**
 * 子 Agent 委派审计记录（C-010 trace 方案）。
 *
 * <p>每次 agent_spawn 或确定性 dispatch 写入一条，通过 traceId 与其他日志关联。</p>
 *
 * @param traceId        全链路追踪 ID
 * @param planId         行程方案 ID
 * @param parentAgentId  委派方 Agent 标识（supervisor）
 * @param subAgentId     被委派子 Agent 标识
 * @param taskType       任务类型（XHS_ANALYSIS/ROUTE_PLANNING/ITINERARY_DESIGN/COST_CALCULATION）
 * @param taskSummary    委派任务内容摘要（已脱敏 + 截断）
 * @param responseSummary 响应摘要（已截断）
 * @param durationMs     耗时毫秒
 * @param fallback       是否降级
 * @param errorMessage   错误信息（正常时为 null）
 * @param createdAt      创建时间
 */
public record TraceDelegationRecord(
        String traceId,
        String planId,
        String parentAgentId,
        String subAgentId,
        String taskType,
        String taskSummary,
        String responseSummary,
        long durationMs,
        boolean fallback,
        String errorMessage,
        Instant createdAt) {

    /** 紧凑构造器：不可变字段归一。 */
    public TraceDelegationRecord {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不可为空");
        }
        if (planId == null) {
            planId = "";
        }
        if (taskSummary == null) {
            taskSummary = "";
        }
        if (responseSummary == null) {
            responseSummary = "";
        }
        if (errorMessage == null) {
            errorMessage = "";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** 文本字段最大长度（超过则截断）。 */
    public static final int MAX_TEXT_LENGTH = 2000;
}
