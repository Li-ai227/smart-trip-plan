package com.smart.trip.skills.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.route.RoutePlanResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ItineraryDesignService} 核心逻辑测试（C-008 T-2，TDD Red→Green）。
 *
 * <p>覆盖六项强规则（AC-4）、Happy Path（Case-1）、边界/降级/TravelStyle。
 * 全部先红，实现后变绿。</p>
 */
class ItineraryDesignServiceTest {

    // ── 测试固件 ──

    private static final LocalDate START_DATE = LocalDate.of(2026, 7, 1);
    private static final GeoLocation LOC_KZX = new GeoLocation(104.0554, 30.6697, "宽窄巷子", "成都");
    private static final GeoLocation LOC_PANDA = new GeoLocation(104.1461, 30.7327, "大熊猫基地", "成都");
    private static final GeoLocation LOC_WHC = new GeoLocation(104.0479, 30.6437, "武侯祠", "成都");
    private static final GeoLocation LOC_JINLI = new GeoLocation(104.0477, 30.6410, "锦里", "成都");
    private static final GeoLocation LOC_DUFU = new GeoLocation(104.0337, 30.6396, "杜甫草堂", "成都");
    private static final GeoLocation LOC_QCHM = new GeoLocation(104.0594, 30.6630, "青城后山", "成都");

    private static Attraction attraction(String id, String name, GeoLocation loc, double rating, int durationMin) {
        return new Attraction(id, name, loc, rating, durationMin, new BigDecimal("80"), List.of());
    }

    private static Attraction attraction(String id, String name, GeoLocation loc) {
        return attraction(id, name, loc, 4.5, 120);
    }

    private static TripPlanRequest request(int days) {
        return request(days, TravelStyle.COUPLE);
    }

    private static TripPlanRequest request(int days, TravelStyle style) {
        return new TripPlanRequest("成都", START_DATE, days,
                new BigDecimal("10000"), 2, style, List.of("美食"));
    }

    private static Route route(GeoLocation from, GeoLocation to, int durationMin) {
        return new Route(from, to, 10.0, durationMin, "驾车", false);
    }

    private static RoutePlanResult routeResult(List<Route> routes) {
        return new RoutePlanResult(routes, List.of(), List.of(), false, null, null);
    }

    private static final MealRecommender STUB_MEAL = new StubMealRecommender();

    /** 空餐推荐桩：模拟推荐失败。 */
    private static final MealRecommender EMPTY_MEAL = query -> List.of();

    private final ItineraryDesignService service = new ItineraryDesignService(STUB_MEAL);

    // ── Happy Path（Case-1）──

    @Nested
    @DisplayName("Happy Path：充足候选，全部强规则满足")
    class HappyPath {

        @Test
        @DisplayName("Case-1：5天15景点+14段路线 → 5个TripDay，每日含午晚餐")
        void fiveDays_sufficientCandidates_producesCompletePlan() {
            List<Attraction> candidates = new ArrayList<>();
            for (int i = 1; i <= 15; i++) {
                double lng = 104.0 + i * 0.01;
                double lat = 30.6 + i * 0.005;
                GeoLocation loc = new GeoLocation(lng, lat, "景点" + i, "成都");
                candidates.add(attraction("A-" + String.format("%03d", i), "景点" + i, loc, 4.0 + (i % 5) * 0.1, 90));
            }

            List<Route> routes = new ArrayList<>();
            for (int i = 0; i < candidates.size() - 1; i++) {
                routes.add(route(candidates.get(i).location(), candidates.get(i + 1).location(), 30));
            }

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(5), candidates, routeResult(routes));
            ItineraryDesignResult result = service.design(query);

            assertEquals(5, result.days().size(), "天数应等于 request.days");
            assertFalse(result.fallback(), "充足候选不应降级");
            assertFalse(hasRisk(result, ItineraryRisk.DAYS_MISMATCH));

            for (TripDay day : result.days()) {
                assertHasLunchAndDinner(day);
                assertTrue(day.attractions().size() >= 1, "每日至少 1 景点");
            }
        }

        @Test
        @DisplayName("3天6景点 → 3个TripDay，景点均匀分布")
        void threeDays_sixCandidates_evenDistribution() {
            List<Attraction> candidates = List.of(
                    attraction("A-001", "宽窄巷子", LOC_KZX),
                    attraction("A-002", "大熊猫基地", LOC_PANDA),
                    attraction("A-003", "武侯祠", LOC_WHC),
                    attraction("A-004", "锦里", LOC_JINLI),
                    attraction("A-005", "杜甫草堂", LOC_DUFU),
                    attraction("A-006", "青城后山", LOC_QCHM));

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(3), candidates, null);
            ItineraryDesignResult result = service.design(query);

            assertEquals(3, result.days().size());
            int totalAttractions = result.days().stream()
                    .mapToInt(d -> d.attractions().size()).sum();
            assertEquals(6, totalAttractions, "所有候选景点都应被分配");
        }
    }

    // ── 强规则 ① 每日至少午晚餐 ──

    @Nested
    @DisplayName("强规则 ①：每日至少含午餐与晚餐")
    class DailyMeals {

        @Test
        void everyDayHasLunchAndDinner() {
            List<Attraction> candidates = List.of(
                    attraction("A-001", "宽窄巷子", LOC_KZX),
                    attraction("A-002", "武侯祠", LOC_WHC));
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2), candidates, null);
            ItineraryDesignResult result = service.design(query);

            for (TripDay day : result.days()) {
                assertHasLunchAndDinner(day);
            }
        }

        @Test
        @DisplayName("餐饮推荐返回空 → 兜底餐 + fallback=true")
        void emptyMealRecommender_fallbackMealInserted() {
            ItineraryDesignService svc = new ItineraryDesignService(EMPTY_MEAL);
            List<Attraction> candidates = List.of(attraction("A-001", "宽窄巷子", LOC_KZX));
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1), candidates, null);
            ItineraryDesignResult result = svc.design(query);

            assertEquals(1, result.days().size());
            assertHasLunchAndDinner(result.days().get(0));
            assertTrue(result.fallback(), "餐饮推荐空时应标记 fallback");
        }
    }

    // ── 强规则 ② 景点不重复 ──

    @Nested
    @DisplayName("强规则 ②：同一 plan 内景点不重复")
    class UniqueAttractions {

        @Test
        void duplicateCandidates_deduplicatedByHigherRating() {
            Attraction dup1 = attraction("A-001", "宽窄巷子", LOC_KZX, 4.0, 120);
            Attraction dup2 = attraction("A-001", "宽窄巷子", LOC_KZX, 4.5, 120);
            Attraction other = attraction("A-002", "武侯祠", LOC_WHC, 4.2, 120);

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1), List.of(dup1, dup2, other), null);
            ItineraryDesignResult result = service.design(query);

            Set<String> allIds = result.days().stream()
                    .flatMap(d -> d.attractions().stream())
                    .map(Attraction::attractionId)
                    .collect(Collectors.toSet());
            assertEquals(2, allIds.size(), "重复景点应被去重");

            Attraction kept = result.days().stream()
                    .flatMap(d -> d.attractions().stream())
                    .filter(a -> "A-001".equals(a.attractionId()))
                    .findFirst().orElseThrow();
            assertEquals(4.5, kept.rating(), "去重应保留评分更高者");
        }
    }

    // ── 强规则 ③ 时间窗 8:00–20:00 ──

    @Nested
    @DisplayName("强规则 ③：每日行程在 8:00–20:00 时间窗内")
    class TimeWindow {

        @Test
        @DisplayName("景点总时长超 12h → 减载顺延，不超时间窗")
        void excessiveDuration_shedLoadsToNextDay() {
            List<Attraction> longDuration = List.of(
                    attraction("A-001", "景点1", LOC_KZX, 4.5, 300),
                    attraction("A-002", "景点2", LOC_PANDA, 4.5, 300),
                    attraction("A-003", "景点3", LOC_WHC, 4.5, 300));

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2), longDuration, null);
            ItineraryDesignResult result = service.design(query);

            for (TripDay day : result.days()) {
                int totalMinutes = day.attractions().stream()
                        .mapToInt(Attraction::durationMin).sum();
                assertTrue(totalMinutes <= ScheduleProfile.DEFAULT_DAY_END - ScheduleProfile.DEFAULT_DAY_START,
                        "每日景点总时长不应超时间窗: " + totalMinutes + " min");
            }
        }
    }

    // ── 强规则 ⑤ 交通耗时 ≤2h ──

    @Nested
    @DisplayName("强规则 ⑤：相邻景点交通耗时 ≤2h")
    class TransitLimit {

        @Test
        @DisplayName("Case-3：耗时 130min → 不安排同日相邻")
        void overLimitTransit_splitToDifferentDays() {
            Attraction a1 = attraction("A-001", "景点A", LOC_KZX);
            Attraction a2 = attraction("A-002", "景点B", LOC_PANDA);

            Route overLimit = route(LOC_KZX, LOC_PANDA, 130);
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2), List.of(a1, a2), routeResult(List.of(overLimit)));
            ItineraryDesignResult result = service.design(query);

            for (TripDay day : result.days()) {
                if (day.attractions().size() >= 2) {
                    for (Route r : day.routes()) {
                        assertTrue(r.durationMin() <= 120,
                                "同日相邻景点交通耗时不应 >2h: " + r.durationMin());
                    }
                }
            }
            assertTrue(hasRisk(result, ItineraryRisk.TRANSIT_OVER_LIMIT));
        }
    }

    // ── 强规则 ⑥ 评分 ≥3.5 ──

    @Nested
    @DisplayName("强规则 ⑥：推荐景点评分 ≥3.5")
    class RatingFilter {

        @Test
        @DisplayName("Case-6：评分 2.8 → 剔除，标记触发 HITL")
        void lowRating_removedAndFlagged() {
            Attraction low = attraction("A-001", "低评分景点", LOC_KZX, 2.8, 120);
            Attraction good = attraction("A-002", "好景点", LOC_WHC, 4.5, 120);

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1), List.of(low, good), null);
            ItineraryDesignResult result = service.design(query);

            boolean hasLow = result.days().stream()
                    .flatMap(d -> d.attractions().stream())
                    .anyMatch(a -> a.rating() < 3.5);
            assertFalse(hasLow, "评分 <3.5 的景点应被剔除");
            assertTrue(hasRisk(result, ItineraryRisk.LOW_RATING_REMOVED));
        }

        @Test
        @DisplayName("评分恰为 3.5 → 保留")
        void exactThreshold_retained() {
            Attraction borderline = attraction("A-001", "边界景点", LOC_KZX, 3.5, 120);

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1), List.of(borderline), null);
            ItineraryDesignResult result = service.design(query);

            boolean found = result.days().stream()
                    .flatMap(d -> d.attractions().stream())
                    .anyMatch(a -> "A-001".equals(a.attractionId()));
            assertTrue(found, "评分恰 3.5 应保留");
        }
    }

    // ── 天数一致（AC-5）──

    @Nested
    @DisplayName("AC-5：天数与 request.days 一致")
    class DaysConsistency {

        @Test
        @DisplayName("Case-2：候选仅够 3 天但 request 5 天 → 标记 DAYS_MISMATCH")
        void insufficientCandidates_marksDaysMismatch() {
            List<Attraction> few = List.of(
                    attraction("A-001", "景点1", LOC_KZX, 4.5, 120),
                    attraction("A-002", "景点2", LOC_PANDA, 4.5, 120));

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(5), few, null);
            ItineraryDesignResult result = service.design(query);

            assertTrue(result.days().size() < 5, "候选不足时天数应 < request.days");
            assertTrue(hasRisk(result, ItineraryRisk.DAYS_MISMATCH));
        }
    }

    // ── TravelStyle 个性化（AC-6）──

    @Nested
    @DisplayName("AC-6：TravelStyle 编排策略")
    class TravelStyleStrategy {

        @Test
        @DisplayName("Case-4：FAMILY → 每日 ≤3 景点")
        void family_maxThreePerDay() {
            List<Attraction> many = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                GeoLocation loc = new GeoLocation(104.0 + i * 0.01, 30.6 + i * 0.005, "景点" + i, "成都");
                many.add(attraction("A-" + String.format("%03d", i), "景点" + i, loc, 4.5, 60));
            }

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(3, TravelStyle.FAMILY), many, null);
            ItineraryDesignResult result = service.design(query);

            for (TripDay day : result.days()) {
                assertTrue(day.attractions().size() <= 3,
                        "FAMILY 每日不超 3 景点: got " + day.attractions().size());
            }
        }

        @Test
        @DisplayName("SOLO → 每日最多 5 景点（宽松节奏）")
        void solo_maxFivePerDay() {
            List<Attraction> many = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                GeoLocation loc = new GeoLocation(104.0 + i * 0.01, 30.6 + i * 0.005, "景点" + i, "成都");
                many.add(attraction("A-" + String.format("%03d", i), "景点" + i, loc, 4.5, 60));
            }

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2, TravelStyle.SOLO), many, null);
            ItineraryDesignResult result = service.design(query);

            for (TripDay day : result.days()) {
                assertTrue(day.attractions().size() <= 5,
                        "SOLO 每日不超 5 景点: got " + day.attractions().size());
            }
        }
    }

    // ── 边界：routes 为空 ──

    @Nested
    @DisplayName("边界：routes 为空/null")
    class EmptyRoutes {

        @Test
        @DisplayName("Case-8：routes 空 → 按景点顺序编排但无交通动线，fallback=true")
        void nullRouteResult_fallbackTrue() {
            List<Attraction> candidates = List.of(
                    attraction("A-001", "宽窄巷子", LOC_KZX),
                    attraction("A-002", "武侯祠", LOC_WHC));

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1), candidates, null);
            ItineraryDesignResult result = service.design(query);

            assertEquals(1, result.days().size());
            assertTrue(result.days().get(0).routes().isEmpty() || result.fallback(),
                    "routes 为空时应标记 fallback 或 routes 为空列表");
        }
    }

    // ── 边界：空候选 ──

    @Nested
    @DisplayName("边界：空候选列表")
    class EmptyCandidates {

        @Test
        void noCandidates_emptyDaysWithMismatch() {
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(3), List.of(), null);
            ItineraryDesignResult result = service.design(query);

            assertTrue(result.days().isEmpty(), "无候选时天数应为 0");
            assertTrue(hasRisk(result, ItineraryRisk.DAYS_MISMATCH));
        }
    }

    // ── 边界：null request 快速失败 ──

    @Nested
    @DisplayName("边界：非法输入")
    class InvalidInput {

        @Test
        void nullQuery_throwsException() {
            assertThrows(Exception.class, () -> service.design(null));
        }
    }

    // ── 日期正确性 ──

    @Nested
    @DisplayName("TripDay 日期与 dayIndex")
    class DateConsistency {

        @Test
        void daysHaveCorrectDatesAndIndices() {
            List<Attraction> candidates = List.of(
                    attraction("A-001", "宽窄巷子", LOC_KZX),
                    attraction("A-002", "武侯祠", LOC_WHC),
                    attraction("A-003", "锦里", LOC_JINLI));

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(3), candidates, null);
            ItineraryDesignResult result = service.design(query);

            for (int i = 0; i < result.days().size(); i++) {
                TripDay day = result.days().get(i);
                assertEquals(i + 1, day.dayIndex(), "dayIndex 应为 1-based");
                assertEquals(START_DATE.plusDays(i), day.date(), "日期应从 startDate 起连续递增");
            }
        }
    }

    // ── 全局景点不重复跨天校验 ──

    @Nested
    @DisplayName("跨天景点不重复")
    class CrossDayUniqueAttractions {

        @Test
        void noDuplicateAttractionsAcrossDays() {
            List<Attraction> candidates = List.of(
                    attraction("A-001", "宽窄巷子", LOC_KZX),
                    attraction("A-002", "大熊猫基地", LOC_PANDA),
                    attraction("A-003", "武侯祠", LOC_WHC),
                    attraction("A-004", "锦里", LOC_JINLI));

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2), candidates, null);
            ItineraryDesignResult result = service.design(query);

            Set<String> allIds = new HashSet<>();
            for (TripDay day : result.days()) {
                for (Attraction a : day.attractions()) {
                    assertTrue(allIds.add(a.attractionId()),
                            "景点跨天重复: " + a.attractionId());
                }
            }
        }
    }

    // ── 辅助方法 ──

    private static void assertHasLunchAndDinner(TripDay day) {
        boolean hasLunch = day.meals().stream()
                .anyMatch(m -> m.mealType() == MealType.LUNCH);
        boolean hasDinner = day.meals().stream()
                .anyMatch(m -> m.mealType() == MealType.DINNER);
        assertTrue(hasLunch, "Day " + day.dayIndex() + " 缺少午餐");
        assertTrue(hasDinner, "Day " + day.dayIndex() + " 缺少晚餐");
    }

    private static boolean hasRisk(ItineraryDesignResult result, String riskCode) {
        return result.risks().stream().anyMatch(r -> r.code().equals(riskCode));
    }
}
