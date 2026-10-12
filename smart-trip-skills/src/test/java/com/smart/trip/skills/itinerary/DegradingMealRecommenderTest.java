package com.smart.trip.skills.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DegradingMealRecommender} 降级链测试（C-008 T-4）。
 */
class DegradingMealRecommenderTest {

    private static final GeoLocation LOC = new GeoLocation(104.0, 30.6, "测试地址", "成都");
    private static final MealRecommendQuery QUERY =
            MealRecommendQuery.of(LOC, "成都", MealType.LUNCH, TravelStyle.FAMILY);
    private static final Meal PRIMARY_MEAL = new Meal("主源餐厅", LOC, MealType.LUNCH,
            new BigDecimal("80"), List.of("菜A"));
    private static final Meal SECONDARY_MEAL = new Meal("备源餐厅", LOC, MealType.LUNCH,
            new BigDecimal("50"), List.of("菜B"));

    @Test
    @DisplayName("primary 返回非空 → 使用 primary 结果")
    void primarySuccess_usesPrimary() {
        MealRecommender primary = q -> List.of(PRIMARY_MEAL);
        MealRecommender secondary = q -> List.of(SECONDARY_MEAL);
        DegradingMealRecommender degrading = new DegradingMealRecommender(primary, secondary);

        List<Meal> result = degrading.recommend(QUERY);

        assertEquals(1, result.size());
        assertEquals("主源餐厅", result.get(0).name());
    }

    @Test
    @DisplayName("primary 返回空 → 降级到 secondary")
    void primaryEmpty_fallsToSecondary() {
        MealRecommender primary = q -> List.of();
        MealRecommender secondary = q -> List.of(SECONDARY_MEAL);
        DegradingMealRecommender degrading = new DegradingMealRecommender(primary, secondary);

        List<Meal> result = degrading.recommend(QUERY);

        assertEquals(1, result.size());
        assertEquals("备源餐厅", result.get(0).name());
    }

    @Test
    @DisplayName("primary 抛异常 → 降级到 secondary")
    void primaryThrows_fallsToSecondary() {
        MealRecommender primary = q -> { throw new RuntimeException("网络超时"); };
        MealRecommender secondary = q -> List.of(SECONDARY_MEAL);
        DegradingMealRecommender degrading = new DegradingMealRecommender(primary, secondary);

        List<Meal> result = degrading.recommend(QUERY);

        assertFalse(result.isEmpty());
        assertEquals("备源餐厅", result.get(0).name());
    }

    @Test
    @DisplayName("both 返回空 → 空列表")
    void bothEmpty_emptyList() {
        MealRecommender primary = q -> List.of();
        MealRecommender secondary = q -> List.of();
        DegradingMealRecommender degrading = new DegradingMealRecommender(primary, secondary);

        List<Meal> result = degrading.recommend(QUERY);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("both 抛异常 → 空列表")
    void bothThrow_emptyList() {
        MealRecommender primary = q -> { throw new RuntimeException("错误1"); };
        MealRecommender secondary = q -> { throw new RuntimeException("错误2"); };
        DegradingMealRecommender degrading = new DegradingMealRecommender(primary, secondary);

        List<Meal> result = degrading.recommend(QUERY);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("null 参数 → 构造抛 IllegalArgumentException")
    void nullParams_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DegradingMealRecommender(null, q -> List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DegradingMealRecommender(q -> List.of(), null));
    }
}
