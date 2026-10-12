package com.smart.trip.agent.route.mcp;

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReconnectingMcpClientWrapper} 单元测试。
 */
class ReconnectingMcpClientWrapperTest {

    private static final McpSchema.CallToolResult OK_RESULT =
            new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("ok")), false);

    @Test
    void normal_call_passes_through_without_reconnect() {
        StubMcpClient stub = new StubMcpClient(() -> Mono.just(OK_RESULT));
        ReconnectingMcpClientWrapper wrapper = newWrapper(stub);

        McpSchema.CallToolResult result = wrapper.callTool("test", Map.of()).block();

        assertNotNull(result);
        assertEquals(1, stub.callCount.get());
    }

    @Test
    void session_expired_triggers_reconnect_and_retries() {
        AtomicInteger attempt = new AtomicInteger();
        StubMcpClient stub = new StubMcpClient(() -> {
            if (attempt.getAndIncrement() == 0) {
                return Mono.error(new RuntimeException(
                        "Sending message failed with a non-OK HTTP code: 400 - "
                                + "{\"message\":\"Session ID not found, Please reconnect\"}"));
            }
            return Mono.just(OK_RESULT);
        });

        ReconnectingMcpClientWrapper wrapper = newTestableWrapper(stub);

        McpSchema.CallToolResult result = wrapper.callTool("test", Map.of()).block();

        assertNotNull(result);
        assertEquals(2, attempt.get(), "should have retried once after reconnect");
    }

    @Test
    void reconnect_failure_propagates_error() {
        StubMcpClient stub = new StubMcpClient(() -> Mono.error(new RuntimeException(
                "Session ID not found, Please reconnect")));

        ReconnectingMcpClientWrapper wrapper = new ReconnectingMcpClientWrapper(
                "test", stub, "https://invalid.example.com/sse", Duration.ofSeconds(5), Duration.ofSeconds(2)) {
            @Override
            synchronized void reconnect() {
                throw new RuntimeException("reconnect failed: network unreachable");
            }
        };

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> wrapper.callTool("test", Map.of()).block());
        assertTrue(ex.getMessage().contains("reconnect failed"));
    }

    @Test
    void non_session_error_does_not_trigger_reconnect() {
        AtomicInteger callCount = new AtomicInteger();
        StubMcpClient stub = new StubMcpClient(() -> {
            callCount.incrementAndGet();
            return Mono.error(new RuntimeException("MCP 工具执行超时"));
        });

        ReconnectingMcpClientWrapper wrapper = newTestableWrapper(stub);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> wrapper.callTool("test", Map.of()).block());
        assertTrue(ex.getMessage().contains("超时"));
        assertEquals(1, callCount.get(), "non-session error should NOT trigger retry");
    }

    @Test
    void isSessionExpired_checks_cause_chain() {
        RuntimeException nested = new RuntimeException("outer",
                new RuntimeException("inner: Session ID not found, Please reconnect"));
        assertTrue(ReconnectingMcpClientWrapper.isSessionExpired(nested));

        assertFalse(ReconnectingMcpClientWrapper.isSessionExpired(
                new RuntimeException("some other error")));

        assertFalse(ReconnectingMcpClientWrapper.isSessionExpired(null));
    }

    private static ReconnectingMcpClientWrapper newWrapper(StubMcpClient stub) {
        return new ReconnectingMcpClientWrapper(
                "test", stub, "https://mcp.amap.com/sse?key=test",
                Duration.ofSeconds(5), Duration.ofSeconds(2));
    }

    private static ReconnectingMcpClientWrapper newTestableWrapper(StubMcpClient stub) {
        return new ReconnectingMcpClientWrapper(
                "test", stub, "https://mcp.amap.com/sse?key=test",
                Duration.ofSeconds(5), Duration.ofSeconds(2)) {
            @Override
            synchronized void reconnect() {
                // no-op in test: reuse same stub (its callTool lambda handles retry logic)
            }
        };
    }

    private interface CallToolAction {
        Mono<McpSchema.CallToolResult> call();
    }

    static class StubMcpClient extends McpClientWrapper {
        final AtomicInteger callCount = new AtomicInteger();
        private final CallToolAction action;

        StubMcpClient(CallToolAction action) {
            super("stub");
            this.action = action;
        }

        @Override
        public Mono<Void> initialize() {
            return Mono.empty();
        }

        @Override
        public Mono<List<McpSchema.Tool>> listTools() {
            return Mono.just(List.of());
        }

        @Override
        public Mono<McpSchema.CallToolResult> callTool(String name, Map<String, Object> args) {
            callCount.incrementAndGet();
            return action.call();
        }

        @Override
        public Mono<McpSchema.CallToolResult> callTool(String name, Map<String, Object> args, Map<String, Object> metadata) {
            return callTool(name, args);
        }

        @Override
        public void close() {
        }
    }
}
