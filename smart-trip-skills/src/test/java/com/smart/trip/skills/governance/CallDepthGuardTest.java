package com.smart.trip.skills.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CallDepthGuard} 单测：调用链深度 ≤5 阻断（C-005 AC-4 / 边界 #2 / 验收 Case-3）。
 *
 * <p>防死锁/死循环：第 6 跳被阻断并返回可识别错误（{@link CallChainDepthExceededException}）。</p>
 */
class CallDepthGuardTest {

    @Test
    @DisplayName("最大深度常量为 5（接口协议 §2 调用约束）")
    void maxDepthIsFive() {
        assertEquals(5, CallDepthGuard.MAX_DEPTH);
    }

    @Test
    @DisplayName("深度 0..4 进入返回 1..5，均放行")
    void allowsUpToMaxDepth() {
        CallDepthGuard guard = new CallDepthGuard();
        assertEquals(1, guard.enter(0));
        assertEquals(5, guard.enter(4));
    }

    @Test
    @DisplayName("在深度 5 再进入（第 6 跳）被阻断")
    void blocksBeyondMaxDepth() {
        CallDepthGuard guard = new CallDepthGuard();
        assertThrows(CallChainDepthExceededException.class, () -> guard.enter(5));
    }

    @Test
    @DisplayName("Case-3：构造深度=6 的委派链，第 6 跳被阻断且错误可识别")
    void chainOfSixIsBlockedWithIdentifiableError() {
        CallDepthGuard guard = new CallDepthGuard();
        int depth = 0;
        for (int hop = 1; hop <= 5; hop++) {
            depth = guard.enter(depth);
        }
        assertEquals(5, depth);
        final int reached = depth;
        CallChainDepthExceededException ex =
                assertThrows(CallChainDepthExceededException.class, () -> guard.enter(reached));
        // 可识别错误：属项目异常体系，码为 SERVICE_UNAVAILABLE（接口协议 §1.1 → 503）
        assertTrue(ex instanceof BaseException);
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    @DisplayName("负深度快速失败（防御式）")
    void rejectsNegativeDepth() {
        CallDepthGuard guard = new CallDepthGuard();
        assertThrows(IllegalArgumentException.class, () -> guard.enter(-1));
    }
}
