package com.smart.trip.common.model;

import java.math.BigDecimal;

/**
 * 预算四类拆分（数据模型.md §2.4 / 接口协议.md §1.2：{@code breakdown{transport,hotel,food,ticket}}）。
 *
 * <p>强类型承载固定四类维度（决策 D-4），消除 Map 魔法字符串键；金额用 {@link BigDecimal} 保精度。</p>
 *
 * @param transport 交通费用（元）
 * @param hotel     住宿费用（元）
 * @param food      餐饮费用（元）
 * @param ticket    门票费用（元）
 */
public record BudgetBreakdown(
        BigDecimal transport,
        BigDecimal hotel,
        BigDecimal food,
        BigDecimal ticket) {
}
