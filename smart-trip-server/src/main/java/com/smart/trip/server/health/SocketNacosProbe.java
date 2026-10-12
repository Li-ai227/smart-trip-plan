package com.smart.trip.server.health;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 基于 TCP 连通性的 Nacos 探针默认实现（C-003）。
 *
 * <p>用 JDK 原生 {@link Socket} 探测 {@code host:port} 可达性，不引入 spring-cloud-alibaba /
 * 完整 nacos 客户端（规避 Spring Boot 4 兼容风险，对齐"Nacos 只就位、注册归 C-005"）。</p>
 */
public class SocketNacosProbe implements NacosProbe {

    private static final int DEFAULT_NACOS_PORT = 8848;

    private final String host;
    private final int port;
    private final int timeoutMillis;

    /**
     * 按 {@code host:port} 形式的地址构造探针。
     *
     * @param serverAddr    Nacos 地址，形如 {@code 127.0.0.1:8848}；缺省端口为 8848
     * @param timeoutMillis 连接超时（毫秒）
     */
    public SocketNacosProbe(String serverAddr, int timeoutMillis) {
        String safeAddr = serverAddr == null ? "" : serverAddr.trim();
        int sep = safeAddr.lastIndexOf(':');
        if (sep > 0) {
            this.host = safeAddr.substring(0, sep);
            this.port = parsePort(safeAddr.substring(sep + 1));
        } else {
            this.host = safeAddr.isEmpty() ? "localhost" : safeAddr;
            this.port = DEFAULT_NACOS_PORT;
        }
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public boolean isReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private static int parsePort(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_NACOS_PORT;
        }
    }
}
