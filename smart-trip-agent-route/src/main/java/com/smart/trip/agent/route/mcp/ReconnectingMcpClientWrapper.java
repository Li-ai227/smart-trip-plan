package com.smart.trip.agent.route.mcp;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 具备 SSE session 过期自动重连能力的 MCP 客户端包装器。
 *
 * <p>高德 MCP Server 的 SSE session 有服务端超时机制，过期后返回
 * HTTP 400 {@code "Session ID not found, Please reconnect"}。
 * 本类在检测到该错误时自动关闭旧连接、创建新连接并重试一次。</p>
 */
class ReconnectingMcpClientWrapper extends McpClientWrapper {

    private static final System.Logger LOG =
            System.getLogger(ReconnectingMcpClientWrapper.class.getName());

    static final String SESSION_EXPIRED_MARKER = "Session ID not found";

    private final String sseUrl;
    private final Duration timeout;
    private final Duration initTimeout;

    // volatile 足够：整体替换单个引用（重连时），无复合读改写操作，不需要 AtomicReference。
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private volatile McpClientWrapper delegate;

    /**
     * @param name        客户端名称
     * @param delegate    初始已连接的 MCP 客户端
     * @param sseUrl      SSE 连接 URL（含 key 参数）
     * @param timeout     MCP 调用超时
     * @param initTimeout MCP 初始化超时
     */
    ReconnectingMcpClientWrapper(String name, McpClientWrapper delegate,
                                  String sseUrl, Duration timeout, Duration initTimeout) {
        super(name);
        if (delegate == null) {
            throw new IllegalArgumentException("delegate 不可为 null");
        }
        this.delegate = delegate;
        this.sseUrl = sseUrl;
        this.timeout = timeout;
        this.initTimeout = initTimeout;
    }

    @Override
    public Mono<Void> initialize() {
        return delegate.initialize();
    }

    @Override
    public Mono<List<McpSchema.Tool>> listTools() {
        return delegate.listTools();
    }

    @Override
    public Mono<McpSchema.CallToolResult> callTool(String toolName,
                                                    java.util.Map<String, Object> arguments) {
        return delegate.callTool(toolName, arguments)
                .onErrorResume(ex -> {
                    if (isSessionExpired(ex)) {
                        LOG.log(System.Logger.Level.WARNING,
                                "MCP session 过期，尝试重连: {0}", ex.getMessage());
                        try {
                            reconnect();
                        } catch (Exception reconnectEx) {
                            LOG.log(System.Logger.Level.ERROR,
                                    "MCP 重连失败: {0}", reconnectEx.getMessage());
                            return Mono.error(reconnectEx);
                        }
                        return delegate.callTool(toolName, arguments);
                    }
                    return Mono.error(ex);
                });
    }

    @Override
    public Mono<McpSchema.CallToolResult> callTool(String toolName,
                                                    Map<String, Object> arguments,
                                                    Map<String, Object> metadata) {
        return callTool(toolName, arguments);
    }

    @Override
    public void close() {
        McpClientWrapper current = delegate;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        }
    }

    /**
     * 关闭旧连接并创建新连接。
     */
    synchronized void reconnect() {
        McpClientWrapper oldDelegate = delegate;
        try {
            oldDelegate.close();
        } catch (Exception ignored) {
            // ignore cleanup failure
        }

        McpClientWrapper newClient = McpClientBuilder.create(name)
                .sseTransport(sseUrl)
                .timeout(timeout)
                .buildSync();
        newClient.initialize().block(initTimeout);
        delegate = newClient;
        LOG.log(System.Logger.Level.INFO, "MCP 重连成功: name={0}", name);
    }

    static boolean isSessionExpired(Throwable ex) {
        if (ex == null) {
            return false;
        }
        String msg = ex.getMessage();
        if (msg != null && msg.contains(SESSION_EXPIRED_MARKER)) {
            return true;
        }
        return isSessionExpired(ex.getCause());
    }
}
