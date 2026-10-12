package com.smart.trip.skills.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.route.RoutePlanResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ItineraryDesignService} 边界与回归测试（C-008 T-6）。
 *
 * <p>覆盖时间窗超限、评分边界、重复景点、复杂场景回归。
 * 已有测试（ItineraryDesignServiceTest）覆盖率不足的边界全部在此补齐。</p>
 */
class ItineraryDesignBoundaryTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 7, 1);
    private static final GeoLocation BASE = new GeoLocation(104.06, 30.67, "成都·春熙路", "成都");

    private static Attraction att(String id, String name, double rating, int durationMin) {
        double off = Integer.parseInt(id.replaceAll("\\D", "")) * 0.01;
        GeoLocation loc = new GeoLocation(BASE.longitude() + off, BASE.latitude() + off, name, "成都");
        return new Attraction(id, name, loc, rating, durationMin, new BigDecimal("80"), List.of());
    }

    private static TripPlanRequest request(int days, TravelStyle style) {
        return new TripPlanRequest("成都", START_DATE, days,
                new BigDecimal("10000"), 2, style, List.of());
    }

    private static Route route(Attraction from, Attraction to, int durationMin) {
        return new Route(from.location(), to.location(), 10.0, durationMin, "驾车", false);
    }

    private static RoutePlanResult routeResult(List<Route> routes) {
        return new RoutePlanResult(routes, List.of(), List.of(), false, null, null);
    }

    /**
     * 使用桩推荐确保每日含午晚餐。
     */
    private final ItineraryDesignService service = new ItineraryDesignService(new StubMealRecommender());

    private static boolean hasRisk(ItineraryDesignResult result, String code) {
        return result.risks().stream().anyMatch(r -> r.code().equals(code));
    }

    // ── 时间超限（强规则 ③）──

    @Nested
    @DisplayName("时间窗边界：8:00–20:00（720 min）")
    class TimeWindowBoundary {

        @Test
        @DisplayName("单景点时长 = 720min（恰好填满一天）→ 单日行程，不超时间窗")
        void exactFillSingleDay_ok() {
            Attraction full = att("A001", "全天景点", 4.5, 720);
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1, TravelStyle.COUPLE), List.of(full), null);
            ItineraryDesignResult result = service.design(query);

            assertEquals(1, result.days().size());
            TripDay day = result.days().get(0);
            int total = day.attractions().stream().mapToInt(Attraction::durationMin).sum();
            assertEquals(720, total, "恰好填满时间窗应完整保留");
        }

        @Test
        @DisplayName("单景点时长 721min（超 1min）→ 无法填入任何天，景点被跳过")
        void singleOverOneMin_exceedsWindow() {
            Attraction over = att("A001", "超时景点", 4.5, 721);
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1, TravelStyle.COUPLE), List.of(over), null);
            ItineraryDesignResult result = service.design(query);

            assertTrue(result.days().isEmpty() || result.days().stream()
                            .flatMap(d -> d.attractions().stream())
                            .noneMatch(a -> "A001".equals(a.attractionId())),
                    "721min 景点不应被强行塞入");
        }

        @Test
        @DisplayName("多景点总时长 > 720，应减载顺延到次日")
        void multiDay_totalExceeds_shedLoads() {
            List<Attraction> candidates = List.of(
                    att("A001", "景点1", 4.5, 400),
                    att("A002", "景点2", 4.5, 300),
                    att("A003", "景点3", 4.5, 90));
            // 400+300=700 day1; 90 goes to day2
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2, TravelStyle.COUPLE), candidates, null);
            ItineraryDesignResult result = service.design(query);

            assertEquals(2, result.days().size());
            for (TripDay day : result.days()) {
                int total = day.attractions().stream().mapToInt(Attraction::durationMin).sum();
                assertTrue(total <= 720, "每日总时长不应超 720min: " + total);
            }
        }

        @Test
        @DisplayName("4 个景点恰填 720min → 单日完整安排（不超数量上限也不超时间窗）")
        void manySmallExactly720_allInOneDay() {
            List<Attraction> four = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                four.add(att("A" + String.format("%03d", i), "景点" + i, 4.5, 180));
            }
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1, TravelStyle.COUPLE), four, null);
            ItineraryDesignResult result = service.design(query);

            assertEquals(1, result.days().size());
            int total = result.days().get(0).attractions().stream()
                    .mapToInt(Attraction::durationMin).sum();
            assertEquals(720, total);
        }
    }

    // ── 评分边界（强规则 ⑥）──

    @Nested
    @DisplayName("评分边界：≥3.5 保留，<3.5 剔除")
    class RatingBoundary {

        @Test
        @DisplayName("全部候选 <3.5 → 全部剔除，空结果 + DAYS_MISMATCH")
        void allLowRating_allRemoved() {
            List<Attraction> allBad = List.of(
                    att("A001", "差评1", 2.0, 120),
                    att("A002", "差评2", 3.0, 120),
                    att("A003", "差评3", 1.5, 90));
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2, TravelStyle.COUPLE), allBad, null);
            ItineraryDesignResult result = service.design(query);

            assertTrue(result.days().isEmpty(), "全部低评分应无行程产出");
            assertTrue(hasRisk(result, ItineraryRisk.DAYS_MISMATCH));
            assertEquals(3, result.telemetry().lowRatingRemoved(), "遥测应记录移除数");
        }

        @Test
        @DisplayName("评分恰为 3.5 的全量候选 → 全部保留")
        void allExactlyThreshold_allRetained() {
            List<Attraction> borders = List.of(
                    att("A001", "边界1", 3.5, 120),
                    att("A002", "边界2", 3.5, 90),
                    att("A003", "边界3", 3.5, 100));
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2, TravelStyle.COUPLE), borders, null);
            ItineraryDesignResult result = service.design(query);

            int total = result.days().stream().flatMap(d -> d.attractions().stream())
                    .mapToInt(a -> 1).sum();
            assertEquals(3, total, "评分 3.5 应全保留");
        }

        @Test
        @DisplayName("混合评分 → 低分剔除，高分保留，遥测计数正确")
        void mixedRatings_correctTelemetry() {
            List<Attraction> mixed = List.of(
                    att("A001", "差", 2.0, 120),
                    att("A002", "好1", 4.5, 120),
                    att("A003", "差", 2.5, 90),
                    att("A004", "好2", 4.0, 100));
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2, TravelStyle.COUPLE), mixed, null);
            ItineraryDesignResult result = service.design(query);

            assertEquals(2, result.telemetry().lowRatingRemoved(), "应精确剔除 2 个低分");
            assertTrue(hasRisk(result, ItineraryRisk.LOW_RATING_REMOVED));
            int retained = result.days().stream().flatMap(d -> d.attractions().stream())
                    .mapToInt(a -> 1).sum();
            assertEquals(2, retained, "应保留 2 个高分");
        }
    }

    // ── 重复景点边界（强规则 ②）──

    @Nested
    @DisplayName("重复景点去重边界")
    class DuplicateBoundary {

        @Test
        @DisplayName("3 个相同 ID，保留评分最高者")
        void tripleDuplicate_keepHighestRating() {
            Attraction d1 = att("A001", "重复景点", 3.8, 120);
            Attraction d2 = att("A001", "重复景点", 4.2, 120);
            Attraction d3 = att("A001", "重复景点", 4.5, 120);
            Attraction other = att("A002", "独立景点", 4.0, 120);
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1, TravelStyle.COUPLE), List.of(d1, d2, d3, other), null);
            ItineraryDesignResult result = service.design(query);

            Set<String> ids = new HashSet<>();
            result.days().forEach(d -> d.attractions().forEach(a -> ids.add(a.attractionId())));
            assertEquals(2, ids.size(), "3重复+1独立 → 去重后 2");
            Attraction kept = result.days().stream()
                    .flatMap(d -> d.attractions().stream())
                    .filter(a -> "A001".equals(a.attractionId()))
                    .findFirst().orElseThrow();
            assertEquals(4.5, kept.rating(), "应保留评分最高 4.5");
        }

        @Test
        @DisplayName("重复景点相同评分 → 保留首次出现者")
        void duplicateSameRating_keepFirst() {
            Attraction first = new Attraction("A001", "景点-先", BASE, 4.0, 120,
                    new BigDecimal("100"), List.of("经典"));
            Attraction second = new Attraction("A001", "景点-后", BASE, 4.0, 120,
                    new BigDecimal("80"), List.of("平价"));
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1, TravelStyle.COUPLE), List.of(first, second), null);
            ItineraryDesignResult result = service.design(query);

            Attraction kept = result.days().stream()
                    .flatMap(d -> d.attractions().stream())
                    .filter(a -> "A001".equals(a.attractionId()))
                    .findFirst().orElseThrow();
            assertEquals("景点-先", kept.name(), "同评分应保留首次出现者");
        }

        @Test
        @DisplayName("多对重复 + 多天 → 全部去重且跨天不重复")
        void multiDuplicateCrossDay_allUnique() {
            List<Attraction> mixed = List.of(
                    att("A001", "景点1-a", 3.5, 120),
                    att("A001", "景点1-b", 4.5, 120), // dedup → 4.5
                    att("A002", "景点2-a", 4.0, 120),
                    att("A002", "景点2-b", 3.0, 120), // dedup → 4.0
                    att("A003", "景点3", 4.5, 120),
                    att("A004", "景点4", 4.5, 120));
            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2, TravelStyle.COUPLE), mixed, null);
            ItineraryDesignResult result = service.design(query);

            Set<String> allIds = new HashSet<>();
            for (TripDay day : result.days()) {
                for (Attraction a : day.attractions()) {
                    assertTrue(allIds.add(a.attractionId()),
                            "景点跨天/天内重复: " + a.attractionId());
                }
            }
            assertEquals(4, allIds.size(), "6 输入(含2对重复)→去重后 4");
        }
    }

    // ── 交通耗时边界（强规则 ⑤）──

    @Nested
    @DisplayName("交通耗时 ≤120min 边界")
    class TransitBoundary {

        @Test
        @DisplayName("恰为 120min → 可安排在同日相邻")
        void exactly120min_allowed() {
            Attraction a1 = att("A001", "景点A", 4.5, 120);
            Attraction a2 = att("A002", "景点B", 4.5, 120);
            Attraction a3 = att("A003", "景点C", 4.5, 100);
            Route r12 = route(a1, a2, 120); // exactly at limit
            Route r23 = route(a2, a3, 30);

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(1, TravelStyle.COUPLE), List.of(a1, a2, a3),
                    routeResult(List.of(r12, r23)));
            ItineraryDesignResult result = service.design(query);

            assertEquals(1, result.days().size());
            assertFalse(hasRisk(result, ItineraryRisk.TRANSIT_OVER_LIMIT),
                    "120min 不应标记为超限");
        }

        @Test
        @DisplayName("121min → 拆到不同天，不安排在同日相邻")
        void over121min_splitToDifferentDays() {
            Attraction a1 = att("A001", "景点A", 4.5, 60);
            Attraction a2 = att("A002", "景点B", 4.5, 60);
            Route r12 = route(a1, a2, 121);

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2, TravelStyle.COUPLE), List.of(a1, a2),
                    routeResult(List.of(r12)));
            ItineraryDesignResult result = service.design(query);

            assertTrue(hasRisk(result, ItineraryRisk.TRANSIT_OVER_LIMIT));
            // 两个景点应在不同天
            for (TripDay day : result.days()) {
                if (day.attractions().size() >= 2) {
                    for (Route r : day.routes()) {
                        assertTrue(r.durationMin() <= 120,
                                "同日相邻交通不得 >2h");
                    }
                }
            }
        }
    }

    // ── 复杂场景回归 ──

    @Nested
    @DisplayName("复杂场景回归：多约束同时作用")
    class ComplexRegression {

        @Test
        @DisplayName("混合低分+重复+超时路线 → 正确过滤、去重、拆分")
        void mixedConstraints_allRulesApplied() {
            Attraction dupGood = att("A001", "好景", 4.5, 120);
            Attraction dupBad = att("A001", "好景低分版", 3.5, 120); // same ID, lower score
            Attraction low = att("A002", "差评", 2.0, 120);
            Attraction normal1 = att("A003", "景点3", 4.0, 90);
            Attraction normal2 = att("A004", "景点4", 4.0, 90);

            Route r34 = route(normal1, normal2, 150); // over limit

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(2, TravelStyle.COUPLE),
                    List.of(dupGood, dupBad, low, normal1, normal2),
                    routeResult(List.of(r34)));
            ItineraryDesignResult result = service.design(query);

            // 强规则验证
            assertTrue(hasRisk(result, ItineraryRisk.LOW_RATING_REMOVED));
            assertTrue(hasRisk(result, ItineraryRisk.TRANSIT_OVER_LIMIT));

            // 去重：A001 只出现一次，保留 4.5
            Set<String> ids = new HashSet<>();
            result.days().forEach(d -> d.attractions().forEach(a -> ids.add(a.attractionId())));
            assertTrue(ids.contains("A001"), "应保留 A001");
            assertFalse(ids.contains("A002"), "低分 A002 应剔除");
            assertEquals(3, ids.size(), "去重+过滤后应 3 景点: A001/A003/A004");

            // 午晚餐不缺
            for (TripDay day : result.days()) {
                boolean lunch = day.meals().stream().anyMatch(m -> m.mealType() == MealType.LUNCH);
                boolean dinner = day.meals().stream().anyMatch(m -> m.mealType() == MealType.DINNER);
                assertTrue(lunch && dinner, "Day " + day.dayIndex() + " 餐别不完整");
            }
        }

        @Test
        @DisplayName("全量约束触发：低分+重复+超时+餐饮空 → 全部风险码 + 遥测正确 + 每日午晚餐不缺失")
        void allConstraints_withMealFallback() {
            ItineraryDesignService emptyMealSvc = new ItineraryDesignService(q -> List.of());

            Attraction dupHigh = att("A001", "好景", 4.8, 90);
            Attraction dupLow = att("A001", "好景低分", 3.6, 120);
            Attraction bad = att("A002", "差评", 2.0, 60);
            Attraction a3 = att("A003", "景点C", 4.5, 120);
            Attraction a4 = att("A004", "景点D", 4.2, 100);
            Attraction a5 = att("A005", "景点E", 4.0, 90);

            Route overLimit = route(a3, a4, 180); // transit >2h → risk

            ItineraryDesignQuery query = new ItineraryDesignQuery(
                    request(3, TravelStyle.FAMILY),
                    List.of(dupHigh, dupLow, bad, a3, a4, a5),
                    routeResult(List.of(overLimit)));
            ItineraryDesignResult result = emptyMealSvc.design(query);

            // 所有应触发的风险码
            assertTrue(hasRisk(result, ItineraryRisk.LOW_RATING_REMOVED),
                    "差评景点应触发 LOW_RATING_REMOVED");
            assertTrue(hasRisk(result, ItineraryRisk.TRANSIT_OVER_LIMIT),
                    "180min 耗时路线应触发 TRANSIT_OVER_LIMIT");
            assertTrue(hasRisk(result, ItineraryRisk.MEAL_FALLBACK),
                    "餐饮空应触发 MEAL_FALLBACK");
            assertTrue(result.fallback(), "有降级时应 fallback=true");

            // 去重验证
            Set<String> ids = new HashSet<>();
            result.days().forEach(d -> d.attractions().forEach(a -> ids.add(a.attractionId())));
            assertFalse(ids.contains("A002"), "低分应剔除");
            assertTrue(ids.contains("A001"), "A001 应保留高评分版");
            assertEquals(4, ids.size(), "A001/A003/A004/A005 → 4");

            // 遥测
            assertEquals(1, result.telemetry().lowRatingRemoved());
            assertTrue(result.telemetry().mealFallbackCount() >= 2, "至少 2 餐兜底");

            // 兜底餐名含"推荐餐厅"（source=FALLBACK）
            for (TripDay day : result.days()) {
                boolean lunch = day.meals().stream().anyMatch(m -> m.mealType() == MealType.LUNCH);
                boolean dinner = day.meals().stream().anyMatch(m -> m.mealType() == MealType.DINNER);
                assertTrue(lunch && dinner,
                        "Day " + day.dayIndex() + " 即使餐饮全空也不缺午晚餐");
                // 时间窗约束
                int total = day.attractions().stream().mapToInt(Attraction::durationMin).sum();
                assertTrue(total <= 720,
                        "Day " + day.dayIndex() + " 总时长 " + total + "min ≤ 720min");
            }
        }
    }
}
