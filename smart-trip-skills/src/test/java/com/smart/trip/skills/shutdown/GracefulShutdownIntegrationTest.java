package com.smart.trip.skills.shutdown;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.shutdown.AgentShuttingDownException;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link GracefulShutdownManager} 集成验证（C-005 增量 B AC-13 / TDD Red→Green）。
 *
 * <p>验证框架 {@code GracefulShutdownManager} 的关键行为：关停期间拒绝新请求
 * ({@code AgentShuttingDownException})，以及 {@code TripGracefulShutdownLifecycle}
 * 触发 {@code performGracefulShutdown()} 后 manager 不再接受请求。</p>
 */
class GracefulShutdownIntegrationTest {

    @AfterEach
    void resetManager() {
        GracefulShutdownManager.getInstance().resetForTesting();
    }

    @Test
    @DisplayName("正常状态：manager 接受请求")
    void normalState_acceptingRequests() {
        assertTrue(GracefulShutdownManager.getInstance().isAcceptingRequests());
    }

    @Test
    @DisplayName("关停后：ensureAcceptingRequests 抛 AgentShuttingDownException")
    void afterShutdown_rejectsNewRequests() {
        GracefulShutdownManager manager = GracefulShutdownManager.getInstance();

        TripGracefulShutdownLifecycle lifecycle = new TripGracefulShutdownLifecycle();
        lifecycle.shutdown();

        assertFalse(manager.isAcceptingRequests(), "关停后不应再接受请求");
        assertThrows(AgentShuttingDownException.class,
                manager::ensureAcceptingRequests,
                "关停后新请求应抛 AgentShuttingDownException");
    }

    @Test
    @DisplayName("lifecycle shutdown 调用 performGracefulShutdown")
    void lifecycle_triggersManagerShutdown() {
        TripGracefulShutdownLifecycle lifecycle = new TripGracefulShutdownLifecycle();
        lifecycle.shutdown();

        assertFalse(GracefulShutdownManager.getInstance().isAcceptingRequests());
    }
}
