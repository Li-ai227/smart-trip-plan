package com.smart.trip.skills.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.common.exception.DownstreamUnavailableException;
import com.smart.trip.common.exception.InvalidRequestException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BaseAgent} 单测：A2A 收发、生命周期、降级与失败转 {@code TASK_ERROR}（C-005 AC-2/AC-5）。
 *
 * <p>对齐接口协议.md §2 测试关注点：子 Agent 失败必须返回可识别错误而非静默超时；
 * 降级路径 {@code TASK_RESULT} 必须显式 {@code fallback=true}（§2.2/§2.3）。</p>
 */
class BaseAgentTest {

    private static final String SUPERVISOR = "agent://supervisor";
    private static final String TRACE = "T-base-001";

    /** 测试用最小子 Agent：按 payload 指令产出正常/降级结果或抛错。 */
    private static final class StubAgent extends BaseAgent {
        StubAgent() {
            super(AgentId.of("route-planner"));
        }

        @Override
        protected AgentReply onTaskAssign(Msg assign) {
            String mode = (String) assign.payload().get("mode");
            if ("fallback".equals(mode)) {
                return AgentReply.fallback("offline-estimate");
            }
            if ("boom".equals(mode)) {
                throw new DownstreamUnavailableException("route-agent 下游不可用");
            }
            if ("npe".equals(mode)) {
                throw new IllegalStateException("unexpected");
            }
            return AgentReply.ok("route-ok:" + assign.payload().get("taskType"));
        }
    }

    private static Msg assign(String mode) {
        return Msg.taskAssign(SUPERVISOR, "agent://route-planner", TRACE,
                Map.of("taskType", "ROUTE_PLANNING", "mode", mode));
    }

    @Nested
    @DisplayName("标识与生命周期")
    class Lifecycle {

        @Test
        @DisplayName("初始状态为 IDLE，标识对齐 agent://<id>")
        void initialState() {
            StubAgent agent = new StubAgent();
            assertEquals(AgentStatus.IDLE, agent.status());
            assertEquals("agent://route-planner", agent.agentId().uri());
        }

        @Test
        @DisplayName("处理完成后回到 DONE")
        void doneAfterHandling() {
            StubAgent agent = new StubAgent();
            agent.receive(assign("ok"));
            assertEquals(AgentStatus.DONE, agent.status());
        }
    }

    @Nested
    @DisplayName("Happy Path：TASK_ASSIGN → TASK_RESULT（Case-1）")
    class HappyPath {

        @Test
        @DisplayName("正常往返：result 非降级，traceId 透传，收发方互换")
        void normalRoundTrip() {
            StubAgent agent = new StubAgent();
            Msg result = agent.receive(assign("ok"));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertEquals("agent://route-planner", result.from());
            assertEquals(SUPERVISOR, result.to());
            assertEquals(TRACE, result.traceId());
            assertEquals("OK", result.payload().get("status"));
            assertEquals("route-ok:ROUTE_PLANNING", result.payload().get("result"));
            assertFalse(result.isFallback());
        }
    }

    @Nested
    @DisplayName("降级：TASK_RESULT 显式 fallback=true（AC-5）")
    class Degradation {

        @Test
        void fallbackReplyIsMarked() {
            StubAgent agent = new StubAgent();
            Msg result = agent.receive(assign("fallback"));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertTrue(result.isFallback());
            assertEquals("offline-estimate", result.payload().get("result"));
            assertEquals(TRACE, result.traceId());
        }
    }

    @Nested
    @DisplayName("失败：返回可识别 TASK_ERROR，禁止静默超时（AC-5）")
    class Failure {

        @Test
        @DisplayName("BaseException 透出其错误码")
        void businessExceptionMappedToErrorCode() {
            StubAgent agent = new StubAgent();
            Msg result = agent.receive(assign("boom"));

            assertEquals(MessageType.TASK_ERROR, result.type());
            assertEquals("SERVICE_UNAVAILABLE", result.payload().get("errorCode"));
            assertEquals(TRACE, result.traceId());
            assertEquals(SUPERVISOR, result.to());
        }

        @Test
        @DisplayName("未预期异常归一为可识别错误码而非静默挂起")
        void unexpectedExceptionMappedToGenericErrorCode() {
            StubAgent agent = new StubAgent();
            Msg result = agent.receive(assign("npe"));

            assertEquals(MessageType.TASK_ERROR, result.type());
            assertEquals(BaseAgent.ERROR_CODE_AGENT_FAILURE, result.payload().get("errorCode"));
            // 失败后仍回到 DONE，不悬挂
            assertEquals(AgentStatus.DONE, agent.status());
        }
    }

    @Nested
    @DisplayName("边界：非法输入与向后兼容")
    class Boundaries {

        @Test
        @DisplayName("收到非 TASK_ASSIGN 抛 InvalidRequestException")
        void rejectsNonAssign() {
            StubAgent agent = new StubAgent();
            Msg result = Msg.taskResult(SUPERVISOR, "agent://route-planner", TRACE, "x", false);
            assertThrows(InvalidRequestException.class, () -> agent.receive(result));
        }

        @Test
        @DisplayName("null 消息抛 InvalidRequestException")
        void rejectsNull() {
            StubAgent agent = new StubAgent();
            assertThrows(InvalidRequestException.class, () -> agent.receive(null));
        }

        @Test
        @DisplayName("payload 含未知字段被忽略而非失败（边界 #4 / §2.3）")
        void ignoresUnknownPayloadFields() {
            StubAgent agent = new StubAgent();
            Msg assign = Msg.taskAssign(SUPERVISOR, "agent://route-planner", TRACE,
                    Map.of("taskType", "ROUTE_PLANNING", "experimentalHint", "prefer-scenic"));
            Msg result = agent.receive(assign);
            assertEquals(MessageType.TASK_RESULT, result.type());
            assertEquals("route-ok:ROUTE_PLANNING", result.payload().get("result"));
        }
    }
}
