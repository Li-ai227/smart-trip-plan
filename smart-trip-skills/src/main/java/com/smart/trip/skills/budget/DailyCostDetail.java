package com.smart.trip.skills.budget;

import java.math.BigDecimal;

/**
 * 逐日费用明细（C-009 AC-8 对账用）。
 *
 * @param dayIndex  第几天（1-based）
 * @param ticket    门票小计
 * @param food      餐饮小计
 * @param transport 交通小计
 * @param hotel     住宿小计
 * @param dailyTotal 当日合计（四类之和）
 */
public record DailyCostDetail(
        int dayIndex,
        BigDecimal ticket,
        BigDecimal food,
        BigDecimal transport,
        BigDecimal hotel,
        BigDecimal dailyTotal) {
}
