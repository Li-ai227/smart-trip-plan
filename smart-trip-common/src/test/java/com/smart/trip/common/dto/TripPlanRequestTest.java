package com.smart.trip.common.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link TripPlanRequest} 字段约束与显式自校验测试（C-004 AC-2 / 决策 D-2）。
 *
 * <p>对齐 {@code 数据模型.md} §2.1 与 {@code 接口协议.md} 错误码：合法请求 {@code validate()} 通过；
 * 非法请求抛 {@link InvalidRequestException}（码 {@code INVALID_REQUEST}）。构造永不抛错，
 * 范围校验只在 {@code validate()} 内（保证反序列化任意载荷可用、{@code preferences=null} 可构造）。</p>
 */
class TripPlanRequestTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    private static TripPlanRequest of(int days, BigDecimal budget, int headcount) {
        return new TripPlanRequest("云南", START, days, budget, headcount,
                TravelStyle.COUPLE, List.of("古镇", "美食"));
    }

    @Nested
    @DisplayName("Happy Path 与边界内取值")
    class HappyPath {

        @Test
        void validRequestPasses() {
            assertDoesNotThrow(() -> of(5, new BigDecimal("8000"), 2).validate());
        }

        @Test
        void daysLowerAndUpperBoundInclusive() {
            assertDoesNotThrow(() -> of(1, new BigDecimal("100"), 1).validate());
            assertDoesNotThrow(() -> of(30, new BigDecimal("100"), 1).validate());
        }
    }

    @Nested
    @DisplayName("边界：非法字段 → INVALID_REQUEST")
    class InvalidFields {

        @Test
        void daysZeroRejected() {
            InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                    () -> of(0, new BigDecimal("8000"), 2).validate());
            assertEquals("INVALID_REQUEST", ex.getCode());
        }

        @Test
        void daysOver30Rejected() {
            assertThrows(InvalidRequestException.class,
                    () -> of(31, new BigDecimal("8000"), 2).validate());
        }

        @Test
        void budgetZeroRejected() {
            assertThrows(InvalidRequestException.class,
                    () -> of(5, BigDecimal.ZERO, 2).validate());
        }

        @Test
        void budgetNullRejected() {
            assertThrows(InvalidRequestException.class,
                    () -> of(5, null, 2).validate());
        }

        @Test
        void negativeBudgetRejected() {
            assertThrows(InvalidRequestException.class,
                    () -> of(5, new BigDecimal("-1"), 2).validate());
        }

        @Test
        void headcountZeroRejected() {
            assertThrows(InvalidRequestException.class,
                    () -> of(5, new BigDecimal("8000"), 0).validate());
        }

        @Test
        void blankDestinationRejected() {
            TripPlanRequest r = new TripPlanRequest("   ", START, 5, new BigDecimal("8000"), 2,
                    TravelStyle.SOLO, List.of());
            assertThrows(InvalidRequestException.class, r::validate);
        }

        @Test
        void nullDestinationRejected() {
            TripPlanRequest r = new TripPlanRequest(null, START, 5, new BigDecimal("8000"), 2,
                    TravelStyle.SOLO, List.of());
            assertThrows(InvalidRequestException.class, r::validate);
        }

        @Test
        void nullStartDateRejected() {
            TripPlanRequest r = new TripPlanRequest("云南", null, 5, new BigDecimal("8000"), 2,
                    TravelStyle.SOLO, List.of());
            assertThrows(InvalidRequestException.class, r::validate);
        }
    }

    @Nested
    @DisplayName("边界：可空与不可变集合")
    class CollectionSafety {

        @Test
        void nullPreferencesConstructsToEmptyAndPasses() {
            TripPlanRequest r = new TripPlanRequest("云南", START, 5, new BigDecimal("8000"), 2,
                    TravelStyle.SOLO, null);
            assertNotNull(r.preferences());
            assertTrue(r.preferences().isEmpty());
            assertDoesNotThrow(r::validate);
        }

        @Test
        void preferencesAreImmutable() {
            TripPlanRequest r = of(5, new BigDecimal("8000"), 2);
            assertThrows(UnsupportedOperationException.class, () -> r.preferences().add("x"));
        }
    }

    @Nested
    @DisplayName("C-018 AC-1: planMode 可选字段，向后兼容")
    class PlanModeField {

        @Test
        void legacySevenArgConstructorDefaultsPlanModeFalse() {
            TripPlanRequest r = of(5, new BigDecimal("8000"), 2);
            assertEquals(false, r.planMode());
        }

        @Test
        void eightArgConstructorHonoursExplicitPlanMode() {
            TripPlanRequest r = new TripPlanRequest("云南", START, 5, new BigDecimal("8000"), 2,
                    TravelStyle.COUPLE, List.of("古镇"), true);
            assertEquals(true, r.planMode());
        }

        @Test
        void planModeDoesNotAffectValidation() {
            TripPlanRequest r = new TripPlanRequest("云南", START, 5, new BigDecimal("8000"), 2,
                    TravelStyle.COUPLE, List.of(), true);
            assertDoesNotThrow(r::validate);
        }
    }
}
