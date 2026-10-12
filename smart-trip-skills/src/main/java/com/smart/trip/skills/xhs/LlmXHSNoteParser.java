package com.smart.trip.skills.xhs;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.governance.CallResult;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RateLimiter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LLM 驱动的笔记解析器（C-006 增量 B / AC-10）：原始笔记 → LLM 实体抽取 → 链接到
 * {@link AttractionCatalog} → 逐提及情感，经 {@link GovernedExternalCaller} 统一治理。
 *
 * <p>解析流程：</p>
 * <ol>
 *   <li>拼接提示词（含笔记标题/正文/标签）送入 LLM（经统一治理：超时 30s / 重试 3 次）；</li>
 *   <li>解析 LLM JSON 响应得到 {@code [{ name, sentiment }]}；</li>
 *   <li>按 {@link AttractionCatalog#findByName} 链接为已知景点 ID——未知名称丢弃（不伪造候选）；</li>
 *   <li>回填 {@link XHSNote#mentions()} 并透传原文字段。</li>
 * </ol>
 *
 * <p>降级：LLM 持续超时/重试耗尽 → {@code GovernedExternalCaller} 的 fallback 返回空提及列表，
 * 笔记降级为「无结构化提及」并由上层跳过（不抛裸异常，不污染候选）。LLM 返回非法 JSON 同样
 * 降级为空提及（解析容错）。</p>
 */
public final class LlmXHSNoteParser implements XHSNoteParser {

    private static final String PROMPT_TEMPLATE =
            "请从以下小红书旅游笔记中抽取提到的景点名称及情感倾向。\n"
                    + "要求：以 JSON 数组返回，每个元素包含 name（景点名）和 sentiment（POSITIVE/NEUTRAL/NEGATIVE）。\n"
                    + "若无景点提及则返回空数组 []。只返回 JSON，不加任何解释。\n\n"
                    + "标题：%s\n正文：%s\n标签：%s";

    private final LlmClient llm;
    private final AttractionCatalog catalog;
    private final GovernedExternalCaller caller;

    /**
     * @param llm     LLM 调用端口（真实实现调用 DeepSeek/DashScope），非 null
     * @param catalog 景点目录端口（名称→Attraction 链接），非 null
     * @param caller  统一外部调用治理执行器（LLM 超时/重试/降级），非 null
     */
    public LlmXHSNoteParser(LlmClient llm, AttractionCatalog catalog, GovernedExternalCaller caller) {
        this.llm = llm;
        this.catalog = catalog;
        this.caller = caller;
    }

    @Override
    public XHSNote parse(XHSNote rawNote) throws Exception {
        CallResult<List<AttractionMention>> result = caller.call(
                RateLimiter.API_LLM,
                () -> doExtract(rawNote),
                cause -> List.of());
        return new XHSNote(rawNote.noteId(), rawNote.title(), rawNote.content(),
                rawNote.tags(), rawNote.likeCount(), result.value());
    }

    /**
     * 实际 LLM 抽取 + 解析 + 链接。
     */
    private List<AttractionMention> doExtract(XHSNote rawNote) throws Exception {
        String prompt = String.format(PROMPT_TEMPLATE,
                rawNote.title(), rawNote.content(), String.join(",", rawNote.tags()));
        String response = llm.complete(prompt);
        return parseAndLink(response);
    }

    /**
     * 解析 LLM JSON 响应并链接到目录。
     */
    @SuppressWarnings("unchecked")
    private List<AttractionMention> parseAndLink(String response) {
        List<Map<String, Object>> extractions;
        try {
            extractions = JsonUtils.fromJson(response, List.class);
        } catch (Exception ex) {
            return List.of();
        }
        if (extractions == null || extractions.isEmpty()) {
            return List.of();
        }
        List<AttractionMention> mentions = new ArrayList<>();
        for (Map<String, Object> entry : extractions) {
            String name = asString(entry.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            Optional<Attraction> resolved = catalog.findByName(name);
            if (resolved.isEmpty()) {
                continue;
            }
            SentimentType sentiment = parseSentiment(asString(entry.get("sentiment")));
            mentions.add(new AttractionMention(resolved.get().attractionId(), sentiment));
        }
        return mentions;
    }

    private static SentimentType parseSentiment(String raw) {
        if (raw == null || raw.isBlank()) {
            return SentimentType.NEUTRAL;
        }
        try {
            return SentimentType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SentimentType.NEUTRAL;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
