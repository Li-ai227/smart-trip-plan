package com.smart.trip.server.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 链路追踪 ID 过滤器（C-011 AC-11 / 接口协议.md §5）。
 *
 * <p>每个请求生成或透传 {@code X-Trace-Id} 响应头，同时注入 MDC {@code traceId}，
 * 供日志与 {@link com.smart.trip.server.config.GlobalExceptionHandler} 引用。</p>
 */
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 键，与 GlobalExceptionHandler 共享。 */
    public static final String MDC_TRACE_ID = "traceId";

    /** 响应头名。 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** 请求头：上游透传的 traceId（OpenTelemetry B3）。 */
    private static final String HEADER_B3 = "X-B3-TraceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER_B3);
        if (traceId == null || traceId.isBlank()) {
            traceId = "T-" + java.util.UUID.randomUUID();
        }
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }
}
