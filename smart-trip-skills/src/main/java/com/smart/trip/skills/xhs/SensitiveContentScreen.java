package com.smart.trip.skills.xhs;

import com.smart.trip.common.model.XHSNote;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * 默认笔记准入过滤：剔除含敏感/不安全内容的笔记（C-006 边界 #4 / NFR 安全）。
 *
 * <p>作为 {@link XHSNoteAnalyzer} 的缺省 {@link Predicate}，对标题/正文/标签做大小写不敏感的
 * 黑名单子串匹配，命中即拒绝（不进入候选聚合，防止污染推荐池）。本实现保守内置最小黑名单，
 * 真实词库/合规策略可经构造注入或在 C-015 接入外部词表后增强。</p>
 */
public final class SensitiveContentScreen implements Predicate<XHSNote> {

    /** 默认敏感词黑名单（中英文，最小集；命中即剔除）。 */
    static final List<String> DEFAULT_BLOCKLIST = List.of(
            "赌博", "色情", "代开发票", "假发票", "诈骗", "违禁", "fake invoice", "porn", "gambling");

    private final List<String> blocklist;

    /**
     * 以默认黑名单构造。
     */
    public SensitiveContentScreen() {
        this(DEFAULT_BLOCKLIST);
    }

    /**
     * 以自定义黑名单构造（便于扩展/测试）。
     *
     * @param blocklist 敏感词列表（大小写不敏感匹配），不可为 null
     */
    public SensitiveContentScreen(List<String> blocklist) {
        this.blocklist = List.copyOf(blocklist);
    }

    /**
     * 判定笔记是否准入（不含敏感内容）。
     *
     * @param note 待筛笔记
     * @return {@code true}=干净放行；{@code false}=命中黑名单或为 null（剔除）
     */
    @Override
    public boolean test(XHSNote note) {
        if (note == null) {
            return false;
        }
        String haystack = (nullSafe(note.title()) + ' ' + nullSafe(note.content()) + ' '
                + String.join(" ", note.tags())).toLowerCase(Locale.ROOT);
        for (String word : blocklist) {
            if (haystack.contains(word.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
