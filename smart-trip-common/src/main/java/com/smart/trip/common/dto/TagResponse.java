package com.smart.trip.common.dto;

import java.util.List;

/**
 * 标签响应 DTO（C-014）。
 *
 * <p>用于 {@code GET /api/v1/trip-plan/tags} 接口的响应体，返回所有可用标签列表。</p>
 *
 * @param tags 标签列表，按频次降序排列（Top 30）
 * @author smart-trip-plan
 */
public record TagResponse(List<String> tags) {

    /**
     * 创建空的标签响应。
     *
     * @return 空标签列表
     */
    public static TagResponse empty() {
        return new TagResponse(List.of());
    }

    /**
     * 创建包含指定标签的响应。
     *
     * @param tags 标签列表，不可为 null
     * @return 标签响应
     */
    public static TagResponse of(List<String> tags) {
        return new TagResponse(tags != null ? tags : List.of());
    }
}
