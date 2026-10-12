package com.smart.trip.skills.supervisor;

import com.smart.trip.common.dto.MemoryContext;
import com.smart.trip.common.dto.TripPlanRequest;

/**
 * 编排请求封装（C-010 T-1 / C-015 T-2 扩展 userId + memoryContext / C-017 FIX-3 扩展 traceId）。
 *
 * @param request       用户规划请求（含 destination/days/budget/headcount/travelStyle/preferences）
 * @param planId        方案 ID，由 TripPlanFacade 在提交时生成并透传；可为 null（测试/降级场景）
 * @param userId        用户 ID，ReMe workspace 隔离维度；可为 null（测试/降级场景）
 * @param memoryContext 记忆召回上下文，可为 null（首次用户 / 召回未完成时）
 * @param traceId       全链路审计追踪 ID，由 SupervisorAgent 透传自 A2A 信封；可为 null
 *                      （测试/降级场景，此时下游按需自生成，不贯穿全链路）
 */
public record TripOrchestrationQuery(TripPlanRequest request, String planId,
                                     String userId, MemoryContext memoryContext,
                                     String traceId) {

    /** 无 planId 构造器（向后兼容测试与降级路径）。 */
    public TripOrchestrationQuery(TripPlanRequest request) {
        this(request, null, null, null, null);
    }

    /** 无 userId/memoryContext/traceId 构造器（向后兼容既有调用方）。 */
    public TripOrchestrationQuery(TripPlanRequest request, String planId) {
        this(request, planId, null, null, null);
    }

    /** 无 traceId 构造器（向后兼容 C-015 既有调用方）。 */
    public TripOrchestrationQuery(TripPlanRequest request, String planId,
                                  String userId, MemoryContext memoryContext) {
        this(request, planId, userId, memoryContext, null);
    }
}
