package com.smart.trip.skills.xhs;

import com.smart.trip.common.model.XHSNote;

/**
 * 笔记解析端口（C-006 增量 B / AC-10）：原始笔记 → 结构化笔记（景点提及 + 逐提及情感）。
 *
 * <p>把「文本 → 景点链接 + 情感判定」这一最难的分析步从输入假设转为可治理的代码产出。职责与
 * 取原文分离：{@link XHSNoteSource} 仅返回原始笔记（{@code mentions} 为空），本端口完成 LLM 实体
 * 抽取 + 链接到 {@link AttractionCatalog} + 逐提及情感判定，回填
 * {@link XHSNote#mentions()}。</p>
 *
 * <p>真实实现对 LLM 的调用经
 * {@link com.smart.trip.skills.governance.GovernedExternalCaller} 统一治理（超时 30s/重试 3 次/
 * 降级）。实现可因超时/故障抛任意异常，由上层施加降级：解析失败的笔记降级为「无结构化提及」并
 * 跳过，不污染候选、不抛裸异常（绝不静默挂起，接口协议.md §2）。</p>
 */
@FunctionalInterface
public interface XHSNoteParser {

    /**
     * 解析单条原始笔记为结构化笔记。
     *
     * @param rawNote 取原文得到的原始笔记（{@code mentions} 通常为空）
     * @return 结构化笔记：透传原文标识/文本，回填 {@link XHSNote#mentions()}（含逐提及情感）
     * @throws Exception 当解析失败/超时（驱动上层降级：该笔记降级为无结构化提及并跳过）
     */
    XHSNote parse(XHSNote rawNote) throws Exception;
}
