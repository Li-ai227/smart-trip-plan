package com.smart.trip.skills.supervisor;

/**
 * C-013-timeout-dataflow：检查原生路是否存在数据流破坏。
 *
 * <p>当 XHS 子 Agent 的工具已成功产出候选景点（setXhsResult 已调用），
 * 但最终组装出的 plan days 为空时，说明 LLM 在 agent_spawn 超时/失败后
 * 错误地将候选数据丢弃（传空数组给下游 route/itinerary/budget），
 * 导致 Java 侧收口逻辑拿到的是"全空但 isComplete==true"的假成功。</p>
 *
 * <p>此检查在 {@code SupervisorHarnessAgentFactory#collectNativeResult} 的收口路径中调用，
 * 返回 true 时 run() 返回 null，由 {@code SupervisorAgent.resolve()} 统一触发确定性
 * fallback。</p>
 */
final class NativeDataFlowGuard {

    private static final System.Logger LOG =
            System.getLogger(NativeDataFlowGuard.class.getName());

    private NativeDataFlowGuard() {
    }

    /**
     * 检查 XHS 有候选但 plan days 为空的数据流破坏。
     *
     * @param ctx    per-run artifact 容器（含 XHS 工具写入的 typed result）
     * @param result 组装后的 native result
     * @return true 当 XHS 有候选但 plan days 为空（数据流破坏）
     */
    static boolean isDataFlowBroken(SupervisorNativeRunContext ctx, TripPlanResult result) {
        if (result == null) {
            return false;
        }
        var plan = result.plan();
        if (plan == null || !plan.days().isEmpty()) {
            return false;
        }
        var xhs = ctx != null ? ctx.xhsResult() : null;
        boolean broken = xhs != null && !xhs.candidates().isEmpty();
        if (broken) {
            LOG.log(System.Logger.Level.WARNING,
                    "native dataflow broken: xhsCandidates={0}, routeRoutes={1}, itineraryDays={2}",
                    xhs.candidates().size(),
                    ctx.routeResult() != null ? ctx.routeResult().routes().size() : -1,
                    ctx.itineraryResult() != null ? ctx.itineraryResult().days().size() : -1);
        }
        return broken;
    }
}
