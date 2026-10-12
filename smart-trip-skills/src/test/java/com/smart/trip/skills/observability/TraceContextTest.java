package com.smart.trip.skills.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.Msg;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TraceContext} 单测：可观测字段 {@code traceId/planId/agentId/taskType/msgId/fallback} 贯穿（C-005 AC-7）。
 */
class TraceContextTest {

    @Test
    @DisplayName("asMap 暴露全部 6 个可观测字段（接口协议 §6 埋点位）")
    void asMapCarriesAllFields() {
        TraceContext ctx = new TraceContext("T-1", "P-1", "agent://route-planner",
                "ROUTE_PLANNING", "M-1", true);
        Map<String, String> map = ctx.asMap();

        assertEquals("T-1", map.get(TraceContext.FIELD_TRACE_ID));
        assertEquals("P-1", map.get(TraceContext.FIELD_PLAN_ID));
        assertEquals("agent://route-planner", map.get(TraceContext.FIELD_AGENT_ID));
        assertEquals("ROUTE_PLANNING", map.get(TraceContext.FIELD_TASK_TYPE));
        assertEquals("M-1", map.get(TraceContext.FIELD_MSG_ID));
        assertEquals("true", map.get(TraceContext.FIELD_FALLBACK));
    }

    @Test
    @DisplayName("从 Msg 派生贯穿字段：透传 traceId/msgId，提取 taskType/fallback")
    void derivesFromMsg() {
        Msg assign = Msg.taskAssign("agent://supervisor", "agent://route-planner", "T-9",
                Map.of("taskType", "ROUTE_PLANNING"));
        TraceContext ctx = TraceContext.from(assign, "P-9");

        assertEquals("T-9", ctx.traceId());
        assertEquals("P-9", ctx.planId());
        assertEquals(assign.msgId(), ctx.msgId());
        assertEquals("ROUTE_PLANNING", ctx.taskType());
        assertTrue(ctx.asMap().containsKey(TraceContext.FIELD_FALLBACK));
    }

    @Test
    @DisplayName("fallback 结果消息派生 fallback=true")
    void derivesFallbackFromResult() {
        Msg result = Msg.taskResult("agent://route-planner", "agent://supervisor", "T-9",
                "offline", true);
        TraceContext ctx = TraceContext.from(result, "P-9");
        assertEquals("true", ctx.asMap().get(TraceContext.FIELD_FALLBACK));
    }
}
