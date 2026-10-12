package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.MapRouter;

/**
 * 双厂商 failover 路线规划器（C-007 增量 B / AC-9）。
 */
public final class FailoverRouter implements MapRouter {

    private final MapRouter primary;
    private final MapRouter secondary;
    private final FailoverExecutor executor;

    /**
     * @param primary        主厂商路线规划器
     * @param secondary      备厂商路线规划器
     * @param primaryHealth  主厂商熔断状态
     * @param secondaryHealth 备厂商熔断状态
     * @param metrics        指标（可 null）
     */
    public FailoverRouter(MapRouter primary, MapRouter secondary,
                          VendorHealth primaryHealth, VendorHealth secondaryHealth,
                          AgentMetrics metrics) {
        this.primary = primary;
        this.secondary = secondary;
        this.executor = new FailoverExecutor(primaryHealth, secondaryHealth, metrics);
    }

    @Override
    public Route route(GeoLocation from, GeoLocation to) throws Exception {
        return executor.execute(
                () -> primary.route(from, to),
                () -> secondary.route(from, to));
    }
}
