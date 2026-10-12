package com.smart.trip.skills.itinerary;

import com.smart.trip.common.enums.TravelStyle;

/**
 * 编排策略参数化配置（C-008 AC-6）：按 {@link TravelStyle} 驱动每日节奏。
 *
 * <p>参数化枚举，避免硬编码分支膨胀（圈复杂度 ≤10）。每种风格决定：
 * 每日最大景点数、时间窗起止、是否偏好性价比等。</p>
 *
 * @param maxAttractionsPerDay 每日最大景点数
 * @param dayStartMinute       每日开始时间（分钟，从 0:00 起算，如 480 = 8:00）
 * @param dayEndMinute         每日结束时间（分钟，如 1200 = 20:00）
 * @param budgetMultiplier     餐饮人均系数（1.0 = 基准价）
 */
public record ScheduleProfile(
        int maxAttractionsPerDay,
        int dayStartMinute,
        int dayEndMinute,
        double budgetMultiplier) {

    /** 每日行程时间窗起点（8:00）。 */
    public static final int DEFAULT_DAY_START = 480;

    /** 每日行程时间窗终点（20:00）。 */
    public static final int DEFAULT_DAY_END = 1200;

    /** 每日可用时长（分钟）。 */
    public int availableMinutes() {
        return dayEndMinute - dayStartMinute;
    }

    /**
     * 按 TravelStyle 映射编排参数。
     *
     * @param style 旅行风格，可 null（缺省走 COUPLE）
     * @return 编排策略
     */
    public static ScheduleProfile forStyle(TravelStyle style) {
        TravelStyle effective = style != null ? style : TravelStyle.COUPLE;
        return switch (effective) {
            case FAMILY -> new ScheduleProfile(3, DEFAULT_DAY_START, DEFAULT_DAY_END, 0.9);
            case COUPLE -> new ScheduleProfile(4, DEFAULT_DAY_START, DEFAULT_DAY_END, 1.2);
            case SOLO -> new ScheduleProfile(5, DEFAULT_DAY_START, DEFAULT_DAY_END, 0.7);
            case TEAM -> new ScheduleProfile(4, DEFAULT_DAY_START, DEFAULT_DAY_END, 1.0);
        };
    }
}
