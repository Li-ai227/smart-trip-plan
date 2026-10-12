package com.smart.trip.skills.route;

import com.smart.trip.common.model.GeoLocation;

/**
 * Haversine 公式工具（C-007 AC-5 ②）：离线直线距离估算降级方案。
 *
 * <p>当 {@link MapRouter} 不可用时，以 haversine 公式计算两点间球面距离，
 * 并按平均速度推算耗时（{@code fallback=true}）。</p>
 */
public final class HaversineUtils {

    /** 地球平均半径（公里）。 */
    static final double EARTH_RADIUS_KM = 6371.0;

    /** 离线估算默认平均速度（公里/小时），用于推算耗时。 */
    static final double DEFAULT_AVG_SPEED_KMH = 40.0;

    private HaversineUtils() {
    }

    /**
     * 计算两点间的球面距离（公里）。
     *
     * @param from 起点
     * @param to   终点
     * @return 球面距离（公里），≥0
     * @throws NullPointerException 当 from 或 to 为 null
     */
    public static double distanceKm(GeoLocation from, GeoLocation to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(to.longitude() - from.longitude());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * 按平均速度推算两点间的耗时（分钟）。
     *
     * @param from 起点
     * @param to   终点
     * @return 估算耗时（分钟），≥0
     */
    public static int estimateDurationMin(GeoLocation from, GeoLocation to) {
        double km = distanceKm(from, to);
        return (int) Math.ceil(km / DEFAULT_AVG_SPEED_KMH * 60);
    }
}
