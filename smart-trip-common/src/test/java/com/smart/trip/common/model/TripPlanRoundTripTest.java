package com.smart.trip.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.PlanStatus;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.util.JsonUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 领域模型 JSON 往返与精度保真测试（C-004 AC-1/AC-6 / 验收用例 Case-3）。
 *
 * <p>校验 {@code BigDecimal} 金额序列化往返不丢精度与标度（如 {@code 1620.00}），
 * 以及完整 {@link TripPlan} 对象图 round-trip 后字段完全一致（回归保护：模型契约一旦稳定即被本测试守护）。</p>
 */
class TripPlanRoundTripTest {

    @Nested
    @DisplayName("BigDecimal 精度与标度保真（Case-3）")
    class MoneyPrecision {

        @Test
        void budgetBreakdownPrecisionPreserved() {
            BudgetBreakdown breakdown = new BudgetBreakdown(
                    new BigDecimal("2200"), new BigDecimal("3000"),
                    new BigDecimal("1620.00"), new BigDecimal("1000"));
            Budget budget = new Budget(new BigDecimal("8000"), new BigDecimal("7820"),
                    new BigDecimal("-0.02"), breakdown);

            Budget back = JsonUtils.fromJson(JsonUtils.toJson(budget), Budget.class);

            assertEquals(budget, back);
            assertEquals(new BigDecimal("1620.00"), back.breakdown().food());
            assertEquals(2, back.breakdown().food().scale(), "标度（小数位）不得丢失");
        }
    }

    @Nested
    @DisplayName("完整 TripPlan 对象图 round-trip")
    class FullGraph {

        @Test
        void fullTripPlanRoundTrip() {
            TripPlan plan = samplePlan();
            TripPlan back = JsonUtils.fromJson(JsonUtils.toJson(plan), TripPlan.class);
            assertEquals(plan, back);
        }

        @Test
        void emptyDayCollectionsRoundTripAndAreImmutable() {
            TripDay day = new TripDay(LocalDate.of(2026, 7, 1), 1, null, null, null, BigDecimal.ZERO);
            assertTrue(day.attractions().isEmpty());
            assertTrue(day.meals().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> day.routes().add(null));

            TripDay back = JsonUtils.fromJson(JsonUtils.toJson(day), TripDay.class);
            assertEquals(day, back);
        }
    }

    @Nested
    @DisplayName("降级链路关键字段保真（接口协议.md §2.3）")
    class DegradationMetadata {

        /**
         * {@code Route.fallback=true} 标记路线由离线估算降级产出，属降级链路关键字段，
         * 序列化往返不得被静默移除/翻转，否则下游无法识别降级结果（回归保护）。
         */
        @Test
        void fallbackRouteFlagSurvivesRoundTrip() {
            GeoLocation from = new GeoLocation(120.13, 30.25, "西湖", "杭州");
            GeoLocation to = new GeoLocation(120.15, 30.26, "灵隐寺", "杭州");
            Route degraded = new Route(from, to, 5.0, 25, "ESTIMATED", true);

            Route back = JsonUtils.fromJson(JsonUtils.toJson(degraded), Route.class);

            assertEquals(degraded, back);
            assertTrue(back.fallback(), "降级标记 fallback 不得在序列化往返中丢失");
        }
    }

    private static TripPlan samplePlan() {
        GeoLocation west = new GeoLocation(120.13, 30.25, "西湖风景区", "杭州");
        GeoLocation rest = new GeoLocation(120.15, 30.26, "楼外楼", "杭州");
        Attraction attraction = new Attraction("A-001", "西湖", west,
                4.8, 180, new BigDecimal("0"), List.of("自然", "免费"));
        Meal meal = new Meal("楼外楼", rest, MealType.LUNCH,
                new BigDecimal("120.50"), List.of("西湖醋鱼", "龙井虾仁"));
        Route route = new Route(west, rest, 1.2, 15, "WALK", false);
        TripDay day = new TripDay(LocalDate.of(2026, 7, 1), 1,
                List.of(attraction), List.of(meal), List.of(route), new BigDecimal("241.00"));
        BudgetBreakdown breakdown = new BudgetBreakdown(
                new BigDecimal("2200"), new BigDecimal("3000"),
                new BigDecimal("1620.00"), new BigDecimal("1000"));
        Budget budget = new Budget(new BigDecimal("8000"), new BigDecimal("7820"),
                new BigDecimal("-0.02"), breakdown);
        TripPlanRequest request = new TripPlanRequest("杭州", LocalDate.of(2026, 7, 1), 1,
                new BigDecimal("8000"), 2, TravelStyle.COUPLE, List.of("古镇", "美食"));
        return new TripPlan("P-20260701-001", request, List.of(day),
                new BigDecimal("7820"), budget, PlanStatus.REVIEW, false);
    }
}
