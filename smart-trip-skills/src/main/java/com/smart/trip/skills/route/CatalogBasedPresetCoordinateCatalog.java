package com.smart.trip.skills.route;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.skills.xhs.AttractionCatalog;
import java.util.Optional;

/**
 * 基于 {@link AttractionCatalog} 的预置坐标目录（C-012-D）：把景点知识库的名称为坐标解析能力
 * 暴露为 {@link PresetCoordinateCatalog}，供 {@link RoutePlanningService} 在
 * {@link MapGeocoder} 失败时作为降级源使用。
 *
 * <p>当景点在知识库中有同名条目时，返回其预置坐标；否则返回 {@link Optional#empty()}，
 * 由 RoutePlanningService 跳过该段并记录不可达。</p>
 */
public final class CatalogBasedPresetCoordinateCatalog implements PresetCoordinateCatalog {

    private final AttractionCatalog attractionCatalog;

    /**
     * @param attractionCatalog 景点知识库目录（禁 null）
     */
    public CatalogBasedPresetCoordinateCatalog(AttractionCatalog attractionCatalog) {
        if (attractionCatalog == null) {
            throw new IllegalArgumentException("attractionCatalog 不可为 null");
        }
        this.attractionCatalog = attractionCatalog;
    }

    @Override
    public Optional<GeoLocation> findByName(String attractionName) {
        return attractionCatalog.findByName(attractionName)
                .map(attraction -> attraction.location());
    }
}
