package com.smart.trip.common.dto;

import com.smart.trip.common.util.CollectionUtils;
import java.util.List;

/**
 * 记忆召回上下文（C-015 AC-4 / AC-6）。
 *
 * <p>封装从 ReMe Server 召回的三类记忆，注入 {@code TripOrchestrationQuery} 透传给子 Agent。
 * 召回失败时 {@code memoryFallback=true}，子 Agent 仍可正常规划。</p>
 *
 * @param userProfiles   用户偏好记忆（travelStyle / preferences 历史），可空
 * @param historyPlans   历史行程记忆（目的地 / 景点 / 费用摘要），可空
 * @param attractionKb   景点知识记忆（XHS 分析沉淀），可空
 * @param memoryFallback 是否降级（ReMe 不可用时为 true）
 */
public record MemoryContext(
        List<String> userProfiles,
        List<String> historyPlans,
        List<String> attractionKb,
        boolean memoryFallback) {

    /** 空记忆上下文（首次用户 / 无召回命中）。 */
    public static final MemoryContext EMPTY = new MemoryContext(List.of(), List.of(), List.of(), false);

    /** 降级空记忆上下文（ReMe 不可用）。 */
    public static final MemoryContext FALLBACK = new MemoryContext(List.of(), List.of(), List.of(), true);

    /** 紧凑构造器：null 安全拷贝（与 TripPlanRequest 保持一致）。 */
    public MemoryContext {
        userProfiles = CollectionUtils.safeCopy(userProfiles);
        historyPlans = CollectionUtils.safeCopy(historyPlans);
        attractionKb = CollectionUtils.safeCopy(attractionKb);
    }

    /**
     * 是否无任何有效记忆（三类列表均为空且非降级）。
     *
     * @return true 表示无记忆可用
     */
    public boolean isEmpty() {
        return userProfiles.isEmpty() && historyPlans.isEmpty() && attractionKb.isEmpty();
    }
}
