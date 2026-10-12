package com.smart.trip.skills.itinerary;

import com.smart.trip.common.model.Meal;
import java.util.List;

/**
 * 三层降级餐饮推荐（C-008 AC-3）：primary → secondary → 空列表。
 *
 * <p>典型组合：{@link AmapMealRecommender}（source=AMAP）→
 * {@link CuisineKnowledgeBase}（source=CUISINE_KB）→ 空（触发
 * {@link ItineraryDesignService} 的 fallbackMeal）。</p>
 */
public final class DegradingMealRecommender implements MealRecommender {

    private final MealRecommender primary;
    private final MealRecommender secondary;

    /**
     * @param primary   主推荐源（如高德 POI），非 null
     * @param secondary 降级推荐源（如菜系知识库），非 null
     */
    public DegradingMealRecommender(MealRecommender primary, MealRecommender secondary) {
        if (primary == null || secondary == null) {
            throw new IllegalArgumentException("primary 和 secondary 不可为 null");
        }
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public List<Meal> recommend(MealRecommendQuery query) {
        List<Meal> result = safeRecommend(primary, query);
        if (!result.isEmpty()) {
            return result;
        }
        return safeRecommend(secondary, query);
    }

    private static List<Meal> safeRecommend(MealRecommender recommender, MealRecommendQuery query) {
        try {
            List<Meal> meals = recommender.recommend(query);
            return meals != null ? meals : List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }
}
