package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.MapTraffic;

/**
 * 双厂商 failover 路况查询器（C-007 增量 B / AC-9）。
 */
public final class FailoverTraffic implements MapTraffic {

    private final MapTraffic primary;
    private final MapTraffic secondary;
    private final FailoverExecutor executor;

    /**
     * @param primary        主厂商路况查询器
     * @param secondary      备厂商路况查询器
     * @param primaryHealth  主厂商熔断状态
     * @param secondaryHealth 备厂商熔断状态
     * @param metrics        指标（可 null）
     */
    public FailoverTraffic(MapTraffic primary, MapTraffic secondary,
                           VendorHealth primaryHealth, VendorHealth secondaryHealth,
                           AgentMetrics metrics) {
        this.primary = primary;
        this.secondary = secondary;
        this.executor = new FailoverExecutor(primaryHealth, secondaryHealth, metrics);
    }

    @Override
    public double trafficFactor(GeoLocation from, GeoLocation to) throws Exception {
        return executor.execute(
                () -> primary.trafficFactor(from, to),
                () -> secondary.trafficFactor(from, to));
    }
}
