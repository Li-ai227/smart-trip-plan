package com.smart.trip.common.model;

import java.math.BigDecimal;

/**
 * 预算与费用拆分（数据模型.md §2.4 / 业务模型.md §2）。
 *
 * <p>业务不变量（业务模型.md §4）：总费用 ≤ 预算 × 1.15，超支 ≥15% 触发 HITL；
 * 临界判定归 BudgetAgent（C-009）。{@code overrunRate} 为超支率（可为负，表示结余）。</p>
 *
 * @param total       总预算（元）
 * @param used        已用费用（元）
 * @param overrunRate 超支率（如 {@code -0.02} 表示低于预算 2%）
 * @param breakdown   四类费用拆分
 */
public record Budget(
        BigDecimal total,
        BigDecimal used,
        BigDecimal overrunRate,
        BudgetBreakdown breakdown) {
}
