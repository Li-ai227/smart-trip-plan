package com.smart.trip.skills.route;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;

/**
 * Haversine 离线路线估算器（C-012-D）：当真实地图路线 API 不可用时，
 * 以 haversine 球面距离 + 平均速度推算耗时，产出 {@code fallback=true} 的 Route。
 *
 * <p>实现 {@link MapRouter} 接口，可注入 {@link RoutePlanningService} 替代生产路由端口，
 * 确保无真实地图 Key / 网络不可达时仍能产出合理的离线路线估算。</p>
 */
public final class HaversineRouter implements MapRouter {

    @Override
    public Route route(GeoLocation from, GeoLocation to) {
        double km = HaversineUtils.distanceKm(from, to);
        int minutes = HaversineUtils.estimateDurationMin(from, to);
        return new Route(from, to, km, minutes, "驾车", true);
    }
}
