package com.smart.trip.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 枚举取值契约测试（C-004 AC-3）。
 *
 * <p>逐一锁定 {@code .harness/wiki/数据模型.md} §3 定义的全部枚举取值数量与代表值，
 * 防止后续静默增删取值导致与数据模型/接口协议漂移（回归保护）。</p>
 */
class EnumContractTest {

    @Test
    @DisplayName("AgentStatus = IDLE/THINKING/ACTING/OBSERVING/DONE")
    void agentStatus() {
        assertEquals(5, AgentStatus.values().length);
        assertNotNull(AgentStatus.valueOf("OBSERVING"));
    }

    @Test
    @DisplayName("TravelStyle = FAMILY/COUPLE/SOLO/TEAM")
    void travelStyle() {
        assertEquals(4, TravelStyle.values().length);
        assertNotNull(TravelStyle.valueOf("COUPLE"));
    }

    @Test
    @DisplayName("BudgetLevel = LOW/MEDIUM/HIGH")
    void budgetLevel() {
        assertEquals(3, BudgetLevel.values().length);
        assertNotNull(BudgetLevel.valueOf("MEDIUM"));
    }

    @Test
    @DisplayName("PlanStatus = ANALYZING/PLANNING/PLAN_REVIEW/ASSEMBLING/BUDGETING/REVIEW/"
            + "CONFIRMED/FAILED（C-018 新增 PLAN_REVIEW）")
    void planStatus() {
        assertEquals(8, PlanStatus.values().length);
        assertNotNull(PlanStatus.valueOf("CONFIRMED"));
        assertNotNull(PlanStatus.valueOf("PLAN_REVIEW"));
    }

    @Test
    @DisplayName("MealType = BREAKFAST/LUNCH/DINNER")
    void mealType() {
        assertEquals(3, MealType.values().length);
        assertNotNull(MealType.valueOf("DINNER"));
    }

    @Test
    @DisplayName("SentimentType = POSITIVE/NEUTRAL/NEGATIVE")
    void sentimentType() {
        assertEquals(3, SentimentType.values().length);
        assertNotNull(SentimentType.valueOf("NEGATIVE"));
    }
}
