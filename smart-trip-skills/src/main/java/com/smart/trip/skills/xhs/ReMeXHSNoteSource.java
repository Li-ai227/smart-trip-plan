package com.smart.trip.skills.xhs;

import com.smart.trip.common.model.XHSNote;
import java.util.List;

/**
 * ReMe 知识库笔记召回适配壳（C-006 增量 B / AC-9）。
 *
 * <p>端口就位（实现 {@link XHSNoteSource}），保证端口契约对齐与可替换。
 * <b>真实向量召回依赖 C-015（ReMe 记忆/RAG）</b>，本卡仅做占位——始终返回空列表，
 * 由调用方降级路径兜底（{@code fallback=true}）。</p>
 */
public final class ReMeXHSNoteSource implements XHSNoteSource {

    @Override
    public List<XHSNote> search(XHSAnalysisQuery query) {
        return List.of();
    }
}
