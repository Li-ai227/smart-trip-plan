package com.smart.trip.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AttractionMention} 字段契约与 JSON 往返测试（C-006 增量 B / T-8，AC-11）。
 *
 * <p>守护「逐提及情感」的字段级契约：{@code attractionId/sentiment} 往返一致。该不可变记录
 * 由 {@code XHSNoteParser} 产出、被 {@link XHSNote#mentions()} 承载，取代原「整篇单值 sentiment +
 * mentionedAttractions 列表」（数据模型.md §2.5）。</p>
 */
class AttractionMentionRoundTripTest {

    @Test
    @DisplayName("attractionId 与逐提及情感 round-trip 后完全一致")
    void roundTripPreservesFields() {
        AttractionMention mention = new AttractionMention("A-001", SentimentType.NEGATIVE);

        AttractionMention back =
                JsonUtils.fromJson(JsonUtils.toJson(mention), AttractionMention.class);

        assertEquals(mention, back);
        assertEquals("A-001", back.attractionId());
        assertEquals(SentimentType.NEGATIVE, back.sentiment());
    }
}
