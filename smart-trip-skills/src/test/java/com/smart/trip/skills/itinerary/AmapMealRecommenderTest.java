package com.smart.trip.skills.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link AmapMealRecommender} 高德 POI 搜索测试（C-008 T-3，Case-9/10）。
 */
class AmapMealRecommenderTest {

    private static final GeoLocation CENTER = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");
    private static final MealRecommendQuery QUERY =
            MealRecommendQuery.of(CENTER, "成都", MealType.LUNCH, TravelStyle.COUPLE);

    @Nested
    @DisplayName("响应解析")
    class Parsing {

        @Test
        @DisplayName("正常 POI → Meal 字段正确（Case-9）")
        void validPois_parsedCorrectly() {
            String json = """
                    {"status":"1","pois":[
                      {"name":"蜀九香火锅","location":"104.0600,30.6700",
                       "address":"宽窄巷子旁","biz_ext":{"rating":"4.2","cost":"80"}}
                    ]}""";

            AmapMealRecommender recommender = new AmapMealRecommender(url -> json, "test-ak");
            List<Meal> meals = recommender.recommend(QUERY);

            assertEquals(1, meals.size());
            Meal meal = meals.get(0);
            assertEquals("蜀九香火锅", meal.name());
            assertEquals(MealType.LUNCH, meal.mealType());
            assertEquals(104.06, meal.location().longitude(), 0.001);
            assertEquals(30.67, meal.location().latitude(), 0.001);
            assertNotNull(meal.avgPrice());
            assertEquals(0, meal.avgPrice().compareTo(new java.math.BigDecimal("80")));
        }

        @Test
        @DisplayName("rating<3.5 过滤 + rating>=3.5 保留")
        void ratingFilter() {
            String json = """
                    {"status":"1","pois":[
                      {"name":"高分店","location":"104.06,30.67","biz_ext":{"rating":"4.0","cost":"60"}},
                      {"name":"低分店","location":"104.07,30.68","biz_ext":{"rating":"3.0","cost":"30"}}
                    ]}""";

            List<Meal> meals = AmapMealRecommender.parsePois(json, MealType.LUNCH, "成都");
            assertEquals(1, meals.size());
            assertEquals("高分店", meals.get(0).name());
        }

        @Test
        @DisplayName("biz_ext.cost 缺失 → avgPrice null")
        void missingCost_nullPrice() {
            String json = """
                    {"status":"1","pois":[
                      {"name":"无价格店","location":"104.06,30.67","biz_ext":{"rating":"4.5"}}
                    ]}""";

            List<Meal> meals = AmapMealRecommender.parsePois(json, MealType.DINNER, "成都");
            assertEquals(1, meals.size());
            assertNull(meals.get(0).avgPrice());
        }

        @Test
        @DisplayName("location 字符串解析为经纬度")
        void locationParsed() {
            String json = """
                    {"status":"1","pois":[
                      {"name":"测试店","location":"104.1234,30.5678","biz_ext":{}}
                    ]}""";

            List<Meal> meals = AmapMealRecommender.parsePois(json, MealType.LUNCH, "成都");
            assertEquals(1, meals.size());
            assertEquals(104.1234, meals.get(0).location().longitude(), 0.0001);
            assertEquals(30.5678, meals.get(0).location().latitude(), 0.0001);
        }
    }

    @Nested
    @DisplayName("错误处理")
    class ErrorHandling {

        @Test
        @DisplayName("status≠1 → 空列表")
        void statusNotOne_empty() {
            String json = """
                    {"status":"0","info":"INVALID_USER_KEY","pois":[]}""";

            AmapMealRecommender recommender = new AmapMealRecommender(url -> json, "bad-ak");
            List<Meal> meals = recommender.recommend(QUERY);
            assertTrue(meals.isEmpty());
        }

        @Test
        @DisplayName("pois 为空数组 → 空列表")
        void emptyPois_empty() {
            String json = """
                    {"status":"1","pois":[]}""";

            AmapMealRecommender recommender = new AmapMealRecommender(url -> json, "test-ak");
            List<Meal> meals = recommender.recommend(QUERY);
            assertTrue(meals.isEmpty());
        }

        @Test
        @DisplayName("HTTP 异常 → 空列表，不抛裸异常")
        void httpFailure_empty() {
            AmapMealRecommender recommender = new AmapMealRecommender(
                    url -> { throw new RuntimeException("连接超时"); }, "test-ak");
            List<Meal> meals = recommender.recommend(QUERY);
            assertTrue(meals.isEmpty());
        }

        @Test
        @DisplayName("空白 AK → 构造抛 IllegalArgumentException")
        void blankAk_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AmapMealRecommender(url -> "", "  "));
        }

        @Test
        @DisplayName("null PoiHttpExecutor → 构造抛 IllegalArgumentException")
        void nullHttp_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AmapMealRecommender(null, "test-ak"));
        }

        @Test
        @DisplayName("非法 JSON 响应 → 空列表，不抛裸异常")
        void malformedJson_empty() {
            AmapMealRecommender recommender = new AmapMealRecommender(
                    url -> "not-valid-json{{{", "test-ak");
            List<Meal> meals = recommender.recommend(QUERY);
            assertTrue(meals.isEmpty(), "非法 JSON 应返回空列表而非抛异常");
        }

        @Test
        @DisplayName("pois 非数组（字符串）→ 空列表")
        void poisNotArray_empty() {
            String json = """
                    {"status":"1","pois":"not-an-array"}""";
            AmapMealRecommender recommender = new AmapMealRecommender(url -> json, "test-ak");
            List<Meal> meals = recommender.recommend(QUERY);
            assertTrue(meals.isEmpty());
        }
    }

    @Nested
    @DisplayName("TravelStyle → keywords 映射")
    class StyleKeywords {

        @Test
        void familyKeywords() {
            assertEquals("家常菜|粤菜|江浙菜", AmapMealRecommender.keywordsForStyle(TravelStyle.FAMILY));
        }

        @Test
        void coupleKeywords() {
            assertEquals("私房菜|西餐|日料", AmapMealRecommender.keywordsForStyle(TravelStyle.COUPLE));
        }

        @Test
        void soloKeywords() {
            assertEquals("小吃|面馆|快餐", AmapMealRecommender.keywordsForStyle(TravelStyle.SOLO));
        }

        @Test
        void teamKeywords() {
            assertEquals("火锅|烧烤|大排档", AmapMealRecommender.keywordsForStyle(TravelStyle.TEAM));
        }

        @Test
        void nullStyle_emptyKeywords() {
            assertEquals("", AmapMealRecommender.keywordsForStyle(null));
        }
    }
}
