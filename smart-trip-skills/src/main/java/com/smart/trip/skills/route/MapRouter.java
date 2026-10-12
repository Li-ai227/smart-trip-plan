package com.smart.trip.skills.route;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;

/**
 * 点间路线规划端口（C-007 AC-3）：两点 → 距离/耗时/方式。
 *
 * <p>增量 A 提供桩实现；增量 B 提供百度/高德真实 MCP 适配。</p>
 */
@FunctionalInterface
public interface MapRouter {

    /**
     * 规划两点间的交通路线。
     *
     * @param from 起点，非 null
     * @param to   终点，非 null
     * @return 路线（含距离、耗时、交通方式）
     * @throws Exception 规划失败（由调用方走降级链）
     */
    Route route(GeoLocation from, GeoLocation to) throws Exception;
}
