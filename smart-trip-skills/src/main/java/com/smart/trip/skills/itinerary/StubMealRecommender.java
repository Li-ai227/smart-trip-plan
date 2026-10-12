package com.smart.trip.skills.itinerary;

import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import java.math.BigDecimal;
import java.util.List;

/**
 * 桩餐饮推荐（C-008 T-1）：确定性测试替身，始终返回固定餐厅。
 *
 * <p>同输入 → 同输出，用于验证 {@link MealRecommender} 契约和
 * {@link ItineraryDesignService} 餐饮插入逻辑。</p>
 */
public final class StubMealRecommender implements MealRecommender {

    private static final BigDecimal STUB_PRICE = new BigDecimal("60");

    @Override
    public List<Meal> recommend(MealRecommendQuery query) {
        GeoLocation loc = query.location();
        String mealLabel = query.mealType() == MealType.LUNCH ? "午餐" : "晚餐";
        String name = query.city() + "·" + mealLabel + "·特色餐厅";

        GeoLocation mealLoc = new GeoLocation(
                loc.longitude() + 0.005,
                loc.latitude() + 0.005,
                name,
                query.city());

        Meal meal = new Meal(name, mealLoc, query.mealType(), STUB_PRICE, List.of("推荐菜"));
        return List.of(meal);
    }
}
