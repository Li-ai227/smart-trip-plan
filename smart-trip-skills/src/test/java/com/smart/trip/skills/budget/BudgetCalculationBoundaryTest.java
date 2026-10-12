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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BudgetCalculationService} 边界与退化测试（C-009 T-5 延伸）。
 */
class BudgetCalculationBoundaryTest {

    private static final GeoLocation LOC = new GeoLocation(104.05, 30.67, "测试", "成都");
    private static final LocalDate START = LocalDate.of(2026, 7, 1);
    private final BudgetCalculationService service = new BudgetCalculationService();

    private static TripPlanRequest request(BigDecimal budget, int headcount, int days) {
        return new TripPlanRequest("成都", START, days, budget, headcount,
                TravelStyle.FAMILY, List.of());
    }

    private static Attraction a(String id, BigDecimal ticketPrice) {
        return new Attraction(id, "景点" + id, LOC, 4.5, 90, ticketPrice, List.of());
    }

    private static Meal m(MealType type, BigDecimal avgPrice) {
        return new Meal("餐厅", LOC, type, avgPrice, List.of());
    }

    private static Route r(double km) {
        return new Route(LOC, LOC, km, 25, "驾车", false);
    }

    private static TripDay day(int index, List<Attraction> attractions,
                               List<Meal> meals, List<Route> routes) {
        return new TripDay(START.plusDays(index - 1), index, attractions, meals, routes,
                BigDecimal.ZERO);
    }

    private static ItineraryDesignResult itin(List<TripDay> days) {
        return new ItineraryDesignResult(days, List.of(), false, null, null);
    }

    // ============ headcount 边界 ============

    @Nested
    @DisplayName("headcount 防御性处理")
    class HeadcountBoundary {

        @Test
        @DisplayName("headcount=0 → 防御性按 1 处理，不崩溃")
        void headcountZero() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("100"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("50"))),
                    List.of(r(10)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("5000"), 0, 1),
                            itin(List.of(d))));

            assertNotNull(r.totalCost());
            // hotel = 1 * 1 * 200 = 200 (not 0)
            assertEquals(0, new BigDecimal("200").compareTo(r.hotel()));
            // food = 50 * 1 = 50
            assertEquals(0, new BigDecimal("50").compareTo(r.food()));
        }

        @Test
        @DisplayName("headcount=1（最小合法值）→ 正常核算")
        void headcountOne() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("100"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("50"))),
                    List.of(r(10)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("5000"), 1, 1),
                            itin(List.of(d))));

            assertEquals(0, new BigDecimal("200").compareTo(r.hotel()));
            assertEquals(0, new BigDecimal("50").compareTo(r.food()));
        }

        @Test
        @DisplayName("headcount 极大值 → 不溢出")
        void headcountLarge() {
            TripDay d = day(1,
                    List.of(a("A1", BigDecimal.ZERO)),
                    List.of(m(MealType.LUNCH, new BigDecimal("30"))),
                    List.of(r(5)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("99999999"), 100, 1),
                            itin(List.of(d))));

            assertEquals(0, new BigDecimal("20000").compareTo(r.hotel()));
        }
    }

    // ============ BigDecimal 临界精度 ============

    @Nested
    @DisplayName("BigDecimal overrunRate 精度边界")
    class OverrunRatePrecision {

        @Test
        @DisplayName("overrunRate 保留 >=10 位标度，compareTo 比较")
        void highPrecisionScale() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("1000"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(r(0)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("3000"), 2, 1),
                            itin(List.of(d))));

            assertTrue(r.overrunRate().scale() >= 5, "应保留足够标度");
        }

        @Test
        @DisplayName("overrunRate 恰为 0.1500000000 → 触发 HITL")
        void exactFifteen() {
            // budget=8000, totalCost=9200 → (9200-8000)/8000 = 0.15
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("8800"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(r(0)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("8000"), 2, 1),
                            itin(List.of(d))));

            assertEquals(0, r.overrunRate().compareTo(new BigDecimal("0.15")));
            assertTrue(r.requiresHITL());
        }

        @Test
        @DisplayName("overrunRate 略低于 0.15 但接近 → 不触发")
        void justBelowFifteen() {
            // budget=10000, totalCost=11499 → overrunRate=0.1499
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("11099"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(r(0)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("10000"), 2, 1),
                            itin(List.of(d))));

            assertTrue(r.overrunRate().compareTo(new BigDecimal("0.15")) < 0);
            assertFalse(r.requiresHITL());
        }

        @Test
        @DisplayName("预算 = totalCost → overrunRate=0，不触发")
        void breakEven() {
            // budget=2000, totalCost=2000 (hotel=200*2*1=400, rest=1600)
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("1600"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(r(0)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("2000"), 2, 1),
                            itin(List.of(d))));

            assertEquals(0, BigDecimal.ZERO.compareTo(r.overrunRate()));
            assertFalse(r.requiresHITL());
        }
    }

    // ============ 预算红线 ≤×1.15 ============

    @Nested
    @DisplayName("预算红线：totalCost ≤ budget × 1.15 不触发")
    class BudgetRedLine {

        @Test
        @DisplayName("totalCost = budget × 1.14 → 不触发 HITL")
        void under115() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("1880"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(r(0)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("2000"), 2, 1),
                            itin(List.of(d))));

            assertTrue(r.overrunRate().compareTo(new BigDecimal("0.15")) < 0);
            assertFalse(r.requiresHITL());
        }

        @Test
        @DisplayName("totalCost = budget × 1.15（等号）→ 触发 HITL")
        void exact115() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("1900"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(r(0)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("2000"), 2, 1),
                            itin(List.of(d))));

            assertEquals(0, r.overrunRate().compareTo(new BigDecimal("0.15")));
            assertTrue(r.requiresHITL());
        }

        @Test
        @DisplayName("totalCost = budget × 1.20 → 触发 HITL")
        void over115() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("2000"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("0"))),
                    List.of(r(0)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("2000"), 2, 1),
                            itin(List.of(d))));

            assertTrue(r.overrunRate().compareTo(new BigDecimal("0.15")) > 0);
            assertTrue(r.requiresHITL());
        }
    }

    // ============ 对账一致性 ============

    @Nested
    @DisplayName("对账一致性详细场景")
    class ReconciliationDetail {

        @Test
        @DisplayName("单天：四类之和 == dailyTotal")
        void singleDayReconciliation() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("100")), a("A2", new BigDecimal("60"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("50")),
                            m(MealType.DINNER, new BigDecimal("80"))),
                    List.of(r(15)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("10000"), 2, 1),
                            itin(List.of(d))));

            DailyCostDetail detail = r.dailyBreakdowns().get(0);
            BigDecimal computedDaily = detail.ticket().add(detail.food())
                    .add(detail.transport()).add(detail.hotel());
            assertEquals(0, computedDaily.compareTo(detail.dailyTotal()));
            // 对账：总费用 == 日合计
            assertEquals(0, r.totalCost().compareTo(detail.dailyTotal()));
        }

        @Test
        @DisplayName("多天：Σ dailyTotal == totalCost")
        void multiDayReconciliation() {
            TripDay d1 = day(1,
                    List.of(a("A1", new BigDecimal("80"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("40"))),
                    List.of(r(10)));
            TripDay d2 = day(2,
                    List.of(a("A2", new BigDecimal("120"))),
                    List.of(m(MealType.DINNER, new BigDecimal("60"))),
                    List.of(r(25)));
            TripDay d3 = day(3,
                    List.of(a("A3", new BigDecimal("50"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("35"))),
                    List.of());

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("20000"), 2, 3),
                            itin(List.of(d1, d2, d3))));

            BigDecimal sumViaDaily = r.dailyBreakdowns().stream()
                    .map(DailyCostDetail::dailyTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sumViaBreakdown = r.ticket().add(r.food()).add(r.transport()).add(r.hotel());

            assertEquals(0, sumViaDaily.compareTo(r.totalCost()));
            assertEquals(0, sumViaBreakdown.compareTo(r.totalCost()));
            assertEquals(3, r.dailyBreakdowns().size());
        }

        @Test
        @DisplayName("逐日 dailyBreakdown.dayIndex 与输入一致")
        void dayIndexCorrect() {
            TripDay d1 = day(1,
                    List.of(a("A1", new BigDecimal("10"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("20"))),
                    List.of(r(5)));
            TripDay d2 = day(3,
                    List.of(a("A2", new BigDecimal("30"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("40"))),
                    List.of(r(8)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("10000"), 2, 5),
                            itin(List.of(d1, d2))));

            assertEquals(1, r.dailyBreakdowns().get(0).dayIndex());
            assertEquals(3, r.dailyBreakdowns().get(1).dayIndex());
        }
    }

    // ============ 空行程与零数据 ============

    @Nested
    @DisplayName("空数据 / 零数据")
    class ZeroData {

        @Test
        @DisplayName("itineraryResult.days() 为 null → 视为空行程，不崩溃")
        void nullDays() {
            ItineraryDesignResult itin = new ItineraryDesignResult(
                    null, List.of(), false, null, null);
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("5000"), 2, 3), itin));

            assertEquals(0, BigDecimal.ZERO.compareTo(r.totalCost()));
            assertFalse(r.requiresHITL());
        }

        @Test
        @DisplayName("每天景点为空 → ticket=ZERO")
        void emptyAttractions() {
            TripDay d = day(1, List.of(),
                    List.of(m(MealType.LUNCH, new BigDecimal("50"))),
                    List.of(r(10)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("5000"), 2, 1),
                            itin(List.of(d))));

            assertEquals(0, BigDecimal.ZERO.compareTo(r.ticket()));
        }

        @Test
        @DisplayName("每天餐饮为空 → food=ZERO，不标记 fallback（无数据 ≠ 估算）")
        void emptyMeals() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("100"))),
                    List.of(),
                    List.of(r(10)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("5000"), 2, 1),
                            itin(List.of(d))));

            assertEquals(0, BigDecimal.ZERO.compareTo(r.food()));
        }

        @Test
        @DisplayName("avgPrice=ZERO（非 null）→ 计 ZERO，不标记 fallback")
        void avgPriceZero() {
            TripDay d = day(1,
                    List.of(a("A1", BigDecimal.ZERO)),
                    List.of(m(MealType.LUNCH, BigDecimal.ZERO)),
                    List.of(r(5)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("5000"), 2, 1),
                            itin(List.of(d))));

            assertEquals(0, BigDecimal.ZERO.compareTo(r.food()));
        }

        @Test
        @DisplayName("路线 routes=null vs routes=empty 均触发降级")
        void routesNullOrEmpty() {
            TripDay dNull = day(1,
                    List.of(a("A1", BigDecimal.ZERO)),
                    List.of(m(MealType.LUNCH, new BigDecimal("40"))),
                    null);
            TripDay dEmpty = day(2,
                    List.of(a("A2", BigDecimal.ZERO)),
                    List.of(m(MealType.LUNCH, new BigDecimal("40"))),
                    List.of());

            BudgetCalculationResult r1 = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("5000"), 1, 1),
                            itin(List.of(dNull))));
            BudgetCalculationResult r2 = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("5000"), 1, 1),
                            itin(List.of(dEmpty))));

            assertEquals(0, BudgetDefaults.TRANSPORT_DAILY_FALLBACK.compareTo(r1.transport()));
            assertEquals(0, BudgetDefaults.TRANSPORT_DAILY_FALLBACK.compareTo(r2.transport()));
            assertTrue(r1.fallback());
            assertTrue(r2.fallback());
        }
    }

    // ============ 混合降级场景 ============

    @Nested
    @DisplayName("混合降级场景")
    class MixedDegradation {

        @Test
        @DisplayName("四类费用全部降级 → fallback=true, fallbackItemCount >= 4")
        void allFourDegraded() {
            TripDay d = day(1,
                    List.of(a("A1", null)),
                    List.of(m(MealType.LUNCH, null)),
                    List.of());

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("10000"), 2, 1),
                            itin(List.of(d))));

            assertTrue(r.fallback());
            assertTrue(r.telemetry().fallbackItemCount() >= 3,
                    "住宿(1) + 交通(1) + 餐饮(1, 任意餐null即标记) = 3");
            // 验证保守估算正确: hotel=200*2*1=400, transport=100, food=30*2=60
            assertEquals(0, new BigDecimal("400").compareTo(r.hotel()));
            assertEquals(0, new BigDecimal("100").compareTo(r.transport()));
            assertEquals(0, new BigDecimal("60").compareTo(r.food()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r.ticket()));
        }

        @Test
        @DisplayName("多天部分降级：第 1 天全数据，第 2 天全降级")
        void partialDayDegradation() {
            TripDay d1 = day(1,
                    List.of(a("A1", new BigDecimal("100"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("50"))),
                    List.of(r(10)));
            TripDay d2 = day(2,
                    List.of(a("A2", null)),
                    List.of(m(MealType.DINNER, null)),
                    List.of());

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("20000"), 2, 2),
                            itin(List.of(d1, d2))));

            assertTrue(r.fallback());
            DailyCostDetail det1 = r.dailyBreakdowns().get(0);
            DailyCostDetail det2 = r.dailyBreakdowns().get(1);

            // day1: ticket=100, food=50*2=100, transport=10, hotel=200*2=400
            assertEquals(0, new BigDecimal("100").compareTo(det1.ticket()));
            assertEquals(0, new BigDecimal("100").compareTo(det1.food()));
            assertEquals(0, new BigDecimal("10").compareTo(det1.transport()));

            // day2: ticket=0, food=30*2=60, transport=100, hotel=200*2=400
            assertEquals(0, BigDecimal.ZERO.compareTo(det2.ticket()));
            assertEquals(0, new BigDecimal("60").compareTo(det2.food()));
            assertEquals(0, new BigDecimal("100").compareTo(det2.transport()));

            // 对账
            BigDecimal d1Total = det1.dailyTotal();
            BigDecimal d2Total = det2.dailyTotal();
            assertEquals(0, d1Total.add(d2Total).compareTo(r.totalCost()));
        }
    }

    // ============ Telemetry 精确 ============

    @Nested
    @DisplayName("Telemetry 精确计数")
    class TelemetryPrecision {

        @Test
        @DisplayName("全正常数据 → fallbackItemCount=1（仅住宿）")
        void onlyHotelFallback() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("100"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("50"))),
                    List.of(r(10)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("10000"), 2, 1),
                            itin(List.of(d))));

            assertEquals(1, r.telemetry().fallbackItemCount());
            assertTrue(r.fallback());
        }

        @Test
        @DisplayName("混合：两餐饮 null + 一路线 null → fallbackItemCount=3（餐饮按天计数）")
        void countedFallbacks() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("100"))),
                    List.of(m(MealType.LUNCH, null), m(MealType.DINNER, null)),
                    List.of());
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("10000"), 2, 1),
                            itin(List.of(d))));

            // 住宿(1) + 餐饮(1, 任意餐估即标记) + 交通(1, 缺失) = 3
            assertEquals(3, r.telemetry().fallbackItemCount());
            assertTrue(r.fallback());
        }
    }

    // ============ 天数边界 ============

    @Nested
    @DisplayName("天数边界")
    class DaysBoundary {

        @Test
        @DisplayName("days=30（最大）→ 住宿按 30 天计算")
        void maxDays() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("100"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("50"))),
                    List.of(r(10)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("100000"), 2, 30),
                            itin(List.of(d))));

            // hotel = 2 * 30 * 200 = 12000
            assertEquals(0, new BigDecimal("12000").compareTo(r.hotel()));
        }

        @Test
        @DisplayName("tripDays 多于请求 days → 以实际 tripDays 为准")
        void moreDaysThanRequest() {
            TripDay d1 = day(1,
                    List.of(a("A1", new BigDecimal("50"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("30"))),
                    List.of(r(5)));
            TripDay d2 = day(2,
                    List.of(a("A2", new BigDecimal("60"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("35"))),
                    List.of(r(8)));

            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("10000"), 2, 1),
                            itin(List.of(d1, d2))));

            assertEquals(2, r.dailyBreakdowns().size());
        }
    }

    // ============ budget BigDecimal 特殊值 ============

    @Nested
    @DisplayName("budget 特殊值")
    class BudgetSpecialValues {

        @Test
        @DisplayName("budget=1（最小正值）→ 正常计算")
        void budgetOne() {
            TripDay d = day(1,
                    List.of(a("A1", BigDecimal.ZERO)),
                    List.of(m(MealType.LUNCH, BigDecimal.ZERO)),
                    List.of());
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(BigDecimal.ONE, 1, 1),
                            itin(List.of(d))));

            // hotel=200, transport=100, food=0, ticket=0 → totalCost=300
            // overrunRate = (300-1)/1 = 299
            assertTrue(r.totalCost().compareTo(BigDecimal.ONE) > 0);
            assertTrue(r.requiresHITL());
        }

        @Test
        @DisplayName("budget=1000000（大额）→ 无溢出")
        void largeBudget() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("1000"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("200"))),
                    List.of(r(50)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("1000000"), 10, 1),
                            itin(List.of(d))));

            assertNotNull(r.totalCost());
            assertTrue(r.totalCost().compareTo(r.hotel()) > 0);
            assertFalse(r.requiresHITL());
        }
    }

    // ============ HITL reason 与 advice ============

    @Nested
    @DisplayName("HITL reason 与调整建议")
    class HITLDetails {

        @Test
        @DisplayName("住宿占比最大 → 建议降档住宿")
        void hotelMax() {
            // 高 headcount 让住宿占比最大
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("100"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("10"))),
                    List.of(r(5)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("500"), 10, 1),
                            itin(List.of(d))));

            assertTrue(r.requiresHITL());
            assertNotNull(r.hitlReason());
            assertTrue(r.hitlReason().contains("住宿"), "住宿应占比最高: " + r.hitlReason());
            assertTrue(r.adjustmentAdvice().stream()
                    .anyMatch(a -> a.contains("住宿")));
        }

        @Test
        @DisplayName("门票占比最大 → 建议减景点")
        void ticketMax() {
            // budget=2000, ticket=2200, food=0, transport=0, hotel=400
            // totalCost=2600, overrunRate=0.30 ≥ 0.15 ✓
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("2200"))),
                    List.of(m(MealType.LUNCH, BigDecimal.ZERO)),
                    List.of(r(0)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("2000"), 2, 1),
                            itin(List.of(d))));

            assertTrue(r.requiresHITL());
            assertTrue(r.hitlReason().contains("门票"),
                    "门票应占比最高: " + r.hitlReason());
        }

        @Test
        @DisplayName("交通占比最大 → 建议公共交通")
        void transportMax() {
            TripDay d = day(1,
                    List.of(a("A1", BigDecimal.ZERO)),
                    List.of(m(MealType.LUNCH, new BigDecimal("10"))),
                    List.of(r(3000)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("1000"), 2, 1),
                            itin(List.of(d))));

            assertTrue(r.requiresHITL());
            assertTrue(r.hitlReason().contains("交通"));
        }

        @Test
        @DisplayName("HITL 不触发时 reason 为 null，advice 为空")
        void noHITLNoDetails() {
            TripDay d = day(1,
                    List.of(a("A1", new BigDecimal("10"))),
                    List.of(m(MealType.LUNCH, new BigDecimal("10"))),
                    List.of(r(1)));
            BudgetCalculationResult r = service.calculate(
                    new BudgetCalculationQuery(request(new BigDecimal("100000"), 2, 1),
                            itin(List.of(d))));

            assertFalse(r.requiresHITL());
            assertTrue(r.adjustmentAdvice().isEmpty());
        }
    }
}
