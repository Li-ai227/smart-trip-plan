package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.XHSNote;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SensitiveContentScreen} 单测：默认敏感/不安全笔记准入过滤（C-006 边界 #4 / NFR 安全）。
 *
 * <p>验证默认黑名单命中（标题/正文/标签任一）即拒绝，干净笔记放行，空内容不误伤。</p>
 */
class SensitiveContentScreenTest {

    private final SensitiveContentScreen screen = new SensitiveContentScreen();

    private static XHSNote note(String title, String content, List<String> tags) {
        return new XHSNote("N1", title, content, tags, 10,
                List.of(new AttractionMention("A-001", SentimentType.POSITIVE)));
    }

    @Test
    @DisplayName("干净笔记放行")
    void cleanNoteAdmitted() {
        assertTrue(screen.test(note("成都美食攻略", "宽窄巷子吃火锅", List.of("美食"))));
    }

    @Test
    @DisplayName("正文含敏感词 → 拒绝")
    void sensitiveContentRejected() {
        assertFalse(screen.test(note("标题", "代开发票联系微信", List.of("美食"))));
    }

    @Test
    @DisplayName("标题含敏感词 → 拒绝")
    void sensitiveTitleRejected() {
        assertFalse(screen.test(note("赌博攻略", "正文", List.of("美食"))));
    }

    @Test
    @DisplayName("标签含敏感词 → 拒绝")
    void sensitiveTagRejected() {
        assertFalse(screen.test(note("标题", "正文", List.of("色情"))));
    }

    @Test
    @DisplayName("匹配大小写不敏感（英文敏感词）")
    void caseInsensitiveMatch() {
        assertFalse(screen.test(note("Title", "buy FAKE invoice here", List.of())));
    }

    @Test
    @DisplayName("null 笔记不放行（防御式）")
    void nullNoteRejected() {
        assertFalse(screen.test(null));
    }

    @Test
    @DisplayName("空字段笔记放行（不误伤）")
    void blankFieldsAdmitted() {
        assertTrue(screen.test(note("", "", List.of())));
    }
}
