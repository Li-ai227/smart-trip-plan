package com.smart.trip.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 统一异常体系的契约测试（C-004 AC-4）。
 *
 * <p>校验业务异常基类 {@link BaseException} 与三个子类携带的错误码对齐
 * {@code .harness/wiki/接口协议.md} §1.1（{@code INVALID_REQUEST / RATE_LIMITED / SERVICE_UNAVAILABLE}），
 * 并验证 message / cause 的可观测性（编码规范 §3：异常携带上下文，禁止吞异常）。</p>
 */
class ExceptionHierarchyTest {

    @Nested
    @DisplayName("子类错误码对齐 接口协议 §1.1")
    class ErrorCodes {

        @Test
        void invalidRequestCarriesCode() {
            InvalidRequestException ex = new InvalidRequestException("days 必须 > 0");
            assertInstanceOf(BaseException.class, ex);
            assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
            assertEquals("INVALID_REQUEST", ex.getCode());
            assertEquals("days 必须 > 0", ex.getMessage());
        }

        @Test
        void rateLimitedCarriesCode() {
            RateLimitedException ex = new RateLimitedException("小红书限频命中");
            assertEquals(ErrorCode.RATE_LIMITED, ex.getErrorCode());
            assertEquals("RATE_LIMITED", ex.getCode());
        }

        @Test
        void downstreamUnavailableCarriesCode() {
            DownstreamUnavailableException ex = new DownstreamUnavailableException("route-agent 不可用");
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());
            assertEquals("SERVICE_UNAVAILABLE", ex.getCode());
        }
    }

    @Nested
    @DisplayName("可观测性：保留 cause，便于日志检索")
    class CausePropagation {

        @Test
        void preservesCause() {
            Throwable cause = new IllegalStateException("boom");
            DownstreamUnavailableException ex = new DownstreamUnavailableException("下游故障", cause);
            assertSame(cause, ex.getCause());
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());
        }

        @Test
        void invalidRequestPreservesCause() {
            Throwable cause = new NumberFormatException("budget");
            InvalidRequestException ex = new InvalidRequestException("参数解析失败", cause);
            assertSame(cause, ex.getCause());
            assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        }

        @Test
        void rateLimitedPreservesCause() {
            Throwable cause = new IllegalStateException("window full");
            RateLimitedException ex = new RateLimitedException("地图限频命中", cause);
            assertSame(cause, ex.getCause());
            assertEquals(ErrorCode.RATE_LIMITED, ex.getErrorCode());
        }
    }
}
