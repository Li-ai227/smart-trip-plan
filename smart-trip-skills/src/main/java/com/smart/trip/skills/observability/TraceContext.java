package com.smart.trip.skills.observability;

import com.smart.trip.common.a2a.Msg;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可观测贯穿字段上下文（C-005 AC-7 / 接口协议.md §5/§6）。
 *
 * <p>承载需贯穿日志与指标的关键字段 {@code traceId/planId/agentId/taskType/msgId/fallback}，
 * {@link #asMap()} 便于注入 MDC 或日志结构化字段，保证全链路可追踪（§5 traceId 全链路）。</p>
 *
 * @param traceId  链路追踪 ID（{@code T-...}），下游透传
 * @param planId   方案 ID（{@code P-...}）
 * @param agentId  Agent 标识（{@code agent://<id>}）
 * @param taskType 任务类型（如 {@code ROUTE_PLANNING}），可空
 * @param msgId    消息 ID（{@code M-...}）
 * @param fallback 是否为降级产出
 * @deprecated 自 C-005-B 起，迁移到 {@code OtelTracingMiddleware}（框架内置）+
 *     {@link TripPlanSpanEnricher}（D-12）。6 字段转为 OTel span attributes，不再需要 MDC 手动贯穿。
 */
@Deprecated(since = "C-005-B", forRemoval = true)
public record TraceContext(
        String traceId, String planId, String agentId, String taskType, String msgId, boolean fallback) {

    /** 字段名：链路追踪 ID。 */
    public static final String FIELD_TRACE_ID = "traceId";

    /** 字段名：方案 ID。 */
    public static final String FIELD_PLAN_ID = "planId";

    /** 字段名：Agent 标识。 */
    public static final String FIELD_AGENT_ID = "agentId";

    /** 字段名：任务类型。 */
    public static final String FIELD_TASK_TYPE = "taskType";

    /** 字段名：消息 ID。 */
    public static final String FIELD_MSG_ID = "msgId";

    /** 字段名：降级标记。 */
    public static final String FIELD_FALLBACK = "fallback";

    /**
     * 从 A2A 消息派生贯穿上下文：透传 {@code traceId/msgId}，提取 {@code taskType/fallback}。
     *
     * @param msg    A2A 消息（{@code TASK_ASSIGN}/{@code TASK_RESULT} 等）
     * @param planId 当前方案 ID
     * @return 贯穿上下文
     */
    public static TraceContext from(Msg msg, String planId) {
        Object taskType = msg.payload().get(Msg.KEY_TASK_TYPE);
        return new TraceContext(
                msg.traceId(), planId, msg.from(),
                taskType == null ? null : taskType.toString(),
                msg.msgId(), msg.isFallback());
    }

    /**
     * 导出为有序键值对，供 MDC / 日志结构化字段使用。
     *
     * @return 含全部 6 个可观测字段的有序 Map
     */
    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(FIELD_TRACE_ID, traceId);
        map.put(FIELD_PLAN_ID, planId);
        map.put(FIELD_AGENT_ID, agentId);
        map.put(FIELD_TASK_TYPE, taskType);
        map.put(FIELD_MSG_ID, msgId);
        map.put(FIELD_FALLBACK, String.valueOf(fallback));
        return map;
    }
}
