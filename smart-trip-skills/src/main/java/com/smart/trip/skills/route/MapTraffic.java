package com.smart.trip.skills.route;

import com.smart.trip.common.model.GeoLocation;

/**
 * 实时路况端口（C-007 AC-5 ③，可选增强）：查询两点间当前路况系数。
 *
 * <p>路况不可用时静默忽略（按平均速度估算），主链路不受影响（不标记 fallback）。</p>
 */
@FunctionalInterface
public interface MapTraffic {

    /**
     * 查询两点间的路况拥堵系数。
     *
     * @param from 起点，非 null
     * @param to   终点，非 null
     * @return 拥堵系数（1.0 = 畅通，>1.0 = 拥堵程度）
     * @throws Exception 查询失败（由调用方忽略路况）
     */
    double trafficFactor(GeoLocation from, GeoLocation to) throws Exception;
}
