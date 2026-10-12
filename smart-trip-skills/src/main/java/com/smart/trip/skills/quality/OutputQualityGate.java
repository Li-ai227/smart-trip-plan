package com.smart.trip.skills.quality;

import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 全局一致性统一复核层（C-005 AC-6 / 业务模型.md §4.1）。
 *
 * <p>对装配后的 {@link TripPlan} 统一复核全局不变量（业务模型.md §4）：天数一致、景点不重复、
 * 每日含午晚餐、预算红线（≤ 预算 ×1.15）、推荐景点评分 ≥3.5。任一违规即置
 * {@code requiresHITL=true} 并给出原因，但保留原 plan（边界 #5，标记而非抛弃）。</p>
 *
 * <p>职责边界（业务模型.md §4.1）：Agent 负责候选筛选与发现，本 Gate 负责结果统一复核，
 * Server 负责对外暴露 {@code requiresHumanIntervention}；同一规则不在多层无约束复制。</p>
 */
public final class OutputQualityGate {

    /** 违规规则：行程天数与请求天数不一致。 */
    public static final String RULE_DAYS_MISMATCH = "DAYS_MISMATCH";

    /** 违规规则：同一方案内景点重复。 */
    public static final String RULE_DUPLICATE_ATTRACTION = "DUPLICATE_ATTRACTION";

    /** 违规规则：某日缺少午餐或晚餐。 */
    public static final String RULE_MISSING_MEAL = "MISSING_MEAL";

    /** 违规规则：总费用超出预算红线（×1.15）。 */
    public static final String RULE_BUDGET_OVERRUN = "BUDGET_OVERRUN";

    /** 违规规则：推荐景点评分低于阈值（3.5）。 */
    public static final String RULE_LOW_RATING = "LOW_RATING";

    /** 推荐景点最低评分阈值（业务模型.md §4）。 */
    public static final double MIN_RATING = 3.5;

    /** 预算红线系数：总费用 ≤ 预算 ×1.15（业务模型.md §4）。 */
    private static final BigDecimal BUDGET_CEILING_FACTOR = new BigDecimal("1.15");

    /**
     * 复核行程方案的全局不变量。
     *
     * @param plan 待复核方案，不可为 null 且须含 request/budget
     * @return 复核结论（含 requiresHITL、违规清单与原 plan）
     * @throws InvalidRequestException 当 plan 或其 request/budget 为 null
     */
    public QualityGateResult review(TripPlan plan) {
        if (plan == null || plan.request() == null || plan.budget() == null) {
            throw new InvalidRequestException("QualityGate 复核入参不可为 null（plan/request/budget）");
        }
        List<QualityViolation> violations = new ArrayList<>();
        checkDays(plan, violations);
        checkUniqueAttractions(plan, violations);
        checkMeals(plan, violations);
        checkBudget(plan, violations);
        checkRatings(plan, violations);
        return new QualityGateResult(!violations.isEmpty(), violations, plan);
    }

    private void checkDays(TripPlan plan, List<QualityViolation> out) {
        int expected = plan.request().days();
        int actual = plan.days().size();
        if (expected != actual) {
            out.add(new QualityViolation(RULE_DAYS_MISMATCH,
                    "天数不一致: 需求 " + expected + " 天，实际 " + actual + " 天"));
        }
    }

    private void checkUniqueAttractions(TripPlan plan, List<QualityViolation> out) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (TripDay day : plan.days()) {
            for (Attraction attraction : day.attractions()) {
                if (!seen.add(attraction.attractionId())) {
                    duplicates.add(attraction.attractionId());
                }
            }
        }
        if (!duplicates.isEmpty()) {
            out.add(new QualityViolation(RULE_DUPLICATE_ATTRACTION, "景点重复: " + duplicates));
        }
    }

    private void checkMeals(TripPlan plan, List<QualityViolation> out) {
        for (TripDay day : plan.days()) {
            Set<MealType> types = EnumSet.noneOf(MealType.class);
            for (Meal meal : day.meals()) {
                types.add(meal.mealType());
            }
            if (!types.contains(MealType.LUNCH) || !types.contains(MealType.DINNER)) {
                out.add(new QualityViolation(RULE_MISSING_MEAL,
                        "第 " + day.dayIndex() + " 天缺少午餐或晚餐"));
            }
        }
    }

    private void checkBudget(TripPlan plan, List<QualityViolation> out) {
        BigDecimal ceiling = plan.budget().total().multiply(BUDGET_CEILING_FACTOR);
        if (plan.totalCost() != null && plan.totalCost().compareTo(ceiling) > 0) {
            out.add(new QualityViolation(RULE_BUDGET_OVERRUN,
                    "超预算红线: 总费用 " + plan.totalCost() + " > 预算×1.15 " + ceiling));
        }
    }

    private void checkRatings(TripPlan plan, List<QualityViolation> out) {
        for (TripDay day : plan.days()) {
            for (Attraction attraction : day.attractions()) {
                if (attraction.rating() < MIN_RATING) {
                    out.add(new QualityViolation(RULE_LOW_RATING,
                            "景点 " + attraction.attractionId() + " 评分 " + attraction.rating()
                                    + " < " + MIN_RATING));
                }
            }
        }
    }
}
