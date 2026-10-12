package com.smart.trip.skills.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.model.ChatUsage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * {@link TripPlanSpanEnricher} 单测（C-005 增量 B AC-12 / TDD Red→Green）。
 *
 * <p>验证 {@code onAgent} 钩子注入 {@link TraceContext} 6 字段为 OTel span attributes，
 * 以及 no-op 路径（无有效 span）不抛异常。</p>
 */
class TripPlanSpanEnricherTest {

    @Test
    @DisplayName("onAgent 将 TraceContext 6 字段写入当前 span attributes")
    void onAgent_writesTraceContextAttributes() {
        TraceContext ctx = new TraceContext("T-001", "P-001", "agent://xhs-analyst",
                "XHS_ANALYSIS", "M-001", true);
        TripPlanSpanEnricher enricher = new TripPlanSpanEnricher(ctx);

        RecordingSpan span = new RecordingSpan();
        AgentEvent event = new ModelCallEndEvent("r1", new ChatUsage(1, 1, 0.1));
        AgentInput input = new AgentInput(List.of());

        Function<AgentInput, Flux<AgentEvent>> next = in -> Flux.just(event);

        List<AgentEvent> result = enricher.onAgentWithSpan(span, null, input, next)
                .collectList().block();

        assertEquals(1, result.size());
        assertSame(event, result.get(0));
        assertEquals("T-001", span.attributes.get("trip.traceId"));
        assertEquals("P-001", span.attributes.get("trip.planId"));
        assertEquals("agent://xhs-analyst", span.attributes.get("trip.agentId"));
        assertEquals("XHS_ANALYSIS", span.attributes.get("trip.taskType"));
        assertEquals("M-001", span.attributes.get("trip.msgId"));
        assertEquals("true", span.attributes.get("trip.fallback"));
    }

    @Test
    @DisplayName("invalid span（no-op）时不写 attributes、不抛异常、事件透传")
    void invalidSpan_noAttributes_noException() {
        TraceContext ctx = new TraceContext("T-002", "P-002", "agent://route",
                "ROUTE_PLANNING", "M-002", false);
        TripPlanSpanEnricher enricher = new TripPlanSpanEnricher(ctx);

        AgentEvent event = new ModelCallEndEvent("r2", new ChatUsage(1, 1, 0.1));
        AgentInput input = new AgentInput(List.of());
        Function<AgentInput, Flux<AgentEvent>> next = in -> Flux.just(event);

        List<AgentEvent> result = enricher.onAgentWithSpan(Span.getInvalid(), null, input, next)
                .collectList().block();

        assertEquals(1, result.size());
        assertSame(event, result.get(0));
    }

    @Test
    @DisplayName("null TraceContext 时 onAgent 直接 delegate，不写 attributes")
    void nullContext_delegatesToNext() {
        TripPlanSpanEnricher enricher = new TripPlanSpanEnricher(null);
        AgentEvent event = new ModelCallEndEvent("r3", new ChatUsage(1, 1, 0.1));
        AgentInput input = new AgentInput(List.of());

        List<AgentEvent> result = enricher.onAgent(null, null, input, in -> Flux.just(event))
                .collectList().block();

        assertEquals(1, result.size());
        assertSame(event, result.get(0));
    }

    /**
     * 简易记录式 Span：记录 setAttribute 调用而不需要 OTel SDK。
     */
    private static class RecordingSpan implements Span {

        final Map<String, String> attributes = new HashMap<>();

        @Override
        public <T> Span setAttribute(AttributeKey<T> key, T value) {
            attributes.put(key.getKey(), String.valueOf(value));
            return this;
        }

        @Override
        public Span addEvent(String name) {
            return this;
        }

        @Override
        public Span addEvent(String name, long timestamp, java.util.concurrent.TimeUnit unit) {
            return this;
        }

        @Override
        public Span addEvent(String name, io.opentelemetry.api.common.Attributes attrs) {
            return this;
        }

        @Override
        public Span addEvent(String name, io.opentelemetry.api.common.Attributes attrs,
                long timestamp, java.util.concurrent.TimeUnit unit) {
            return this;
        }

        @Override
        public Span setStatus(io.opentelemetry.api.trace.StatusCode statusCode) {
            return this;
        }

        @Override
        public Span setStatus(io.opentelemetry.api.trace.StatusCode statusCode, String description) {
            return this;
        }

        @Override
        public Span recordException(Throwable exception) {
            return this;
        }

        @Override
        public Span recordException(Throwable exception, io.opentelemetry.api.common.Attributes attrs) {
            return this;
        }

        @Override
        public Span updateName(String name) {
            return this;
        }

        @Override
        public void end() {
        }

        @Override
        public void end(long timestamp, java.util.concurrent.TimeUnit unit) {
        }

        @Override
        public io.opentelemetry.api.trace.SpanContext getSpanContext() {
            return io.opentelemetry.api.trace.SpanContext.getInvalid();
        }

        @Override
        public boolean isRecording() {
            return true;
        }
    }
}
