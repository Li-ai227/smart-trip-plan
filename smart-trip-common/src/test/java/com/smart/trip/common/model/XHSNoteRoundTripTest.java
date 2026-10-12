package com.smart.trip.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.util.JsonUtils;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link XHSNote} 字段契约与 JSON 往返测试（C-006 T-1 + 增量 B / T-8，对齐数据模型.md §2.5）。
 *
 * <p>守护小红书笔记分析输入的字段级契约：{@code noteId/title/content/tags/likeCount/mentions}
 * 往返一致；可空集合 null 安全归一为不可变空列表（与既有 {@link Attraction} 同口径，决策 D-5/D-6）。</p>
 *
 * <p>增量 B（AC-11）：情感由「整篇单值 + mentionedAttractions 列表」下沉为
 * {@code mentions: List<AttractionMention>}（逐提及情感），以表达「一篇夸 A 吐槽 B」。</p>
 */
class XHSNoteRoundTripTest {

    private static XHSNote sampleNote() {
        return new XHSNote(
                "N-001",
                "成都三日小众美食",
                "人少景美，本地人才知道的苍蝇馆子……",
                List.of("美食", "小众"),
                1280,
                List.of(
                        new AttractionMention("A-001", SentimentType.POSITIVE),
                        new AttractionMention("A-002", SentimentType.NEUTRAL)));
    }

    @Nested
    @DisplayName("JSON 往返保真")
    class RoundTrip {

        @Test
        @DisplayName("完整字段 round-trip 后完全一致")
        void fullRoundTrip() {
            XHSNote note = sampleNote();
            XHSNote back = JsonUtils.fromJson(JsonUtils.toJson(note), XHSNote.class);
            assertEquals(note, back);
        }

        @Test
        @DisplayName("逐提及情感（mentions）保真")
        void mentionsPreserved() {
            XHSNote back = JsonUtils.fromJson(JsonUtils.toJson(sampleNote()), XHSNote.class);
            assertEquals(List.of(
                    new AttractionMention("A-001", SentimentType.POSITIVE),
                    new AttractionMention("A-002", SentimentType.NEUTRAL)), back.mentions());
        }

        @Test
        @DisplayName("一篇笔记可分别承载正面/负面提及（夸 A 吐槽 B，AC-11 核心）")
        void mixedSentimentPerMention() {
            XHSNote note = new XHSNote("N-MIX", "宽窄巷子好玩但锦里太挤", "正文", List.of("古镇"), 520,
                    List.of(
                            new AttractionMention("A-KZ", SentimentType.POSITIVE),
                            new AttractionMention("A-JL", SentimentType.NEGATIVE)));

            XHSNote back = JsonUtils.fromJson(JsonUtils.toJson(note), XHSNote.class);

            assertEquals(note, back);
            assertEquals(SentimentType.POSITIVE, back.mentions().get(0).sentiment());
            assertEquals(SentimentType.NEGATIVE, back.mentions().get(1).sentiment());
        }
    }

    @Nested
    @DisplayName("可空集合 null 安全（决策 D-5/D-6）")
    class NullSafety {

        @Test
        @DisplayName("tags / mentions 为 null 归一为不可变空列表")
        void nullCollectionsNormalized() {
            XHSNote note = new XHSNote("N-002", "t", "c", null, 0, null);
            assertEquals(List.of(), note.tags());
            assertEquals(List.of(), note.mentions());
            assertThrows(UnsupportedOperationException.class, () -> note.tags().add("x"));
            assertThrows(UnsupportedOperationException.class,
                    () -> note.mentions().add(new AttractionMention("y", SentimentType.NEUTRAL)));
        }

        @Test
        @DisplayName("空集合笔记同样可 round-trip")
        void emptyCollectionsRoundTrip() {
            XHSNote note = new XHSNote("N-003", "t", "c", null, 5, null);
            XHSNote back = JsonUtils.fromJson(JsonUtils.toJson(note), XHSNote.class);
            assertEquals(note, back);
            assertTrue(back.tags().isEmpty());
            assertTrue(back.mentions().isEmpty());
        }
    }
}
