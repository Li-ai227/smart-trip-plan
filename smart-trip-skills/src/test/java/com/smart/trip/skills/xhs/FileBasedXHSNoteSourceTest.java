package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.XHSNote;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link FileBasedXHSNoteSource} 确定性单测（C-012-D AC-D4/AC-D5）：
 * 验证从 AttractionCatalog 生成合成笔记的正确性。
 */
class FileBasedXHSNoteSourceTest {

    private static final Map<String, Attraction> TEST_DATA = Map.of(
            "BJ001", new Attraction("BJ001", "故宫博物院",
                    new GeoLocation(116.397, 39.916, "北京市东城区", "北京"),
                    4.8, 240, new BigDecimal("60"), List.of("历史文化")),
            "BJ002", new Attraction("BJ002", "长城",
                    new GeoLocation(116.570, 40.431, "北京市怀柔区", "北京"),
                    4.7, 300, new BigDecimal("45"), List.of("历史文化")),
            "BJ003", new Attraction("BJ003", "天坛",
                    new GeoLocation(116.407, 39.882, "北京市东城区", "北京"),
                    4.6, 120, new BigDecimal("34"), List.of("文化")),
            "BJ004", new Attraction("BJ004", "颐和园",
                    new GeoLocation(116.275, 39.995, "北京市海淀区", "北京"),
                    4.7, 180, new BigDecimal("30"), List.of("园林")),
            "BJ005", new Attraction("BJ005", "天安门广场",
                    new GeoLocation(116.397, 39.905, "北京市东城区", "北京"),
                    4.5, 60, new BigDecimal("0"), List.of("爱国主义")),
            "CD001", new Attraction("CD001", "大熊猫繁育研究基地",
                    new GeoLocation(104.143, 30.733, "成都市成华区", "成都"),
                    4.9, 180, new BigDecimal("55"), List.of("动物")),
            "CD002", new Attraction("CD002", "宽窄巷子",
                    new GeoLocation(104.058, 30.664, "成都市青羊区", "成都"),
                    4.4, 120, new BigDecimal("0"), List.of("美食"))
    );

    /** 同时支持 findById / findByName / findByCity 的测试目录。 */
    private static final AttractionCatalog TEST_CATALOG = new AttractionCatalog() {
        @Override
        public Optional<Attraction> findById(String attractionId) {
            return Optional.ofNullable(TEST_DATA.get(attractionId));
        }

        @Override
        public Optional<Attraction> findByName(String name) {
            return TEST_DATA.values().stream()
                    .filter(a -> a.name().equals(name))
                    .findFirst();
        }

        @Override
        public List<Attraction> findByCity(String city) {
            return TEST_DATA.values().stream()
                    .filter(a -> city.equals(a.location().city()))
                    .toList();
        }
    };

    private AttractionCatalog testCatalog;

    @BeforeEach
    void setUp() {
        testCatalog = TEST_CATALOG;
    }

    @Test
    @DisplayName("AC-D4: search('北京', ...) 返回 ≥1 条笔记，每条 mentions 非空且 ID 可解析")
    void beijingReturnsNotesWithResolvableMentions() throws Exception {
        FileBasedXHSNoteSource source = new FileBasedXHSNoteSource(testCatalog);
        XHSAnalysisQuery query = XHSAnalysisQuery.of("北京", List.of("文化"));

        List<XHSNote> notes = source.search(query);

        assertFalse(notes.isEmpty(), "北京应返回至少 1 条笔记");
        assertTrue(notes.size() <= 12, "笔记数量应在 4–12 条范围内");
        for (XHSNote note : notes) {
            assertNotNull(note.noteId(), "笔记 ID 不应为 null");
            assertFalse(note.mentions().isEmpty(), "每条笔记的 mentions 不应为空");
            for (var mention : note.mentions()) {
                assertTrue(mention.attractionId().startsWith("BJ"),
                        "北京笔记的提及应为 BJ 开头的景点 ID，实际为: " + mention.attractionId());
                assertTrue(testCatalog.findById(mention.attractionId()).isPresent(),
                        "提及的景点 ID " + mention.attractionId() + " 应可被 catalog 解析");
            }
        }
    }

    @Test
    @DisplayName("AC-D4: search('成都', ...) 返回笔记，提及的景点 ID 可解析")
    void chengduReturnsNotesWithResolvableMentions() throws Exception {
        FileBasedXHSNoteSource source = new FileBasedXHSNoteSource(testCatalog);
        XHSAnalysisQuery query = XHSAnalysisQuery.of("成都", List.of());

        List<XHSNote> notes = source.search(query);

        assertFalse(notes.isEmpty(), "成都应返回至少 1 条笔记");
        for (XHSNote note : notes) {
            for (var mention : note.mentions()) {
                assertTrue(testCatalog.findById(mention.attractionId()).isPresent(),
                        "成都笔记提及的景点 ID " + mention.attractionId() + " 应可被解析");
            }
        }
    }

    @Test
    @DisplayName("AC-D5: 未知城市返回空列表（降级行为同 query -> List.of()）")
    void unknownCityReturnsEmpty() throws Exception {
        FileBasedXHSNoteSource source = new FileBasedXHSNoteSource(testCatalog);
        XHSAnalysisQuery query = XHSAnalysisQuery.of("未知城市_不存在", List.of());

        List<XHSNote> notes = source.search(query);

        assertTrue(notes.isEmpty(), "未知城市应返回空列表");
    }

    @Test
    @DisplayName("笔记标题包含目的地名称")
    void noteTitleContainsDestination() throws Exception {
        FileBasedXHSNoteSource source = new FileBasedXHSNoteSource(testCatalog);
        XHSAnalysisQuery query = XHSAnalysisQuery.of("北京", List.of());

        List<XHSNote> notes = source.search(query);

        assertFalse(notes.isEmpty());
        for (XHSNote note : notes) {
            assertTrue(note.title().contains("北京"),
                    "笔记标题应包含目的地名称「北京」，实际: " + note.title());
        }
    }

    @Test
    @DisplayName("生成笔记的 likeCount 全部为正数")
    void likeCountsArePositive() throws Exception {
        FileBasedXHSNoteSource source = new FileBasedXHSNoteSource(testCatalog);
        XHSAnalysisQuery query = XHSAnalysisQuery.of("北京", List.of());

        List<XHSNote> notes = source.search(query);

        assertFalse(notes.isEmpty());
        for (XHSNote note : notes) {
            assertTrue(note.likeCount() > 0, "笔记点赞数应为正数，实际: " + note.likeCount());
        }
    }

    @Test
    @DisplayName("笔记的 negativeRatio 整体 < 0.5（确保 XHSNoteAnalyzer 筛选通过）")
    void negativeRatioBelowThreshold() throws Exception {
        FileBasedXHSNoteSource source = new FileBasedXHSNoteSource(testCatalog);
        XHSAnalysisQuery query = XHSAnalysisQuery.of("北京", List.of());

        List<XHSNote> notes = source.search(query);

        assertFalse(notes.isEmpty());
        long positiveMentions = notes.stream()
                .flatMap(n -> n.mentions().stream())
                .filter(m -> m.sentiment() == com.smart.trip.common.enums.SentimentType.POSITIVE)
                .count();
        long negativeMentions = notes.stream()
                .flatMap(n -> n.mentions().stream())
                .filter(m -> m.sentiment() == com.smart.trip.common.enums.SentimentType.NEGATIVE)
                .count();

        long total = positiveMentions + negativeMentions;
        if (total > 0) {
            double negativeRatio = (double) negativeMentions / total;
            assertTrue(negativeRatio < 0.5,
                    "负面情感占比应 < 0.5，实际: " + negativeRatio);
        }
    }

    @Test
    @DisplayName("产生的笔记可通过 XHSNoteAnalyzer.analyze() 完整链路（集成验证）")
    void notesPassThroughXhsNoteAnalyzer() {
        FileBasedXHSNoteSource source = new FileBasedXHSNoteSource(testCatalog);
        XHSAnalysisQuery query = XHSAnalysisQuery.of("北京", List.of("文化"));

        // 模拟 XHSNoteAnalyzer 调用
        XHSNoteAnalyzer analyzer = new XHSNoteAnalyzer(
                source,
                new XHSNoteCache() {
                    @Override
                    public java.util.Optional<List<XHSNote>> get(String destination) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public void put(String destination, List<XHSNote> notes) {
                    }
                },
                testCatalog,
                new com.smart.trip.skills.governance.GovernedExternalCaller(
                        new com.smart.trip.skills.governance.RetryPolicy(0, 1L),
                        com.smart.trip.skills.governance.Sleeper.real(),
                        com.smart.trip.skills.governance.RateLimiter.withDefaults(
                                new com.smart.trip.skills.governance.InMemoryRateLimitStore())));

        XHSAnalysisResult result = analyzer.analyze(query);

        // 应产生候选景点（非空）
        assertFalse(result.candidates().isEmpty(),
                "FileBasedXHSNoteSource + FileBasedAttractionCatalog 的笔记应产出候选景点");
        // 不应为 fallback（数据完整）
        assertFalse(result.fallback(),
                "已知城市的正常数据不应标记为 fallback");
        // 候选景点评分应 ≥ 3.5（业务不变量）
        for (var candidate : result.candidates()) {
            assertTrue(candidate.attraction().rating() >= 3.5,
                    "候选景点评分应 ≥ 3.5，实际: " + candidate.attraction().rating());
        }
    }
}
