package com.smart.trip.common.model;

import com.smart.trip.common.enums.SentimentType;

/**
 * 景点提及（逐提及情感，数据模型.md §2.5 / C-006 增量 B AC-11）。
 *
 * <p>不可变记录，承载一条小红书笔记对单个景点的一次提及及其情感。情感粒度由「整篇单值」
 * 下沉到「每个提及」后，{@link XHSNote#mentions()} 以本记录的列表表达「一篇夸 A 吐槽 B」，
 * 由 {@code xhs-note-analysis} 的 {@code XHSNoteParser} 抽取产出、{@code XHSNoteAnalyzer}
 * 按提及聚合（业务模型.md §4 情感筛选）。本模型仅承载字段契约，不含判定逻辑。</p>
 *
 * @param attractionId 提及的景点 ID（链接到 {@code AttractionCatalog} 的键）
 * @param sentiment    本次提及的情感倾向（POSITIVE/NEUTRAL/NEGATIVE）
 */
public record AttractionMention(String attractionId, SentimentType sentiment) {
}
