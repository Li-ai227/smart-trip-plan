package com.smart.trip.common.model;

/**
 * 地理位置值对象（数据模型.md §2.4 / 业务模型.md §2），被景点/餐厅/路线多处引用。
 *
 * @param longitude 经度
 * @param latitude  纬度
 * @param address   详细地址
 * @param city      所在城市
 */
public record GeoLocation(
        double longitude,
        double latitude,
        String address,
        String city) {
}
