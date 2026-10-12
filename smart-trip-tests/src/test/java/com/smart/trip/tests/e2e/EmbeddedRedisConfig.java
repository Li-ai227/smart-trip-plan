package com.smart.trip.tests.e2e;

import org.springframework.boot.test.context.TestConfiguration;
import redis.embedded.RedisServer;

/**
 * embedded-redis 生命周期管理（C-012 要素四）。
 *
 * <p>端口 6399（对齐 application-e2e.yml），与本地真实 Redis 默认端口 6379 隔离。
 * 采用静态初始化块确保 Redis 在 Spring Boot 自动装配 Redis 连接之前启动。
 * JVM 关闭钩子负责停止 Redis 进程，避免端口占用污染其他测试。</p>
 */
@TestConfiguration
public class EmbeddedRedisConfig {

    private static final int REDIS_PORT = 6399;
    private static RedisServer redisServer;

    static {
        try {
            redisServer = new RedisServer(REDIS_PORT);
            redisServer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (redisServer != null && redisServer.isActive()) {
                    redisServer.stop();
                }
            }));
        } catch (Exception e) {
            // Port already in use from a prior test run — redis is already running
            if (!e.getMessage().contains("Address already in use")
                    && !e.getMessage().contains("bind")) {
                throw new IllegalStateException("embedded Redis 启动失败 port=" + REDIS_PORT, e);
            }
        }
    }
}
