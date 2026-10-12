package com.smart.trip.skills.itinerary;

/**
 * 行程编排风险项（C-008 AC-5 / 边界情况）。
 *
 * @param code    风险编码
 * @param message 可读描述
 */
public record ItineraryRisk(String code, String message) {

    /** 天数不一致：候选不足以填满 request.days。 */
    public static final String DAYS_MISMATCH = "DAYS_MISMATCH";

    /** 餐厅距离超限：无法在 5km 内找到合适餐厅，已放宽。 */
    public static final String RESTAURANT_DISTANCE = "RESTAURANT_DISTANCE";

    /** 交通耗时超限：两景点间 >2h，已拆分到不同天。 */
    public static final String TRANSIT_OVER_LIMIT = "TRANSIT_OVER_LIMIT";

    /** 低评分景点已剔除。 */
    public static final String LOW_RATING_REMOVED = "LOW_RATING_REMOVED";

    /** 时间窗溢出：某日景点总时长超出 8:00–20:00，已减载。 */
    public static final String TIME_WINDOW_OVERFLOW = "TIME_WINDOW_OVERFLOW";

    /** 餐饮推荐降级：高德/知识库均不可用，使用兜底餐。 */
    public static final String MEAL_FALLBACK = "MEAL_FALLBACK";
}
