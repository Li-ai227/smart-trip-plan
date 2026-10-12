package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.MapGeocoder;

/**
 * 双厂商 failover 地理编码器（C-007 增量 B / AC-9）。
 *
 * <p>委托 {@link FailoverExecutor} 实现熔断 + 切换逻辑，避免与
 * {@link FailoverRouter}/{@link FailoverTraffic} 之间的代码重复。</p>
 */
public final class FailoverGeocoder implements MapGeocoder {

    private final MapGeocoder primary;
    private final MapGeocoder secondary;
    private final FailoverExecutor executor;

    /**
     * @param primary        主厂商地理编码器
     * @param secondary      备厂商地理编码器
     * @param primaryHealth  主厂商熔断状态
     * @param secondaryHealth 备厂商熔断状态
     * @param metrics        指标（可 null）
     */
    public FailoverGeocoder(MapGeocoder primary, MapGeocoder secondary,
                            VendorHealth primaryHealth, VendorHealth secondaryHealth,
                            AgentMetrics metrics) {
        this.primary = primary;
        this.secondary = secondary;
        this.executor = new FailoverExecutor(primaryHealth, secondaryHealth, metrics);
    }

    @Override
    public GeoLocation geocode(String address, String city) throws Exception {
        return executor.execute(
                () -> primary.geocode(address, city),
                () -> secondary.geocode(address, city));
    }
}
