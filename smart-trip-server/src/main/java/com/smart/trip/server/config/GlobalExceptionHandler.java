package com.smart.trip.server.config;

import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.server.auth.filter.TraceIdFilter;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理（C-011 AC-8/AC-11）。
 *
 * <p>所有错误响应结构 {@code {code, message, traceId}}，对齐接口协议.md §5。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<ErrorCode, HttpStatus> STATUS_MAP = Map.ofEntries(
            Map.entry(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN),
            Map.entry(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.CONFLICT, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS),
            Map.entry(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(ErrorCode.PLAN_NOT_EXPORTABLE, HttpStatus.BAD_REQUEST));

    /** 处理业务异常，映射 ErrorCode 到 HTTP 状态码。 */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Map<String, String>> handleBaseException(BaseException ex) {
        HttpStatus status = STATUS_MAP.getOrDefault(ex.getErrorCode(), HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(status).body(errorBody(ex.getCode(), ex.getMessage()));
    }

    /** 兜底处理未预期异常，返回 500。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        LOG.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("INTERNAL_ERROR", "服务内部错误"));
    }

    private Map<String, String> errorBody(String code, String message) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return Map.of("code", code, "message", message, "traceId", traceId);
    }
}
