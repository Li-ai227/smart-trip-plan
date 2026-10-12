package com.smart.trip.common.model;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.PlanStatus;
import com.smart.trip.common.util.CollectionUtils;
import java.math.BigDecimal;
import java.util.List;

/**
 * 完整行程方案，系统最终交付物（数据模型.md §2.2 / 业务模型.md §2）。
 *
 * <p>业务不变量（业务模型.md §4）：{@code days.size() == request.days}、景点不重复；
 * 全局一致性校验归 OutputQualityGate（C-005），{@code requiresHITL} 由 Agent 发现并经 Server 对外暴露。</p>
 *
 * @param planId       方案 ID（格式 {@code P-yyyyMMdd-NNN}）
 * @param request      原始请求
 * @param days         每日行程，可空（构造时归一为不可变拷贝）
 * @param totalCost    总费用（元）
 * @param budget       预算与拆分
 * @param status       方案状态
 * @param requiresHITL 是否需人工介入
 */
public record TripPlan(
        String planId,
        TripPlanRequest request,
        List<TripDay> days,
        BigDecimal totalCost,
        Budget budget,
        PlanStatus status,
        boolean requiresHITL) {

    /**
     * 紧凑构造器：对可空集合做 null 安全的不可变拷贝（决策 D-5/D-6）。
     */
    public TripPlan {
        days = CollectionUtils.safeCopy(days);
    }
}
