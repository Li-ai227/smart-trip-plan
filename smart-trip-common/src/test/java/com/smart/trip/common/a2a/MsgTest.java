package com.smart.trip.common.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.util.IdUtils;
import com.smart.trip.common.util.JsonUtils;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A2A 不可变 {@link Msg} 信封测试（C-005 Session 1 / AC-1、AC-5 消息侧）。
 *
 * <p>对齐 {@code 接口协议.md} §2 测试关注点：{@code TASK_ASSIGN} 必含 {@code traceId/timeoutMs/payload}；
 * {@code TASK_RESULT} 降级场景必须显式 {@code fallback=true}；子 Agent 失败必须返回可识别错误
 * （{@code TASK_ERROR}）而非静默超时。决策见 change.md D-1..D-6。</p>
 */
class MsgTest {

    private static final String FROM = "supervisor";
    private static final String TO = "route-agent";
    private static final String TRACE = "T-trace-001";

    private static Map<String, Object> assignPayload() {
        Map<String, Object> p = new HashMap<>();
        p.put("taskType", "ROUTE_PLANNING");
        p.put("destination", "云南");
        return p;
    }

    @Nested
    @DisplayName("消息类型枚举（接口协议 §2.2）")
    class Types {

        @Test
        void hasExactlyFiveContractTypes() {
            assertEquals(5, MessageType.values().length);
            // 逐一存在，缺一即编译/枚举失败
            assertEquals(MessageType.TASK_ASSIGN, MessageType.valueOf("TASK_ASSIGN"));
            assertEquals(MessageType.TASK_RESULT, MessageType.valueOf("TASK_RESULT"));
            assertEquals(MessageType.TASK_ERROR, MessageType.valueOf("TASK_ERROR"));
            assertEquals(MessageType.HEARTBEAT, MessageType.valueOf("HEARTBEAT"));
            assertEquals(MessageType.BROADCAST, MessageType.valueOf("BROADCAST"));
        }
    }

    @Nested
    @DisplayName("TASK_ASSIGN：契约字段与默认超时")
    class TaskAssign {

        @Test
        void buildsWithDefaultTimeout() {
            Msg msg = Msg.taskAssign(FROM, TO, TRACE, assignPayload());
            assertEquals(MessageType.TASK_ASSIGN, msg.type());
            assertEquals(FROM, msg.from());
            assertEquals(TO, msg.to());
            assertEquals(TRACE, msg.traceId());
            assertEquals(Msg.DEFAULT_TIMEOUT_MS, msg.timeoutMs());
            assertEquals(30_000L, msg.timeoutMs());
            assertTrue(msg.msgId().startsWith("M-"));
            assertEquals("ROUTE_PLANNING", msg.payload().get("taskType"));
        }

        @Test
        void honoursExplicitTimeout() {
            Msg msg = Msg.taskAssign(FROM, TO, TRACE, assignPayload(), 15_000L);
            assertEquals(15_000L, msg.timeoutMs());
        }

        @Test
        void rejectsMissingTraceId() {
            assertThrows(InvalidRequestException.class,
                    () -> Msg.taskAssign(FROM, TO, null, assignPayload()));
            assertThrows(InvalidRequestException.class,
                    () -> Msg.taskAssign(FROM, TO, "  ", assignPayload()));
        }

        @Test
        void rejectsEmptyPayload() {
            assertThrows(InvalidRequestException.class,
                    () -> Msg.taskAssign(FROM, TO, TRACE, null));
            assertThrows(InvalidRequestException.class,
                    () -> Msg.taskAssign(FROM, TO, TRACE, Map.of()));
        }

        @Test
        void rejectsNonPositiveTimeout() {
            assertThrows(InvalidRequestException.class,
                    () -> Msg.taskAssign(FROM, TO, TRACE, assignPayload(), 0L));
            assertThrows(InvalidRequestException.class,
                    () -> Msg.taskAssign(FROM, TO, TRACE, assignPayload(), -1L));
        }

        @Test
        void rejectsBlankParticipants() {
            assertThrows(InvalidRequestException.class,
                    () -> Msg.taskAssign("", TO, TRACE, assignPayload()));
            assertThrows(InvalidRequestException.class,
                    () -> Msg.taskAssign(FROM, null, TRACE, assignPayload()));
        }
    }

    @Nested
    @DisplayName("TASK_RESULT：降级标记必须显式（接口协议 §2.2/§2.3）")
    class TaskResult {

        @Test
        void okResultIsNotFallback() {
            Msg msg = Msg.taskResult(TO, FROM, TRACE, "route-ok", false);
            assertEquals(MessageType.TASK_RESULT, msg.type());
            assertEquals("OK", msg.payload().get("status"));
            assertEquals("route-ok", msg.payload().get("result"));
            assertFalse(msg.isFallback());
            assertEquals(TRACE, msg.traceId());
        }

        @Test
        void fallbackResultIsExplicitlyMarked() {
            Msg msg = Msg.taskResult(TO, FROM, TRACE, "offline-estimate", true);
            assertTrue(msg.isFallback());
            assertEquals(Boolean.TRUE, msg.payload().get("fallback"));
        }

        @Test
        @DisplayName("降级：result 可为 null 仍显式带 fallback（payload 允许 null 值，D-6）")
        void nullResultWithFallbackIsAllowed() {
            Msg msg = Msg.taskResult(TO, FROM, TRACE, null, true);
            assertTrue(msg.isFallback());
            assertEquals("OK", msg.payload().get("status"));
            // result 键存在但值为 null（区别于「无 result 键」），保证降级语义可被下游识别
            assertTrue(msg.payload().containsKey("result"));
            assertNull(msg.payload().get("result"));
        }
    }

    @Nested
    @DisplayName("TASK_ERROR：可识别错误，禁止静默超时（接口协议 §2 测试关注点）")
    class TaskError {

        @Test
        void carriesIdentifiableError() {
            Msg msg = Msg.taskError(TO, FROM, TRACE, "SERVICE_UNAVAILABLE", "route-agent 超时");
            assertEquals(MessageType.TASK_ERROR, msg.type());
            assertEquals("SERVICE_UNAVAILABLE", msg.payload().get("errorCode"));
            assertEquals("route-agent 超时", msg.payload().get("errorMessage"));
            assertFalse(msg.isFallback());
        }

        @Test
        void rejectsBlankErrorCode() {
            assertThrows(InvalidRequestException.class,
                    () -> Msg.taskError(TO, FROM, TRACE, "  ", "msg"));
            assertThrows(InvalidRequestException.class,
                    () -> Msg.taskError(TO, FROM, TRACE, "CODE", null));
        }

        @Test
        @DisplayName("回归：可识别错误契约序列化往返不静默移除（§2.3）")
        void errorContractSurvivesRoundTrip() {
            Msg original = Msg.taskError(TO, FROM, TRACE, "SERVICE_UNAVAILABLE", "route-agent 超时");
            Msg restored = JsonUtils.fromJson(JsonUtils.toJson(original), Msg.class);
            assertEquals(MessageType.TASK_ERROR, restored.type());
            assertEquals("SERVICE_UNAVAILABLE", restored.payload().get("errorCode"));
            assertEquals("route-agent 超时", restored.payload().get("errorMessage"));
            assertEquals(TRACE, restored.traceId());
            assertFalse(restored.isFallback());
        }
    }

    @Nested
    @DisplayName("不可变性：构造后 payload 不可改、防御性拷贝")
    class Immutability {

        @Test
        void payloadIsUnmodifiable() {
            Msg msg = Msg.taskAssign(FROM, TO, TRACE, assignPayload());
            assertThrows(UnsupportedOperationException.class,
                    () -> msg.payload().put("x", "y"));
        }

        @Test
        void defensivelyCopiesSourcePayload() {
            Map<String, Object> source = assignPayload();
            Msg msg = Msg.taskAssign(FROM, TO, TRACE, source);
            source.put("mutated", "after-construction");
            assertFalse(msg.payload().containsKey("mutated"));
        }
    }

    @Nested
    @DisplayName("序列化往返：§2.3 关键字段不静默移除")
    class RoundTrip {

        @Test
        void taskAssignRoundTripsAllFields() {
            Map<String, Object> p = new HashMap<>();
            p.put("taskType", "ROUTE_PLANNING");
            Msg original = Msg.taskAssign(FROM, TO, TRACE, p, 20_000L);
            Msg restored = JsonUtils.fromJson(JsonUtils.toJson(original), Msg.class);
            assertEquals(original, restored);
            assertEquals(TRACE, restored.traceId());
            assertEquals(20_000L, restored.timeoutMs());
        }

        @Test
        void fallbackSurvivesRoundTrip() {
            Msg original = Msg.taskResult(TO, FROM, TRACE, "offline-estimate", true);
            Msg restored = JsonUtils.fromJson(JsonUtils.toJson(original), Msg.class);
            assertTrue(restored.isFallback());
            assertEquals(MessageType.TASK_RESULT, restored.type());
            assertEquals(TRACE, restored.traceId());
        }
    }

    @Nested
    @DisplayName("traceId 贯穿：调用方透传")
    class TracePropagation {

        @Test
        void resultPropagatesAssignTraceId() {
            Msg assign = Msg.taskAssign(FROM, TO, TRACE, assignPayload());
            Msg result = Msg.taskResult(TO, FROM, assign.traceId(), "ok", false);
            assertEquals(assign.traceId(), result.traceId());
            // 每条消息有独立 msgId
            assertNotEquals(assign.msgId(), result.msgId());
        }
    }

    @Nested
    @DisplayName("向后兼容：未知字段忽略而非失败（边界 #4 / §2.3）")
    class BackwardCompatibility {

        @Test
        @DisplayName("反序列化含未知顶层字段的消息不失败，子 Agent 忽略新增字段")
        void ignoresUnknownTopLevelFieldsOnDeserialize() {
            // 模拟新版本调用方多带了未声明的顶层字段（如 priority），老版本反序列化应忽略而非崩
            String futureJson = "{\"msgId\":\"M-1\",\"from\":\"supervisor\",\"to\":\"route-agent\","
                    + "\"type\":\"TASK_ASSIGN\",\"payload\":{\"taskType\":\"ROUTE_PLANNING\"},"
                    + "\"timeoutMs\":30000,\"traceId\":\"T-1\",\"priority\":\"HIGH\"}";
            Msg restored = JsonUtils.fromJson(futureJson, Msg.class);
            assertEquals(MessageType.TASK_ASSIGN, restored.type());
            assertEquals("ROUTE_PLANNING", restored.payload().get("taskType"));
            assertEquals("T-1", restored.traceId());
        }

        @Test
        @DisplayName("payload 内未知新增字段在往返中保留（向前兼容，不静默丢弃）")
        void preservesUnknownPayloadFieldsOnRoundTrip() {
            Map<String, Object> p = new HashMap<>();
            p.put("taskType", "ROUTE_PLANNING");
            // v2 才引入的字段，v1 不识别但应原样保留以便链路下游或回传时不丢信息
            p.put("experimentalHint", "prefer-scenic");
            Msg original = Msg.taskAssign(FROM, TO, TRACE, p);
            Msg restored = JsonUtils.fromJson(JsonUtils.toJson(original), Msg.class);
            assertEquals("prefer-scenic", restored.payload().get("experimentalHint"));
            assertEquals("ROUTE_PLANNING", restored.payload().get("taskType"));
        }
    }

    @Nested
    @DisplayName("canonical 构造器：payload null 安全（决策 D-3）")
    class CanonicalConstructor {

        @Test
        @DisplayName("HEARTBEAT/BROADCAST 等可直接构造，null payload 归一为不可变空 Map")
        void nullPayloadNormalisedToEmptyImmutable() {
            Msg msg = new Msg(IdUtils.newMsgId(), FROM, null, MessageType.BROADCAST, null, 0L, TRACE);
            assertTrue(msg.payload().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> msg.payload().put("x", "y"));
        }

        @Test
        @DisplayName("无 fallback 键的消息 isFallback() 为 false（TASK_ASSIGN）")
        void messageWithoutFallbackKeyIsNotFallback() {
            Msg assign = Msg.taskAssign(FROM, TO, TRACE, assignPayload());
            assertFalse(assign.payload().containsKey("fallback"));
            assertFalse(assign.isFallback());
        }
    }
}
