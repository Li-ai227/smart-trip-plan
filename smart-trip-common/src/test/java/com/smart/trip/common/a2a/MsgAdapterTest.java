package com.smart.trip.common.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.message.MsgRole;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * MsgAdapter 双向桥接测试（C-005 增量 B AC-8 / TDD Red→Green）。
 *
 * <p>验证自定义 {@link Msg}（任务信封）↔ 框架 {@code io.agentscope.core.message.Msg}（LLM 对话消息）
 * 的双向无损转换：5 种 {@link MessageType} 往返中 {@code msgId/from/to/type/traceId/timeoutMs/payload}
 * 全部保留。</p>
 */
class MsgAdapterTest {

    // ── TASK_ASSIGN 往返 ──

    @Test
    void taskAssign_roundTrip_preservesAllFields() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(Msg.KEY_TASK_TYPE, "ROUTE_PLANNING");
        payload.put("destination", "云南");
        Msg original = Msg.taskAssign("supervisor", "route-agent", "T-abc", payload, 15_000L);

        Optional<io.agentscope.core.message.Msg> fw = MsgAdapter.toFrameworkMsg(original);
        assertTrue(fw.isPresent(), "TASK_ASSIGN should produce framework Msg");
        assertEquals(MsgRole.USER, fw.get().getRole(), "TASK_ASSIGN maps to USER role");

        Optional<Msg> rebuilt = MsgAdapter.fromFrameworkMsg(fw.get());
        assertTrue(rebuilt.isPresent(), "round-trip should produce custom Msg");

        assertMsgFieldsEqual(original, rebuilt.get());
    }

    // ── TASK_RESULT 往返 ──

    @Test
    void taskResult_roundTrip_preservesAllFields() {
        Msg original = Msg.taskResult("route-agent", "supervisor", "T-def",
                Map.of("routes", "R1"), false);

        Optional<io.agentscope.core.message.Msg> fw = MsgAdapter.toFrameworkMsg(original);
        assertTrue(fw.isPresent());
        assertEquals(MsgRole.ASSISTANT, fw.get().getRole(), "TASK_RESULT maps to ASSISTANT role");

        Optional<Msg> rebuilt = MsgAdapter.fromFrameworkMsg(fw.get());
        assertTrue(rebuilt.isPresent());
        assertMsgFieldsEqual(original, rebuilt.get());
    }

    @Test
    void taskResult_fallbackTrue_preservedInRoundTrip() {
        Msg original = Msg.taskResult("route-agent", "supervisor", "T-fb", null, true);

        io.agentscope.core.message.Msg fw = MsgAdapter.toFrameworkMsg(original).orElseThrow();
        Msg rebuilt = MsgAdapter.fromFrameworkMsg(fw).orElseThrow();

        assertTrue(rebuilt.isFallback(), "fallback=true must survive round-trip");
        assertMsgFieldsEqual(original, rebuilt);
    }

    // ── TASK_ERROR 往返 ──

    @Test
    void taskError_roundTrip_preservesAllFields() {
        Msg original = Msg.taskError("xhs-analyst", "supervisor", "T-err",
                "AGENT_FAILURE", "NullPointerException");

        Optional<io.agentscope.core.message.Msg> fw = MsgAdapter.toFrameworkMsg(original);
        assertTrue(fw.isPresent());
        assertEquals(MsgRole.ASSISTANT, fw.get().getRole(), "TASK_ERROR maps to ASSISTANT role");

        Optional<Msg> rebuilt = MsgAdapter.fromFrameworkMsg(fw.get());
        assertTrue(rebuilt.isPresent());
        assertMsgFieldsEqual(original, rebuilt.get());
    }

    // ── HEARTBEAT 往返 ──

    @Test
    void heartbeat_roundTrip_preservesAllFields() {
        Msg original = new Msg("M-hb1", "route-agent", null,
                MessageType.HEARTBEAT, Map.of(), 0L, "T-hb");

        Optional<io.agentscope.core.message.Msg> fw = MsgAdapter.toFrameworkMsg(original);
        assertTrue(fw.isPresent());
        assertEquals(MsgRole.SYSTEM, fw.get().getRole(), "HEARTBEAT maps to SYSTEM role");

        Optional<Msg> rebuilt = MsgAdapter.fromFrameworkMsg(fw.get());
        assertTrue(rebuilt.isPresent());
        assertMsgFieldsEqual(original, rebuilt.get());
    }

    // ── BROADCAST 往返 ──

    @Test
    void broadcast_roundTrip_preservesAllFields() {
        Msg original = new Msg("M-bc1", "supervisor", null,
                MessageType.BROADCAST, Map.of("event", "shutdown"), 0L, "T-bc");

        Optional<io.agentscope.core.message.Msg> fw = MsgAdapter.toFrameworkMsg(original);
        assertTrue(fw.isPresent());
        assertEquals(MsgRole.SYSTEM, fw.get().getRole(), "BROADCAST maps to SYSTEM role");

        Optional<Msg> rebuilt = MsgAdapter.fromFrameworkMsg(fw.get());
        assertTrue(rebuilt.isPresent());
        assertMsgFieldsEqual(original, rebuilt.get());
    }

    // ── 边界：无 metadata 的框架消息 → Optional.empty() ──

    @Test
    void fromFrameworkMsg_withoutMetadata_returnsEmpty() {
        io.agentscope.core.message.Msg bare = io.agentscope.core.message.Msg.builder()
                .role(MsgRole.USER)
                .textContent("hello")
                .build();

        Optional<Msg> result = MsgAdapter.fromFrameworkMsg(bare);
        assertFalse(result.isPresent(), "no trip metadata → empty");
    }

    // ── 边界：null 输入 ──

    @Test
    void toFrameworkMsg_null_returnsEmpty() {
        assertFalse(MsgAdapter.toFrameworkMsg(null).isPresent());
    }

    @Test
    void fromFrameworkMsg_null_returnsEmpty() {
        assertFalse(MsgAdapter.fromFrameworkMsg(null).isPresent());
    }

    // ── 边界：null payload ──

    @Test
    void taskResult_nullResult_roundTripPreservesNullPayloadValue() {
        Msg original = Msg.taskResult("a", "b", "T-np", null, false);

        io.agentscope.core.message.Msg fw = MsgAdapter.toFrameworkMsg(original).orElseThrow();
        Msg rebuilt = MsgAdapter.fromFrameworkMsg(fw).orElseThrow();

        assertMsgFieldsEqual(original, rebuilt);
    }

    // ── 边界：from 为 null ──

    @Test
    void toFrameworkMsg_nullFrom_metadataOmitsFromKey() {
        Msg original = new Msg("M-nf", null, "supervisor",
                MessageType.HEARTBEAT, Map.of(), 0L, "T-nf");

        io.agentscope.core.message.Msg fw = MsgAdapter.toFrameworkMsg(original).orElseThrow();
        assertFalse(fw.getMetadata().containsKey(MsgAdapter.META_FROM),
                "null from should be omitted from metadata");

        Msg rebuilt = MsgAdapter.fromFrameworkMsg(fw).orElseThrow();
        assertMsgFieldsEqual(original, rebuilt);
    }

    // ── 边界：traceId 为 null ──

    @Test
    void toFrameworkMsg_nullTraceId_metadataOmitsTraceIdKey() {
        Msg original = new Msg("M-nt", "agent-a", "agent-b",
                MessageType.TASK_ASSIGN, Map.of("k", "v"), 5000L, null);

        io.agentscope.core.message.Msg fw = MsgAdapter.toFrameworkMsg(original).orElseThrow();
        assertFalse(fw.getMetadata().containsKey(MsgAdapter.META_TRACE_ID),
                "null traceId should be omitted from metadata");

        Msg rebuilt = MsgAdapter.fromFrameworkMsg(fw).orElseThrow();
        assertMsgFieldsEqual(original, rebuilt);
    }

    // ── 边界：fromFrameworkMsg metadata 缺失 META_TIMEOUT_MS（getLong val==null→0L）──

    @Test
    void fromFrameworkMsg_missingTimeoutMs_defaultsToZero() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MsgAdapter.META_MSG_ID, "M-mt");
        metadata.put(MsgAdapter.META_FROM, "a");
        metadata.put(MsgAdapter.META_TO, "b");
        metadata.put(MsgAdapter.META_TYPE, "TASK_ASSIGN");
        metadata.put(MsgAdapter.META_PAYLOAD, "{}");

        io.agentscope.core.message.Msg fw = io.agentscope.core.message.Msg.builder()
                .role(MsgRole.USER)
                .textContent("{}")
                .metadata(metadata)
                .build();

        Msg rebuilt = MsgAdapter.fromFrameworkMsg(fw).orElseThrow();
        assertEquals(0L, rebuilt.timeoutMs(), "missing timeoutMs should default to 0");
    }

    // ── 边界：getLong 处理 String 类型的 timeoutMs ──

    @Test
    void fromFrameworkMsg_stringTimeoutMs_parsedCorrectly() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MsgAdapter.META_MSG_ID, "M-st");
        metadata.put(MsgAdapter.META_FROM, "a");
        metadata.put(MsgAdapter.META_TO, "b");
        metadata.put(MsgAdapter.META_TYPE, "TASK_ASSIGN");
        metadata.put(MsgAdapter.META_TIMEOUT_MS, "12345");
        metadata.put(MsgAdapter.META_PAYLOAD, "{}");

        io.agentscope.core.message.Msg fw = io.agentscope.core.message.Msg.builder()
                .role(MsgRole.USER)
                .textContent("{}")
                .metadata(metadata)
                .build();

        Msg rebuilt = MsgAdapter.fromFrameworkMsg(fw).orElseThrow();
        assertEquals(12345L, rebuilt.timeoutMs(), "String timeoutMs should be parsed as long");
    }

    // ── 边界：getLong 处理不可解析的 String ──

    @Test
    void fromFrameworkMsg_nonNumericTimeoutMs_defaultsToZero() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MsgAdapter.META_MSG_ID, "M-nn");
        metadata.put(MsgAdapter.META_FROM, "a");
        metadata.put(MsgAdapter.META_TO, "b");
        metadata.put(MsgAdapter.META_TYPE, "TASK_ASSIGN");
        metadata.put(MsgAdapter.META_TIMEOUT_MS, "not-a-number");
        metadata.put(MsgAdapter.META_PAYLOAD, "{}");

        io.agentscope.core.message.Msg fw = io.agentscope.core.message.Msg.builder()
                .role(MsgRole.USER)
                .textContent("{}")
                .metadata(metadata)
                .build();

        Msg rebuilt = MsgAdapter.fromFrameworkMsg(fw).orElseThrow();
        assertEquals(0L, rebuilt.timeoutMs(), "unparseable String timeoutMs should default to 0");
    }

    // ── 边界：deserializePayload 处理 blank JSON ──

    @Test
    void fromFrameworkMsg_blankPayload_returnsEmptyMap() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MsgAdapter.META_MSG_ID, "M-bp");
        metadata.put(MsgAdapter.META_FROM, "a");
        metadata.put(MsgAdapter.META_TO, "b");
        metadata.put(MsgAdapter.META_TYPE, "TASK_ASSIGN");
        metadata.put(MsgAdapter.META_TIMEOUT_MS, 0L);
        metadata.put(MsgAdapter.META_PAYLOAD, "   ");

        io.agentscope.core.message.Msg fw = io.agentscope.core.message.Msg.builder()
                .role(MsgRole.USER)
                .textContent("{}")
                .metadata(metadata)
                .build();

        Msg rebuilt = MsgAdapter.fromFrameworkMsg(fw).orElseThrow();
        assertNotNull(rebuilt.payload());
        assertTrue(rebuilt.payload().isEmpty(), "blank payload JSON should yield empty map");
    }

    // ── 边界：deserializePayload 处理 malformed JSON ──

    @Test
    void fromFrameworkMsg_malformedPayloadJson_returnsEmptyMap() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MsgAdapter.META_MSG_ID, "M-mj");
        metadata.put(MsgAdapter.META_FROM, "a");
        metadata.put(MsgAdapter.META_TO, "b");
        metadata.put(MsgAdapter.META_TYPE, "TASK_ASSIGN");
        metadata.put(MsgAdapter.META_TIMEOUT_MS, 0L);
        metadata.put(MsgAdapter.META_PAYLOAD, "{invalid-json!!}");

        io.agentscope.core.message.Msg fw = io.agentscope.core.message.Msg.builder()
                .role(MsgRole.USER)
                .textContent("{}")
                .metadata(metadata)
                .build();

        Msg rebuilt = MsgAdapter.fromFrameworkMsg(fw).orElseThrow();
        assertNotNull(rebuilt.payload());
        assertTrue(rebuilt.payload().isEmpty(), "malformed JSON should yield empty map (graceful fallback)");
    }

    // ── 边界：fromFrameworkMsg metadata 存在但缺少 META_TYPE ──

    @Test
    void fromFrameworkMsg_metadataWithoutType_returnsEmpty() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MsgAdapter.META_MSG_ID, "M-nt2");
        metadata.put(MsgAdapter.META_FROM, "a");

        io.agentscope.core.message.Msg fw = io.agentscope.core.message.Msg.builder()
                .role(MsgRole.USER)
                .textContent("hello")
                .metadata(metadata)
                .build();

        assertFalse(MsgAdapter.fromFrameworkMsg(fw).isPresent(),
                "metadata without trip.type should return empty");
    }

    /**
     * 断言两个自定义 Msg 的 7 个关键字段全等（msgId 因 round-trip 保留在 metadata 中，也要比对）。
     */
    private void assertMsgFieldsEqual(Msg expected, Msg actual) {
        assertEquals(expected.msgId(), actual.msgId(), "msgId");
        assertEquals(expected.from(), actual.from(), "from");
        assertEquals(expected.to(), actual.to(), "to");
        assertEquals(expected.type(), actual.type(), "type");
        assertEquals(expected.traceId(), actual.traceId(), "traceId");
        assertEquals(expected.timeoutMs(), actual.timeoutMs(), "timeoutMs");
        assertEquals(expected.payload(), actual.payload(), "payload");
    }
}
