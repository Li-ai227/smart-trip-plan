package com.smart.trip.common.model;

import com.smart.trip.common.util.CollectionUtils;
import java.util.List;

/**
 * 小红书笔记（分析输入，数据模型.md §2.5 / 业务模型.md §2）。
 *
 * <p>作为 XHSAnalysisAgent 的分析输入承载字段级契约：经检索得到的笔记原文由
 * {@code xhs-note-analysis} Skill 的 {@code XHSNoteParser} 解析为结构化提及，再聚合为候选
 * {@link Attraction}（C-006）。字段口径对齐数据模型.md §2.5；本模型仅承载契约，不含筛选/情感
 * 判定逻辑（评分阈值与情感筛选归 XHS Agent/Skill 内部，业务模型.md §4）。</p>
 *
 * <p>增量 B（AC-11）：情感由「整篇单值 {@code sentiment} + {@code mentionedAttractions} 列表」
 * 下沉为 {@code mentions: List<AttractionMention>}（逐提及情感），以表达「一篇夸 A 吐槽 B」。
 * 取原文的 {@code XHSNoteSource} 返回的原始笔记 {@code mentions} 为空，由 {@code XHSNoteParser}
 * 回填。</p>
 *
 * @param noteId    笔记 ID
 * @param title     标题
 * @param content   正文
 * @param tags      标签，可空（构造时归一为不可变空列表）
 * @param likeCount 点赞数
 * @param mentions  景点提及（含逐提及情感），可空（构造时归一为不可变空列表）
 */
public record XHSNote(
        String noteId,
        String title,
        String content,
        List<String> tags,
        int likeCount,
        List<AttractionMention> mentions) {

    /**
     * 紧凑构造器：对可空集合做 null 安全的不可变拷贝（决策 D-5/D-6）。
     */
    public XHSNote {
        tags = CollectionUtils.safeCopy(tags);
        mentions = CollectionUtils.safeCopy(mentions);
    }
}
