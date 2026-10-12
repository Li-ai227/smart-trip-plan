package com.smart.trip.common.enums;

/**
 * 餐别（数据模型.md §3）。业务规则要求每个 TripDay 至少含午餐与晚餐（业务模型.md §4）。
 */
public enum MealType {

    /** 早餐。 */
    BREAKFAST,

    /** 午餐。 */
    LUNCH,

    /** 晚餐。 */
    DINNER
}
