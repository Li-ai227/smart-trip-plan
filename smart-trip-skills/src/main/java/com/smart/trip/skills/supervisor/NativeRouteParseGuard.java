package com.smart.trip.skills.supervisor;

/**
 * 检测 native 路 route 工具是否因参数解析失败（而非业务无需路线）而返回空路线（C-013-B）。
 *
 * <p>触发条件：XHS 已产出 ≥2 个候选景点，但 route 工具返回 "景点数不足" 消息。
 * 这表示 LLM 传给 plan_routes 的 JSON 丢掉了关键字段（如 durationMin），
 * 导致 AttractionJsonParser 解析出不完整的 Attraction 对象 → Service 早退。
 * 此时应立即触发 fallback，不要继续跑 itinerary/budget。</p>
 */
final class NativeRouteParseGuard {

    private NativeRouteParseGuard() {
    }

    /**
     * @param ctx per-run artifact 容器
     * @return true 当 XHS 有足够候选但 route 因解析失败返回空
     */
    static boolean isRouteParseFailure(SupervisorNativeRunContext ctx) {
        if (ctx == null) {
            return false;
        }
        var xhs = ctx.xhsResult();
        var route = ctx.routeResult();
        if (xhs == null || route == null) {
            return false;
        }
        // XHS 有足够候选，但 route 报景点不足 → JSON 传参失败
        return xhs.candidates().size() >= 2
                && route.routes().isEmpty()
                && route.message() != null
                && route.message().contains("景点数不足");
    }
}
