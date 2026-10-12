package com.smart.trip.skills.xhs;

import com.smart.trip.common.model.Attraction;

/**
 * 候选景点（笔记分析产物，C-006 AC-3/AC-5）。
 *
 * <p>在 {@link Attraction} 字段契约之上承载分析维度：{@code mentionCount}（提及频次，驱动排序）
 * 与情感聚合（{@code positiveMentions}/{@code negativeMentions}/{@code neutralMentions}）。
 * 评分阈值（≥3.5）与负面情感剔除属本 Skill 内部职责；候选全局一致性由下游
 * {@code OutputQualityGate} 复核（业务模型.md §4 / §7）。</p>
 *
 * @param attraction       景点详情（经 {@link AttractionCatalog} 解析，含真实评分）
 * @param mentionCount     被笔记提及的次数
 * @param positiveMentions 正面情感提及数
 * @param neutralMentions  中性情感提及数
 * @param negativeMentions 负面情感提及数
 */
public record AttractionCandidate(
        Attraction attraction,
        int mentionCount,
        int positiveMentions,
        int neutralMentions,
        int negativeMentions) {

    /**
     * 负面情感占比（{@code negativeMentions / mentionCount}），用于负面降权/剔除判定。
     *
     * @return 负面占比 [0,1]；无提及时返回 0
     */
    public double negativeRatio() {
        return mentionCount == 0 ? 0.0 : (double) negativeMentions / mentionCount;
    }
}
