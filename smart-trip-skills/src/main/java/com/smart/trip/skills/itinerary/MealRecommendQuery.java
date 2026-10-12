package com.smart.trip.skills.itinerary;

import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.GeoLocation;

/**
 * 餐饮推荐请求（C-008 AC-3 / T-1）。
 *
 * @param location     搜索中心点（景点坐标），非 null
 * @param city         城市名（用于菜系匹配），非空白
 * @param mealType     餐别（午/晚），非 null
 * @param style        旅行风格，可 null
 * @param maxDistanceKm 最大搜索半径（公里），&gt; 0
 */
public record MealRecommendQuery(
        GeoLocation location,
        String city,
        MealType mealType,
        TravelStyle style,
        double maxDistanceKm) {

    /** 默认搜索半径（公里）：5km（AC-4 ④）。 */
    public static final double DEFAULT_MAX_DISTANCE_KM = 5.0;

    /**
     * 便捷构造：使用默认搜索半径。
     */
    public static MealRecommendQuery of(GeoLocation location, String city,
                                        MealType mealType, TravelStyle style) {
        return new MealRecommendQuery(location, city, mealType, style, DEFAULT_MAX_DISTANCE_KM);
    }
}
