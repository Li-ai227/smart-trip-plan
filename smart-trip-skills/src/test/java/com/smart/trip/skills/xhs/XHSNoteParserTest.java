package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.XHSNote;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link XHSNoteParser} 端口抽象契约测试（C-006 增量 B / T-9，AC-10）。
 *
 * <p>本测试只锁定「原始笔记 → 结构化（景点提及 + 逐提及情感）」的端口形态：{@code XHSNoteSource}
 * 退化为只取原文（{@code mentions} 为空），由独立 {@code XHSNoteParser} 抽取/链接/判情感并回填
 * {@link XHSNote#mentions()}。真实 LLM 实现（经 {@code GovernedExternalCaller} 治理）与降级编排为
 * 后续步骤，这里以手写测试替身验证端口契约与可插拔性。</p>
 */
class XHSNoteParserTest {

    /** 取原文的原始笔记：有标题/正文，但尚无结构化提及。 */
    private static XHSNote rawNote() {
        return new XHSNote("N1", "宽窄巷子好玩但锦里太挤", "成都古镇游记", List.of("古镇"), 520, List.of());
    }

    /**
     * 端口测试替身：按词表把正文中的景点名链接为 {@link AttractionMention} 并附情感。
     *
     * <p>仅用于固定端口契约——真实实现走 LLM 抽取，归后续步骤。</p>
     */
    private static XHSNoteParser dictionaryParser() {
        Map<String, AttractionMention> lexicon = Map.of(
                "宽窄巷子", new AttractionMention("A-KZ", SentimentType.POSITIVE),
                "锦里", new AttractionMention("A-JL", SentimentType.NEGATIVE));
        return raw -> {
            List<AttractionMention> mentions = lexicon.entrySet().stream()
                    .filter(e -> raw.title().contains(e.getKey()) || raw.content().contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .sorted((a, b) -> a.attractionId().compareTo(b.attractionId()))
                    .toList();
            return new XHSNote(raw.noteId(), raw.title(), raw.content(), raw.tags(),
                    raw.likeCount(), mentions);
        };
    }

    @Test
    @DisplayName("parse(rawNote)：抽取景点提及并回填逐提及情感（夸 A 吐槽 B 互不串扰）")
    void parseExtractsPerMentionSentiment() throws Exception {
        XHSNoteParser parser = dictionaryParser();

        XHSNote structured = parser.parse(rawNote());

        assertEquals("N1", structured.noteId(), "原文标识应透传");
        assertEquals(List.of(
                new AttractionMention("A-JL", SentimentType.NEGATIVE),
                new AttractionMention("A-KZ", SentimentType.POSITIVE)), structured.mentions());
    }

    @Test
    @DisplayName("无可链接景点：返回空提及（不伪造候选）")
    void parseWithNoLinkableAttractionsYieldsEmptyMentions() throws Exception {
        XHSNoteParser parser = dictionaryParser();

        XHSNote structured = parser.parse(
                new XHSNote("N2", "纯风景照", "没有提到任何景点名", List.of(), 3, List.of()));

        assertTrue(structured.mentions().isEmpty());
    }

    @Test
    @DisplayName("端口允许实现抛异常（解析失败由上层降级治理）")
    void parseMayThrowForUpstreamDegradation() {
        XHSNoteParser failing = raw -> {
            throw new TimeoutException("llm slow");
        };

        assertThrows(TimeoutException.class, () -> failing.parse(rawNote()));
    }
}
