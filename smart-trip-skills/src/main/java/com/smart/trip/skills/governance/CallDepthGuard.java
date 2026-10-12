package com.smart.trip.skills.governance;

/**
 * A2A 调用链深度守卫（C-005 AC-4 / 边界 #2 / 验收 Case-3）。
 *
 * <p>限制委派链深度 ≤ {@link #MAX_DEPTH}（接口协议.md §2 调用约束），防止 Agent 间相互委派形成
 * 死锁/死循环。第 {@code MAX_DEPTH+1} 跳被阻断并抛可识别 {@link CallChainDepthExceededException}。
 * 本守卫无状态、线程安全，深度由调用方在链路上下文中传递。</p>
 */
public final class CallDepthGuard {

    /** 调用链最大深度（接口协议.md §2：≤5，防死锁）。 */
    public static final int MAX_DEPTH = 5;

    /**
     * 在当前深度上进入下一跳委派，返回新深度。
     *
     * @param currentDepth 当前链路深度（{@code >= 0}，0 表示尚未发生委派）
     * @return 进入后的新深度 {@code currentDepth + 1}
     * @throws IllegalArgumentException             当 currentDepth 为负
     * @throws CallChainDepthExceededException 当进入会使深度超过 {@link #MAX_DEPTH}
     */
    public int enter(int currentDepth) {
        if (currentDepth < 0) {
            throw new IllegalArgumentException("currentDepth 不可为负: " + currentDepth);
        }
        if (currentDepth >= MAX_DEPTH) {
            throw new CallChainDepthExceededException(
                    "调用链深度超限(>" + MAX_DEPTH + ")，阻断更深委派以防死锁: depth=" + currentDepth);
        }
        return currentDepth + 1;
    }
}
