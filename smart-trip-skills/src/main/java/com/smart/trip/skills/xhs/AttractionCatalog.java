package com.smart.trip.skills.xhs;

import com.smart.trip.common.model.Attraction;
import java.util.List;
import java.util.Optional;

/**
 * 景点目录端口：把笔记提及的景点 ID/名称解析为带评分的 {@link Attraction}（C-006 设计偏差登记）。
 *
 * <p>{@link com.smart.trip.common.model.XHSNote} 仅含提及景点 ID 与情感、不含评分，而候选筛选需
 * 依据 {@code rating ≥ 3.5}（业务模型.md §4）。本端口承载景点的真实评分/名称/位置等知识：
 * 主链路用内存桩；真实实现由 ReMe {@code attraction_kb} 向量召回承载，延后至 T-7/C-015。</p>
 *
 * <p>增量 B（AC-10）新增 {@link #findByName}：{@code XHSNoteParser} 从 LLM 抽取的景点名经此方法
 * 链接到目录 ID，未知名称返回 {@link Optional#empty()}（丢弃该提及而非伪造候选）。</p>
 */
public interface AttractionCatalog {

    /**
     * 按景点 ID 解析景点详情（含评分）。
     *
     * @param attractionId 景点 ID（来自笔记 {@code mentions} 的 {@code attractionId}），非空白
     * @return 命中返回景点详情；未知 ID 返回 {@link Optional#empty()}（候选聚合时跳过）
     */
    Optional<Attraction> findById(String attractionId);

    /**
     * 按景点名称解析景点详情（增量 B / AC-10 · LLM 抽取链接）。
     *
     * <p>{@code XHSNoteParser} 从 LLM 抽取出景点名（非 ID），经此方法解析到已知景点；
     * 未知名称返回空（丢弃该提及，不伪造候选）。默认实现返回 {@link Optional#empty()}，
     * 即不支持名称查找——仅 ID 查找的桩实现无需改动。</p>
     *
     * @param name 景点名称（来自 LLM 抽取），非空白
     * @return 命中返回景点详情；未知名称返回 {@link Optional#empty()}
     */
    default Optional<Attraction> findByName(String name) {
        return Optional.empty();
    }

    /**
     * 按城市名返回该城市的全部景点列表（C-012-D fix）。
     *
     * <p>默认实现返回空列表；仅 ID/名称查找的桩实现无需改动。
     * {@link FileBasedAttractionCatalog} 覆写此方法提供 O(1) 城市索引。</p>
     *
     * @param city 城市名（如"北京"），非空白
     * @return 该城市的景点列表；未知城市返回空列表（不返回 null）
     */
    default List<Attraction> findByCity(String city) {
        return List.of();
    }
}
