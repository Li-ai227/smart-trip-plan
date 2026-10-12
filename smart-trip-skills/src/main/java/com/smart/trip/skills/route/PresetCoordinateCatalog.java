package com.smart.trip.skills.route;

import com.smart.trip.common.model.GeoLocation;
import java.util.Optional;

/**
 * 预置坐标目录端口（C-007 AC-5 ①）：地理编码降级源。
 *
 * <p>当 {@link MapGeocoder} 失败时，从预置坐标库查找降级坐标。
 * 预置坐标也缺失时跳过该段 + 记录不可达说明。</p>
 */
@FunctionalInterface
public interface PresetCoordinateCatalog {

    /**
     * 按景点名称查找预置坐标。
     *
     * @param attractionName 景点名称，非空
     * @return 预置坐标（空表示无预置数据）
     */
    Optional<GeoLocation> findByName(String attractionName);
}
