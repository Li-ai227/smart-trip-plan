package com.smart.trip.skills.budget;

import com.smart.trip.common.util.CollectionUtils;
import java.math.BigDecimal;
import java.util.List;

/**
 * 费用核算结果（C-009 AC-2）。
 *
 * @param totalCost        总费用
 * @param transport        交通费用合计
 * @param hotel            住宿费用合计
 * @param food             餐饮费用合计
 * @param ticket           门票费用合计
 * @param overrunRate      超支率（(totalCost - budget) / budget）
 * @param dailyBreakdowns  逐日费用明细
 * @param requiresHITL     是否需要人工介入
 * @param hitlReason       HITL 触发原因
 * @param adjustmentAdvice 调整建议
 * @param fallback         是否含降级估算
 * @param telemetry        可观测遥测
 */
public record BudgetCalculationResult(
        BigDecimal totalCost,
        BigDecimal transport,
        BigDecimal hotel,
        BigDecimal food,
        BigDecimal ticket,
        BigDecimal overrunRate,
        List<DailyCostDetail> dailyBreakdowns,
        boolean requiresHITL,
        String hitlReason,
        List<String> adjustmentAdvice,
        boolean fallback,
        Telemetry telemetry) {

    /**
     * 紧凑构造器：集合 null 安全不可变拷贝，遥测缺省归一。
     */
    public BudgetCalculationResult {
        dailyBreakdowns = CollectionUtils.safeCopy(dailyBreakdowns);
        adjustmentAdvice = CollectionUtils.safeCopy(adjustmentAdvice);
        telemetry = telemetry == null ? Telemetry.none() : telemetry;
    }

    /**
     * 可观测遥测载荷。
     *
     * @param fallbackItemCount 降级估算项数
     * @param hitlTriggered     是否触发 HITL
     */
    public record Telemetry(int fallbackItemCount, boolean hitlTriggered) {

        /** 无遥测信号。 */
        public static Telemetry none() {
            return new Telemetry(0, false);
        }
    }
}
