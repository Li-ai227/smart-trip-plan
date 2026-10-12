package com.smart.trip.common.model;

import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.util.CollectionUtils;
import java.math.BigDecimal;
import java.util.List;

/**
 * 餐饮安排（数据模型.md §2.4 / 业务模型.md §2）。
 *
 * <p>业务不变量（业务模型.md §4）：每个 TripDay 至少含午餐与晚餐、餐厅距当日景点 ≤5km；
 * 校验归 Itinerary / OutputQualityGate（C-008），本模型仅承载字段契约。</p>
 *
 * @param name            餐厅名
 * @param location        地理位置
 * @param mealType        餐别（早/午/晚）
 * @param avgPrice        人均价格（元）
 * @param recommendDishes 推荐菜，可空（构造时归一为不可变空列表）
 */
public record Meal(
        String name,
        GeoLocation location,
        MealType mealType,
        BigDecimal avgPrice,
        List<String> recommendDishes) {

    /**
     * 紧凑构造器：对可空集合做 null 安全的不可变拷贝（决策 D-5/D-6）。
     */
    public Meal {
        recommendDishes = CollectionUtils.safeCopy(recommendDishes);
    }
}
