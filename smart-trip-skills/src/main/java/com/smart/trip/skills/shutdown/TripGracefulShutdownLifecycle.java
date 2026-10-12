package com.smart.trip.skills.shutdown;

import io.agentscope.core.shutdown.GracefulShutdownManager;

/**
 * Spring 生命周期桥接：优雅关停（C-005 增量 B AC-13）。
 *
 * <p>由 Spring {@code @PreDestroy} 或 {@code SmartLifecycle.stop()} 触发
 * {@link #shutdown()}，委托框架 {@link GracefulShutdownManager#performGracefulShutdown()}
 * 执行：拒绝新请求、等待进行中 {@code HarnessAgent.call()} 完成（configurable timeout 30s）、
 * 超时后强制停止。</p>
 */
public final class TripGracefulShutdownLifecycle {

    /**
     * 触发优雅关停。
     */
    public void shutdown() {
        GracefulShutdownManager.getInstance().performGracefulShutdown();
    }
}
