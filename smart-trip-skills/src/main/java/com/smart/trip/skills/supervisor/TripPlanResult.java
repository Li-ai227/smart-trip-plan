package com.smart.trip.skills.supervisor;

import com.smart.trip.common.model.TripPlan;
import com.smart.trip.common.util.CollectionUtils;
import com.smart.trip.skills.trace.TraceOrchestrationSummary;
import java.util.List;

/**
 * 编排结果封装（C-010 AC-2 + C-012-C path 字段）。
 *
 * @param plan           完整行程方案
 * @param requiresHITL   是否需要人工介入
 * @param hitlReasons    HITL 触发原因列表
 * @param fallback       是否含降级产出
 * @param telemetry      可观测遥测
 * @param path           编排路径（NATIVE / DETERMINISTIC），缺省为 DETERMINISTIC
 * @param memoryFallback ReMe 记忆召回降级标记（C-015 AC-6：true 表示降级为无记忆规划）
 * @param planPause      计划模式暂停载荷（C-018 AC-3），非 null 表示 {@code plan_exit} 触发
 *                       HITL 暂停、等待人工确认，此时 {@code plan} 为 null；正常完成时为 null
 */
public record TripPlanResult(
        TripPlan plan,
        boolean requiresHITL,
        List<String> hitlReasons,
        boolean fallback,
        Telemetry telemetry,
        String path,
        boolean memoryFallback,
        PlanDraftPause planPause) {

    /** 编排路径常量（与 {@link TraceOrchestrationSummary} 对齐）。 */
    public static final String PATH_NATIVE = TraceOrchestrationSummary.PATH_NATIVE;
    public static final String PATH_DETERMINISTIC = TraceOrchestrationSummary.PATH_DETERMINISTIC;

    /**
     * 紧凑构造器：集合 null 安全不可变拷贝，遥测与 path 缺省归一。
     */
    public TripPlanResult {
        hitlReasons = CollectionUtils.safeCopy(hitlReasons);
        telemetry = telemetry == null ? Telemetry.none() : telemetry;
        path = (path == null || path.isBlank()) ? PATH_DETERMINISTIC : path;
    }

    /** 向后兼容构造器（C-018 前的既有 7 参调用点），{@code planPause} 默认 {@code null}。 */
    public TripPlanResult(TripPlan plan, boolean requiresHITL, List<String> hitlReasons,
            boolean fallback, Telemetry telemetry, String path, boolean memoryFallback) {
        this(plan, requiresHITL, hitlReasons, fallback, telemetry, path, memoryFallback, null);
    }

    /**
     * 可观测遥测载荷。
     *
     * @param subAgentFallbackCount 子 Agent 降级次数
     * @param hitlTriggered         是否触发 HITL
     * @param totalLatencyMs        端到端延迟（毫秒）
     */
    public record Telemetry(int subAgentFallbackCount, boolean hitlTriggered, long totalLatencyMs) {

        /** 无遥测信号。 */
        public static Telemetry none() {
            return new Telemetry(0, false, 0L);
        }
    }
}
