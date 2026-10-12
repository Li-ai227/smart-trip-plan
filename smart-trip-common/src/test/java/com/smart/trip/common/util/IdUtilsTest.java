package com.smart.trip.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.exception.InvalidRequestException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link IdUtils} planId 生成测试（C-004 AC-5 / 决策 D-5）。
 *
 * <p>格式对齐 {@code 数据模型.md} §2.2 与 {@code 接口协议.md} 示例 {@code P-yyyyMMdd-NNN}；
 * 纯函数签名（无隐藏静态/时间状态），序列越界（{@code <1} 或 {@code >999}）显式抛
 * {@link InvalidRequestException}，明确「NNN 进位」边界行为。</p>
 */
class IdUtilsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 1);

    @Nested
    @DisplayName("格式与补零")
    class Format {

        @Test
        void formatsWithLeadingZeros() {
            assertEquals("P-20260701-001", IdUtils.newPlanId(DATE, 1));
            assertEquals("P-20260701-042", IdUtils.newPlanId(DATE, 42));
            assertEquals("P-20260701-999", IdUtils.newPlanId(DATE, 999));
        }

        @Test
        void matchesContractRegex() {
            assertTrue(IdUtils.newPlanId(DATE, 7).matches("^P-\\d{8}-\\d{3}$"));
        }
    }

    @Nested
    @DisplayName("A2A 标识符：msgId / traceId（C-005 / 接口协议 §2 示例 M-.../T-...）")
    class A2aIds {

        @Test
        void msgIdHasContractPrefixAndIsUnique() {
            String a = IdUtils.newMsgId();
            String b = IdUtils.newMsgId();
            assertTrue(a.startsWith("M-"), a);
            assertNotEquals(a, b);
        }

        @Test
        void traceIdHasContractPrefixAndIsUnique() {
            String a = IdUtils.newTraceId();
            String b = IdUtils.newTraceId();
            assertTrue(a.startsWith("T-"), a);
            assertNotEquals(a, b);
        }
    }

    @Nested
    @DisplayName("边界：序列越界与空日期 fail-fast")
    class Boundaries {

        @Test
        void sequenceOverflowRejected() {
            assertThrows(InvalidRequestException.class, () -> IdUtils.newPlanId(DATE, 1000));
        }

        @Test
        void sequenceZeroRejected() {
            assertThrows(InvalidRequestException.class, () -> IdUtils.newPlanId(DATE, 0));
        }

        @Test
        void negativeSequenceRejected() {
            assertThrows(InvalidRequestException.class, () -> IdUtils.newPlanId(DATE, -1));
        }

        @Test
        void nullDateRejected() {
            assertThrows(InvalidRequestException.class, () -> IdUtils.newPlanId(null, 1));
        }
    }
}
