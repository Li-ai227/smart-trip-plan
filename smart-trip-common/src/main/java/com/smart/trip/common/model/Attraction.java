package com.smart.trip.common.model;

import com.smart.trip.common.util.CollectionUtils;
import java.math.BigDecimal;
import java.util.List;

/**
 * 景点（数据模型.md §2.4 / 业务模型.md §2）。
 *
 * <p>业务不变量（业务模型.md §4）：推荐景点 {@code rating ≥ 3.5}、建议游玩时长用于行程编排；
 * 这些阈值校验归对应 Agent / OutputQualityGate（C-006/C-008），本模型仅承载字段契约。</p>
 *
 * @param attractionId 景点 ID
 * @param name         名称
 * @param location     地理位置
 * @param rating       评分（0–5）
 * @param durationMin  建议游玩时长（分钟）
 * @param ticketPrice  门票价格（元）
 * @param tags         标签，可空（构造时归一为不可变空列表）
 */
public record Attraction(
        String attractionId,
        String name,
        GeoLocation location,
        double rating,
        int durationMin,
        BigDecimal ticketPrice,
        List<String> tags) {

    /**
     * 紧凑构造器：对可空集合做 null 安全的不可变拷贝（决策 D-5/D-6）。
     */
    public Attraction {
        tags = CollectionUtils.safeCopy(tags);
    }
}
