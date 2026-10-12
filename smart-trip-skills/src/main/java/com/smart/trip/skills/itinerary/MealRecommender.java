package com.smart.trip.skills.itinerary;

import com.smart.trip.common.model.Meal;
import java.util.List;

/**
 * 餐饮推荐端口（C-008 AC-3 / T-1）：按景点位置 + 风格推荐就近餐厅。
 *
 * <p>主力实现 {@code AmapMealRecommender}（高德 POI 周边搜索，复用 C-007 已有 {@code AMAP_MAP_AK}）；
 * 降级实现 {@code CuisineKnowledgeBase}（菜系知识库 YAML）；桩实现 {@link StubMealRecommender}
 * 用于单测。</p>
 *
 * <p>返回的 {@link Meal} 列表按推荐度降序排列（距离近 + 评分高优先）。空列表表示无合适餐厅，
 * 调用方应走兜底策略（AC-4 ④）。</p>
 */
@FunctionalInterface
public interface MealRecommender {

    /**
     * 推荐就近餐厅。
     *
     * @param query 推荐请求，非 null
     * @return 候选餐厅列表（按推荐度降序），空列表表示无合适餐厅
     */
    List<Meal> recommend(MealRecommendQuery query);
}
