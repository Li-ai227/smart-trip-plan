package com.smart.trip.skills.route;

import com.smart.trip.common.model.Route;
import com.smart.trip.common.util.CollectionUtils;
import java.util.List;

/**
 * 路线规划结果（C-007 AC-2/AC-4/AC-5）。
 *
 * <p>由 {@link RoutePlanningService} 产出、经 RouteAgent 装配为 {@code TASK_RESULT}：
 * {@code routes} 为按输入访问序的相邻点路线列表；{@code overLimitRoutes} 为耗时 &gt; 120min 的超限路线；
 * {@code unreachableSegments} 为因坐标缺失而跳过的不可达段说明。</p>
 *
 * @param routes              相邻点路线列表（按访问序，不可变）
 * @param overLimitRoutes     超限路线清单（durationMin &gt; 120，不可变）
 * @param unreachableSegments 不可达段说明（不可变）
 * @param fallback            是否为降级产出（任意段降级即 true）
 * @param message             可读说明，可空
 * @param telemetry           可观测遥测
 */
public record RoutePlanResult(
        List<Route> routes,
        List<Route> overLimitRoutes,
        List<String> unreachableSegments,
        boolean fallback,
        String message,
        Telemetry telemetry) {

    /** 交通耗时上限（分钟），超过即进入超限清单（业务模型.md §4 强规则）。 */
    public static final int DURATION_LIMIT_MINUTES = 120;

    /**
     * 紧凑构造器：集合 null 安全不可变拷贝，遥测缺省归一。
     */
    public RoutePlanResult {
        routes = CollectionUtils.safeCopy(routes);
        overLimitRoutes = CollectionUtils.safeCopy(overLimitRoutes);
        unreachableSegments = CollectionUtils.safeCopy(unreachableSegments);
        telemetry = telemetry == null ? Telemetry.none() : telemetry;
    }

    /**
     * 可观测遥测载荷（C-007 AC-7）。
     *
     * @param rateLimited 是否命中限频
     */
    public record Telemetry(boolean rateLimited) {

        /**
         * 无遥测信号。
         *
         * @return 缺省遥测
         */
        public static Telemetry none() {
            return new Telemetry(false);
        }
    }
}
