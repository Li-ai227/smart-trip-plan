package com.smart.trip.skills.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CuisineKnowledgeBase} 菜系知识库降级推荐测试（C-008 T-3）。
 */
class CuisineKnowledgeBaseTest {

    private static final GeoLocation CENTER = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");

    private static final List<CuisineKnowledgeBase.CuisineEntry> CHENGDU_DATA = List.of(
            new CuisineKnowledgeBase.CuisineEntry(
                    "陈麻婆豆腐", List.of("麻婆豆腐", "回锅肉"), 50, 80, List.of("川菜", "家常菜")),
            new CuisineKnowledgeBase.CuisineEntry(
                    "蜀九香火锅", List.of("鸳鸯锅", "毛肚"), 80, 120, List.of("火锅", "大排档")),
            new CuisineKnowledgeBase.CuisineEntry(
                    "龙抄手", List.of("抄手", "担担面"), 20, 40, List.of("小吃", "面馆")),
            new CuisineKnowledgeBase.CuisineEntry(
                    "廊桥法餐", List.of("鹅肝", "牛排"), 200, 400, List.of("西餐", "私房菜"))
    );

    private CuisineKnowledgeBase kb() {
        return new CuisineKnowledgeBase(Map.of("成都", CHENGDU_DATA));
    }

    @Test
    @DisplayName("已知城市 → 非空结果，字段完整")
    void knownCity_nonEmptyResult() {
        MealRecommendQuery query = MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH, TravelStyle.FAMILY);
        List<Meal> meals = kb().recommend(query);

        assertFalse(meals.isEmpty());
        Meal meal = meals.get(0);
        assertNotNull(meal.name());
        assertNotNull(meal.location());
        assertEquals(MealType.LUNCH, meal.mealType());
        assertNotNull(meal.avgPrice());
        assertFalse(meal.recommendDishes().isEmpty());
    }

    @Test
    @DisplayName("确定性：同输入 → 同输出")
    void deterministic_sameInputSameOutput() {
        MealRecommendQuery query = MealRecommendQuery.of(CENTER, "成都", MealType.DINNER, TravelStyle.SOLO);
        CuisineKnowledgeBase kb = kb();

        Meal first = kb.recommend(query).get(0);
        Meal second = kb.recommend(query).get(0);

        assertEquals(first.name(), second.name());
        assertEquals(first.avgPrice(), second.avgPrice());
    }

    @Test
    @DisplayName("未知城市 → 空列表")
    void unknownCity_empty() {
        MealRecommendQuery query = MealRecommendQuery.of(CENTER, "拉萨", MealType.LUNCH, TravelStyle.FAMILY);
        List<Meal> meals = kb().recommend(query);

        assertTrue(meals.isEmpty());
    }

    @Test
    @DisplayName("null style → 不过滤标签，仍正常返回")
    void nullStyle_returnsResult() {
        MealRecommendQuery query = MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH, null);
        List<Meal> meals = kb().recommend(query);

        assertFalse(meals.isEmpty());
    }

    @Test
    @DisplayName("不同 mealType → seed 不同可选不同菜")
    void differentMealType_differentSeedCanSelectDifferent() {
        MealRecommendQuery lunch = MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH, null);
        MealRecommendQuery dinner = MealRecommendQuery.of(CENTER, "成都", MealType.DINNER, null);
        CuisineKnowledgeBase kb = kb();

        Meal lunchMeal = kb.recommend(lunch).get(0);
        Meal dinnerMeal = kb.recommend(dinner).get(0);

        assertNotNull(lunchMeal);
        assertNotNull(dinnerMeal);
    }

    @Test
    @DisplayName("classpath 加载：默认构造器能加载成都数据")
    void defaultConstructor_loadsClasspathData() {
        CuisineKnowledgeBase kb = new CuisineKnowledgeBase();
        MealRecommendQuery query = MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH, TravelStyle.SOLO);
        List<Meal> meals = kb.recommend(query);

        assertFalse(meals.isEmpty());
        assertEquals("成都", meals.get(0).location().city());
    }
}
