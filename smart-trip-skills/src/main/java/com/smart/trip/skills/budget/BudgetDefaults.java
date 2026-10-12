package com.smart.trip.skills.budget;

import java.math.BigDecimal;

/**
 * 费用估算可配置常量（C-009 AC-3/AC-6 设计约束）。
 *
 * <p>集中管理保守估算系数与 HITL 阈值，不散落硬编码。</p>
 */
public final class BudgetDefaults {

    /** 住宿单价（元/人·天）。 */
    public static final BigDecimal HOTEL_UNIT_PRICE = new BigDecimal("200");

    /** 交通单价（元/km）。 */
    public static final BigDecimal TRANSPORT_COST_PER_KM = BigDecimal.ONE;

    /** 交通每日保守估算（元/天），路线缺失时使用。 */
    public static final BigDecimal TRANSPORT_DAILY_FALLBACK = new BigDecimal("100");

    /** 餐饮保守单价（元/人·餐），avgPrice 缺失时使用。 */
    public static final BigDecimal MEAL_FALLBACK_PRICE = new BigDecimal("30");

    /** HITL 触发阈值：overrunRate >= 0.15（含等号）。 */
    public static final BigDecimal OVERRUN_THRESHOLD = new BigDecimal("0.15");

    private BudgetDefaults() {
    }
}
