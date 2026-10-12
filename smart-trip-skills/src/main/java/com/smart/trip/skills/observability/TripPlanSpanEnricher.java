package com.smart.trip.skills.observability;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * 自定义 OTel span 属性注入中间件（C-005 增量 B AC-12 / 决策 D-12）。
 *
 * <p>在 {@code onAgent} 钩子中将 {@link TraceContext} 6 字段（{@code traceId/planId/agentId/
 * taskType/msgId/fallback}）注入当前 OTel span attributes，保留全链路可追踪语义。
 * 当 {@code Span.current()} 为 {@code Span.getInvalid()}（无 OTel SDK 配置）时不写 attributes、
 * 不抛异常（框架保证 no-op 路径零额外开销）。</p>
 */
public final class TripPlanSpanEnricher implements MiddlewareBase {

    private static final AttributeKey<String> ATTR_TRACE_ID = AttributeKey.stringKey("trip.traceId");
    private static final AttributeKey<String> ATTR_PLAN_ID = AttributeKey.stringKey("trip.planId");
    private static final AttributeKey<String> ATTR_AGENT_ID = AttributeKey.stringKey("trip.agentId");
    private static final AttributeKey<String> ATTR_TASK_TYPE = AttributeKey.stringKey("trip.taskType");
    private static final AttributeKey<String> ATTR_MSG_ID = AttributeKey.stringKey("trip.msgId");
    private static final AttributeKey<String> ATTR_FALLBACK = AttributeKey.stringKey("trip.fallback");

    private final TraceContext context;

    /**
     * @param context 可观测贯穿上下文，可为 null（null 时直接 delegate 不注入属性）
     */
    public TripPlanSpanEnricher(TraceContext context) {
        this.context = context;
    }

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext runtimeContext,
            AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        return onAgentWithSpan(Span.current(), agent, input, next);
    }

    /**
     * 内部方法：接受显式 span（便于测试注入 RecordingSpan 而非依赖 Span.current()）。
     */
    Flux<AgentEvent> onAgentWithSpan(Span span, Agent agent, AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        if (context != null && span.isRecording()) {
            span.setAttribute(ATTR_TRACE_ID, context.traceId());
            span.setAttribute(ATTR_PLAN_ID, context.planId());
            span.setAttribute(ATTR_AGENT_ID, context.agentId());
            span.setAttribute(ATTR_TASK_TYPE, context.taskType());
            span.setAttribute(ATTR_MSG_ID, context.msgId());
            span.setAttribute(ATTR_FALLBACK, String.valueOf(context.fallback()));
        }
        return next.apply(input);
    }
}
