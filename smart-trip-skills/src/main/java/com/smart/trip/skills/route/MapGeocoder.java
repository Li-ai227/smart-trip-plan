package com.smart.trip.skills.route;

import com.smart.trip.common.model.GeoLocation;

/**
 * 地理编码端口（C-007 AC-3）：地址/名称 → 经纬度。
 *
 * <p>增量 A 提供桩实现；增量 B 提供百度/高德真实 MCP 适配。
 * 所有实现的调用必须经 {@code GovernedExternalCaller} 统一治理。</p>
 */
@FunctionalInterface
public interface MapGeocoder {

    /**
     * 将地址或景点名称解析为地理坐标。
     *
     * @param address 地址或景点名称，非空
     * @param city    所在城市（可选，辅助精度），可空
     * @return 解析后的地理位置
     * @throws Exception 解析失败（由调用方走降级链）
     */
    GeoLocation geocode(String address, String city) throws Exception;
}
