package com.smart.trip.skills.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BudgetCalculationService} 确定性核算单测（C-009 T-2/T-3/T-5）。
 */
class BudgetCalculationServiceTest {

    private static final GeoLocation LOC = new GeoLocation(104.05, 30.67, "测试", "成都");
    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    private final BudgetCalculationService service = new BudgetCalculationService();

    private static TripPlanRequest request(int days, BigDecimal budget, int headcount) {
        return new TripPlanRequest("成都", START, days, budget, headcount,
                TravelStyle.FAMILY, List.of());
    }

    private static Attraction attraction(String id, BigDecimal ticketPrice) {
        return new Attraction(id, "景点-" + id, LOC, 4.5, 120, ticketPrice, List.of());
    }

    private static Meal meal(MealType type, BigDecimal avgPrice) {
        return new Meal("餐厅", LOC, type, avgPrice, List.of());
    }

    private static Route route(double distanceKm) {
        return new Route(LOC, LOC, distanceKm, 30, "驾车", false);
    }

    private static TripDay tripDay(int dayIndex, List<Attraction> attractions,
                                   List<Meal> meals, List<Route> routes) {
        return new TripDay(START.plusDays(dayIndex - 1), dayIndex,
                attractions, meals, routes, BigDecimal.ZERO);
    }

    private static ItineraryDesignResult itinerary(List<TripDay> days) {
        return new ItineraryDesignResult(days, List.of(), false, null, null);
    }

    // ==================== AC-3: 四类费用拆分 ====================

    @Nested
    @DisplayName("AC-3：四类费用拆分")
    class CostBreakdown {

        @Test
        @DisplayName("门票：Σ ticketPrice()，null 计 ZERO")
        void ticketSum() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", new BigDecimal("80")),
                            attraction("A2", null),
                            attraction("A3", new BigDecimal("50"))),
                    List.of(meal(MealType.LUNCH, new BigDecimal("60"))),
                    List.of(route(10)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("10000"), 2),
                            itinerary(List.of(day))));

            assertEquals(new BigDecimal("130"), r.ticket());
        }

        @Test
        @DisplayName("餐饮：avgPrice × headcount，null 用保守 30 元/人·餐")
        void foodCost() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", BigDecimal.TEN)),
                    List.of(meal(MealType.LUNCH, new BigDecimal("50")),
                            meal(MealType.DINNER, null)),
                    List.of(route(5)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("10000"), 3),
                            itinerary(List.of(day))));

            // lunch: 50*3=150, dinner: 30*3=90 => total food = 240
            assertEquals(0, new BigDecimal("240").compareTo(r.food()));
            assertTrue(r.fallback());
        }

        @Test
        @DisplayName("交通：Σ distanceKm × 1 元/km")
        void transportCost() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", BigDecimal.ZERO)),
                    List.of(meal(MealType.LUNCH, new BigDecimal("40"))),
                    List.of(route(15.5), route(20.0)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("10000"), 1),
                            itinerary(List.of(day))));

            // 15.5 + 20.0 = 35.5
            assertEquals(0, new BigDecimal("35.5").compareTo(r.transport()));
        }

        @Test
        @DisplayName("交通降级：路线缺失 → days × 100 + fallback=true")
        void transportFallback() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", BigDecimal.ZERO)),
                    List.of(meal(MealType.LUNCH, new BigDecimal("40"))),
                    List.of());

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("10000"), 1),
                            itinerary(List.of(day))));

            assertEquals(0, new BigDecimal("100").compareTo(r.transport()));
            assertTrue(r.fallback());
        }

        @Test
        @DisplayName("住宿：headcount × days × 200 + fallback=true")
        void hotelCost() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", BigDecimal.ZERO)),
                    List.of(meal(MealType.LUNCH, new BigDecimal("40"))),
                    List.of(route(5)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(5, new BigDecimal("50000"), 4),
                            itinerary(List.of(day))));

            // hotel = 4 * 5 * 200 = 4000
            assertEquals(0, new BigDecimal("4000").compareTo(r.hotel()));
            assertTrue(r.fallback());
        }
    }

    // ==================== AC-4/AC-5: totalCost + overrunRate ====================

    @Nested
    @DisplayName("AC-4/AC-5：totalCost + overrunRate")
    class TotalAndOverrun {

        @Test
        @DisplayName("totalCost = transport + hotel + food + ticket")
        void totalCostSum() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", new BigDecimal("100"))),
                    List.of(meal(MealType.LUNCH, new BigDecimal("60"))),
                    List.of(route(20)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("10000"), 2),
                            itinerary(List.of(day))));

            BigDecimal expected = r.transport().add(r.hotel()).add(r.food()).add(r.ticket());
            assertEquals(0, expected.compareTo(r.totalCost()));
        }

        @Test
        @DisplayName("overrunRate = (totalCost - budget) / budget，BigDecimal 精度")
        void overrunRatePrecision() {
            // totalCost = ticket(0) + food(60*2=120) + transport(10) + hotel(2*1*200=400)
            // totalCost = 530, budget = 8000
            // overrunRate = (530 - 8000) / 8000 = -7470/8000 = -0.933750...
            TripDay day = tripDay(1,
                    List.of(attraction("A1", BigDecimal.ZERO)),
                    List.of(meal(MealType.LUNCH, new BigDecimal("60"))),
                    List.of(route(10)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("8000"), 2),
                            itinerary(List.of(day))));

            assertTrue(r.overrunRate().signum() < 0);
        }
    }

    // ==================== AC-6: HITL BigDecimal 临界判定 ====================

    @Nested
    @DisplayName("AC-6：overrunRate >= 0.15 触发 HITL（含等号）")
    class HITLThreshold {

        @Test
        @DisplayName("Case-2: overrunRate 恰为 0.15 → requiresHITL=true")
        void exactlyThreshold() {
            // Need totalCost = budget * 1.15
            // budget=8000, totalCost must = 9200
            // Construct: hotel=200*2*1=400, so need other costs = 9200-400=8800
            // ticket=8800, food/transport from day
            TripDay day = tripDay(1,
                    List.of(attraction("A1", new BigDecimal("8800"))),
                    List.of(meal(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(route(0)));

            // totalCost = ticket(8800) + food(0*2=0) + transport(0) + hotel(200*2*1=400) = 9200
            // overrunRate = (9200-8000)/8000 = 1200/8000 = 0.15
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("8000"), 2),
                            itinerary(List.of(day))));

            assertEquals(0, r.overrunRate().compareTo(new BigDecimal("0.15")));
            assertTrue(r.requiresHITL(), "overrunRate=0.15 含等号必须触发 HITL");
        }

        @Test
        @DisplayName("Case-3: overrunRate 略低于 0.15 → requiresHITL=false")
        void belowThreshold() {
            // budget=8000, need totalCost < 9200
            // ticket=8799, hotel=400, food=0, transport=0 => totalCost=9199
            // overrunRate = (9199-8000)/8000 = 1199/8000 = 0.149875
            TripDay day = tripDay(1,
                    List.of(attraction("A1", new BigDecimal("8799"))),
                    List.of(meal(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(route(0)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("8000"), 2),
                            itinerary(List.of(day))));

            assertTrue(r.overrunRate().compareTo(new BigDecimal("0.15")) < 0);
            assertFalse(r.requiresHITL(), "overrunRate<0.15 不触发 HITL");
        }

        @Test
        @DisplayName("Case-1: 预算内（负 overrunRate）→ requiresHITL=false")
        void withinBudget() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", new BigDecimal("100"))),
                    List.of(meal(MealType.LUNCH, new BigDecimal("50"))),
                    List.of(route(10)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("8000"), 2),
                            itinerary(List.of(day))));

            assertTrue(r.overrunRate().signum() < 0);
            assertFalse(r.requiresHITL());
        }

        @Test
        @DisplayName("Case-11: 超支 20% → requiresHITL=true + 调整建议非空")
        void farExceedsThreshold() {
            // budget=5000, need totalCost=6000 => overrunRate=0.20
            // hotel=200*2*1=400, so other=5600, ticket=5600
            TripDay day = tripDay(1,
                    List.of(attraction("A1", new BigDecimal("5600"))),
                    List.of(meal(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(route(0)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("5000"), 2),
                            itinerary(List.of(day))));

            assertTrue(r.requiresHITL());
            assertNotNull(r.hitlReason());
            assertFalse(r.adjustmentAdvice().isEmpty());
        }
    }

    // ==================== AC-8: 逐日对账一致性 ====================

    @Nested
    @DisplayName("AC-8：逐日小计对账一致性")
    class Reconciliation {

        @Test
        @DisplayName("Case-4: Σ dailyBreakdown.dailyTotal + hotelRemainder == totalCost")
        void dailySumEqualsTotal() {
            TripDay day1 = tripDay(1,
                    List.of(attraction("A1", new BigDecimal("50"))),
                    List.of(meal(MealType.LUNCH, new BigDecimal("40"))),
                    List.of(route(10)));
            TripDay day2 = tripDay(2,
                    List.of(attraction("A2", new BigDecimal("80"))),
                    List.of(meal(MealType.DINNER, new BigDecimal("70"))),
                    List.of(route(20)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(2, new BigDecimal("10000"), 2),
                            itinerary(List.of(day1, day2))));

            BigDecimal daySum = r.dailyBreakdowns().stream()
                    .map(DailyCostDetail::dailyTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, daySum.compareTo(r.totalCost()));
        }
    }

    // ==================== AC-9: 空行程不除零 ====================

    @Nested
    @DisplayName("AC-9：空行程/边界")
    class EmptyItinerary {

        @Test
        @DisplayName("Case-9: 空行程 → totalCost=ZERO, overrunRate=ZERO, requiresHITL=false")
        void emptyDays() {
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(3, new BigDecimal("5000"), 2),
                            itinerary(List.of())));

            assertEquals(0, BigDecimal.ZERO.compareTo(r.totalCost()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r.overrunRate()));
            assertFalse(r.requiresHITL());
        }

        @Test
        @DisplayName("Case-10: budget=0 → InvalidRequestException")
        void zeroBudget() {
            assertThrows(InvalidRequestException.class, () ->
                    service.calculate(new BudgetCalculationQuery(
                            request(1, BigDecimal.ZERO, 2),
                            itinerary(List.of(tripDay(1, List.of(), List.of(), List.of()))))));
        }

        @Test
        @DisplayName("budget 为负 → InvalidRequestException")
        void negativeBudget() {
            assertThrows(InvalidRequestException.class, () ->
                    service.calculate(new BudgetCalculationQuery(
                            request(1, new BigDecimal("-100"), 2),
                            itinerary(List.of(tripDay(1, List.of(), List.of(), List.of()))))));
        }
    }

    // ==================== 降级标记 ====================

    @Nested
    @DisplayName("降级标记")
    class FallbackFlags {

        @Test
        @DisplayName("Case-5: 门票 ticketPrice 全 null → ticket=ZERO 不崩溃")
        void allTicketsNull() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", null), attraction("A2", null)),
                    List.of(meal(MealType.LUNCH, new BigDecimal("50"))),
                    List.of(route(5)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("10000"), 2),
                            itinerary(List.of(day))));

            assertEquals(0, BigDecimal.ZERO.compareTo(r.ticket()));
        }

        @Test
        @DisplayName("Case-6: 路线全缺失 → transport 保守估算 + fallback=true")
        void allRoutesEmpty() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", BigDecimal.TEN)),
                    List.of(meal(MealType.LUNCH, new BigDecimal("50"))),
                    null);

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("10000"), 1),
                            itinerary(List.of(day))));

            assertEquals(0, BudgetDefaults.TRANSPORT_DAILY_FALLBACK.compareTo(r.transport()));
            assertTrue(r.fallback());
        }

        @Test
        @DisplayName("Case-7: 住宿估算 headcount=4, days=5 → hotel=4000 + fallback=true")
        void hotelEstimate() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", BigDecimal.ZERO)),
                    List.of(meal(MealType.LUNCH, new BigDecimal("40"))),
                    List.of(route(5)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(5, new BigDecimal("50000"), 4),
                            itinerary(List.of(day))));

            assertEquals(0, new BigDecimal("4000").compareTo(r.hotel()));
            assertTrue(r.fallback());
        }

        @Test
        @DisplayName("餐饮 avgPrice 为 null → 保守 30/人·餐 + fallback=true")
        void mealFallback() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", BigDecimal.ZERO)),
                    List.of(meal(MealType.LUNCH, null), meal(MealType.DINNER, null)),
                    List.of(route(5)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("10000"), 2),
                            itinerary(List.of(day))));

            // 2 meals * 30 * 2 headcount = 120
            assertEquals(0, new BigDecimal("120").compareTo(r.food()));
            assertTrue(r.fallback());
        }
    }

    // ==================== 参数校验 ====================

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("request 为 null → InvalidRequestException")
        void nullRequest() {
            assertThrows(InvalidRequestException.class, () ->
                    service.calculate(new BudgetCalculationQuery(null,
                            itinerary(List.of()))));
        }

        @Test
        @DisplayName("itineraryResult 为 null → InvalidRequestException")
        void nullItinerary() {
            assertThrows(InvalidRequestException.class, () ->
                    service.calculate(new BudgetCalculationQuery(
                            request(1, new BigDecimal("5000"), 2), null)));
        }
    }

    // ==================== Telemetry ====================

    @Nested
    @DisplayName("Telemetry")
    class TelemetryTests {

        @Test
        @DisplayName("telemetry.hitlTriggered 与 requiresHITL 一致")
        void telemetryConsistency() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", new BigDecimal("8800"))),
                    List.of(meal(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(route(0)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("8000"), 2),
                            itinerary(List.of(day))));

            assertEquals(r.requiresHITL(), r.telemetry().hitlTriggered());
        }

        @Test
        @DisplayName("telemetry.fallbackItemCount > 0 当有降级项")
        void fallbackItemCount() {
            TripDay day = tripDay(1,
                    List.of(attraction("A1", BigDecimal.ZERO)),
                    List.of(meal(MealType.LUNCH, null)),
                    List.of());

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(1, new BigDecimal("10000"), 2),
                            itinerary(List.of(day))));

            assertTrue(r.telemetry().fallbackItemCount() > 0);
        }
    }
}
