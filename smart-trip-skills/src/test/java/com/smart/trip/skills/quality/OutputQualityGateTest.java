package com.smart.trip.skills.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.PlanStatus;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.Budget;
import com.smart.trip.common.model.BudgetBreakdown;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link OutputQualityGate} 单测：装配后 TripPlan 全局不变量统一复核（C-005 AC-6 / 业务模型.md §4/§4.1）。
 *
 * <p>覆盖天数一致、景点不重复、每日含午晚餐、预算红线（×1.15）、评分阈值（≥3.5）；违规标记
 * {@code requiresHITL} 并给出原因，且不丢弃数据（边界 #5）。含验收用例 Case-4（天数不一致）。</p>
 */
class OutputQualityGateTest {

    private static final GeoLocation LOC = new GeoLocation(102.7, 25.0, "昆明市", "昆明");

    private final OutputQualityGate gate = new OutputQualityGate();

    private static Attraction attraction(String id, double rating) {
        return new Attraction(id, "景点" + id, LOC, rating, 120, new BigDecimal("100"), List.of("古镇"));
    }

    private static Meal meal(MealType type) {
        return new Meal("餐厅" + type, LOC, type, new BigDecimal("80"), List.of("过桥米线"));
    }

    private static TripDay fullDay(int index, Attraction attraction) {
        return new TripDay(LocalDate.of(2026, 7, index), index,
                List.of(attraction),
                List.of(meal(MealType.LUNCH), meal(MealType.DINNER)),
                List.of(), new BigDecimal("500"));
    }

    private static TripPlanRequest request(int days) {
        return new TripPlanRequest("云南", LocalDate.of(2026, 7, 1), days,
                new BigDecimal("1000"), 2, TravelStyle.COUPLE, List.of("古镇"));
    }

    private static Budget budget(String total) {
        return new Budget(new BigDecimal(total), new BigDecimal(total),
                BigDecimal.ZERO, new BudgetBreakdown(
                        new BigDecimal("100"), new BigDecimal("100"),
                        new BigDecimal("100"), new BigDecimal("100")));
    }

    private static TripPlan plan(TripPlanRequest req, List<TripDay> days, String totalCost, String budgetTotal) {
        return new TripPlan("P-20260701-001", req, days, new BigDecimal(totalCost),
                budget(budgetTotal), PlanStatus.REVIEW, false);
    }

    private static TripPlan happyPlan() {
        TripPlanRequest req = request(2);
        List<TripDay> days = List.of(fullDay(1, attraction("A-001", 4.5)),
                fullDay(2, attraction("A-002", 4.0)));
        return plan(req, days, "1000", "1000");
    }

    @Nested
    @DisplayName("合规方案：通过，无需 HITL")
    class HappyPath {

        @Test
        void compliantPlanPasses() {
            QualityGateResult result = gate.review(happyPlan());
            assertTrue(result.passed());
            assertFalse(result.requiresHITL());
            assertTrue(result.violations().isEmpty());
        }

        @Test
        @DisplayName("总费用恰为预算 ×1.15 临界值仍通过")
        void exactlyAtBudgetCeilingPasses() {
            TripPlanRequest req = request(2);
            List<TripDay> days = List.of(fullDay(1, attraction("A-001", 4.5)),
                    fullDay(2, attraction("A-002", 4.0)));
            // 预算 1000 → 红线 1150；恰好 1150 不算超支
            QualityGateResult result = gate.review(plan(req, days, "1150", "1000"));
            assertTrue(result.passed());
        }
    }

    @Nested
    @DisplayName("天数一致（Case-4）")
    class DaysConsistency {

        @Test
        @DisplayName("days≠request.days → requiresHITL=true + 原因，且不丢数据")
        void daysMismatchTriggersHitl() {
            TripPlanRequest req = request(3); // 需求 3 天
            List<TripDay> days = List.of(fullDay(1, attraction("A-001", 4.5)),
                    fullDay(2, attraction("A-002", 4.0))); // 实际 2 天
            TripPlan input = plan(req, days, "1000", "1000");

            QualityGateResult result = gate.review(input);
            assertTrue(result.requiresHITL());
            assertTrue(hasRule(result, OutputQualityGate.RULE_DAYS_MISMATCH));
            // 边界 #5：违规不丢弃数据，原 plan 仍可取回
            assertSame(input, result.plan());
        }
    }

    @Nested
    @DisplayName("景点不重复")
    class UniqueAttractions {

        @Test
        void duplicateAttractionFlagged() {
            TripPlanRequest req = request(2);
            List<TripDay> days = List.of(fullDay(1, attraction("A-001", 4.5)),
                    fullDay(2, attraction("A-001", 4.0))); // 同一 A-001 重复
            QualityGateResult result = gate.review(plan(req, days, "1000", "1000"));
            assertTrue(result.requiresHITL());
            assertTrue(hasRule(result, OutputQualityGate.RULE_DUPLICATE_ATTRACTION));
        }
    }

    @Nested
    @DisplayName("每日含午晚餐")
    class MealsPerDay {

        @Test
        void missingDinnerFlagged() {
            TripPlanRequest req = request(1);
            TripDay lunchOnly = new TripDay(LocalDate.of(2026, 7, 1), 1,
                    List.of(attraction("A-001", 4.5)),
                    List.of(meal(MealType.LUNCH)), // 缺晚餐
                    List.of(), new BigDecimal("500"));
            QualityGateResult result = gate.review(plan(req, List.of(lunchOnly), "1000", "1000"));
            assertTrue(result.requiresHITL());
            assertTrue(hasRule(result, OutputQualityGate.RULE_MISSING_MEAL));
        }
    }

    @Nested
    @DisplayName("预算红线 ×1.15")
    class BudgetRedline {

        @Test
        @DisplayName("超出红线 → 超预算违规 + HITL")
        void overrunBeyondCeilingFlagged() {
            TripPlanRequest req = request(2);
            List<TripDay> days = List.of(fullDay(1, attraction("A-001", 4.5)),
                    fullDay(2, attraction("A-002", 4.0)));
            // 预算 1000 → 红线 1150；1151 超线
            QualityGateResult result = gate.review(plan(req, days, "1151", "1000"));
            assertTrue(result.requiresHITL());
            assertTrue(hasRule(result, OutputQualityGate.RULE_BUDGET_OVERRUN));
        }
    }

    @Nested
    @DisplayName("评分阈值 ≥3.5")
    class RatingThreshold {

        @Test
        void lowRatingFlagged() {
            TripPlanRequest req = request(2);
            List<TripDay> days = List.of(fullDay(1, attraction("A-001", 3.4)), // < 3.5
                    fullDay(2, attraction("A-002", 4.0)));
            QualityGateResult result = gate.review(plan(req, days, "1000", "1000"));
            assertTrue(result.requiresHITL());
            assertTrue(hasRule(result, OutputQualityGate.RULE_LOW_RATING));
        }

        @Test
        @DisplayName("评分恰为 3.5 临界值通过")
        void ratingExactlyAtThresholdPasses() {
            TripPlanRequest req = request(2);
            List<TripDay> days = List.of(fullDay(1, attraction("A-001", 3.5)),
                    fullDay(2, attraction("A-002", 3.5)));
            QualityGateResult result = gate.review(plan(req, days, "1000", "1000"));
            assertFalse(hasRule(result, OutputQualityGate.RULE_LOW_RATING));
        }
    }

    @Nested
    @DisplayName("多违规累加与非法输入")
    class Aggregate {

        @Test
        @DisplayName("多项违规同时给出，全部计入原因清单")
        void multipleViolationsAccumulate() {
            TripPlanRequest req = request(3); // 天数不一致
            List<TripDay> days = List.of(fullDay(1, attraction("A-001", 3.0)), // 低评分
                    fullDay(2, attraction("A-001", 4.0))); // 重复景点
            QualityGateResult result = gate.review(plan(req, days, "5000", "1000")); // 超预算
            assertTrue(result.requiresHITL());
            assertTrue(result.violations().size() >= 4);
            assertTrue(hasRule(result, OutputQualityGate.RULE_DAYS_MISMATCH));
            assertTrue(hasRule(result, OutputQualityGate.RULE_DUPLICATE_ATTRACTION));
            assertTrue(hasRule(result, OutputQualityGate.RULE_LOW_RATING));
            assertTrue(hasRule(result, OutputQualityGate.RULE_BUDGET_OVERRUN));
        }

        @Test
        @DisplayName("null plan 快速失败")
        void nullPlanRejected() {
            assertThrows(InvalidRequestException.class, () -> gate.review(null));
        }
    }

    private static boolean hasRule(QualityGateResult result, String rule) {
        return result.violations().stream().anyMatch(v -> v.rule().equals(rule));
    }
}
