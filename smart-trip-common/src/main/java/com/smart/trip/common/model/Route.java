package com.smart.trip.common.model;

/**
 * 两点间路线（数据模型.md §2.4 / 业务模型.md §2）。
 *
 * <p>业务不变量（业务模型.md §4）：景点间交通耗时 {@code durationMin ≤ 120}；
 * 校验归 Route / Itinerary（C-007/C-008）。{@code fallback=true} 表示路线由离线估算降级产出
 * （接口协议.md §2.3：降级链路关键字段，不得静默移除）。</p>
 *
 * @param from          起点
 * @param to            终点
 * @param distanceKm    距离（公里）
 * @param durationMin   耗时（分钟）
 * @param transportMode 交通方式
 * @param fallback      是否为降级（离线估算）结果
 */
public record Route(
        GeoLocation from,
        GeoLocation to,
        double distanceKm,
        int durationMin,
        String transportMode,
        boolean fallback) {
}
