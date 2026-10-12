package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.model.XHSNote;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * FileBasedXHSNoteSource 集成测试（C-012-D fix）。
 *
 * <p>使用真实 {@link FileBasedAttractionCatalog}（从 classpath knowledge-base.json 加载），
 * 直接调用 {@link FileBasedXHSNoteSource#search(XHSAnalysisQuery)}（不经 GovernedExternalCaller），
 * 验证 prefix 提取 bug 修复后所有城市均能产出笔记。</p>
 *
 * <p><b>RED → GREEN 期望</b>：修复前，8 个多字符前缀城市（HRB/ZJJ/JZG/WLMQ/HHHT/DLN/SYC/WHI）
 * 测试 A/B 失败；修复后全绿。</p>
 */
class FileBasedXHSNoteSourceIntegrationTest {

    /** 多字符前缀（≥3 字符）的城市 — 受 substring(0,2) bug 影响的 8 个城市。 */
    private static final List<String> MULTI_CHAR_PREFIX_CITIES = List.of(
            "哈尔滨",   // HRB
            "张家界",   // ZJJ
            "九寨沟",   // JZG
            "乌鲁木齐", // WLMQ
            "呼和浩特", // HHHT
            "大连",     // DLN
            "沈阳",     // SYC
            "威海"      // WHI
    );

    /** knowledge-base.json 中全部 50 个城市（与 FileBasedXHSNoteSource.DESTINATION_PROBE 一致）。 */
    static Stream<String> allCitiesInKnowledgeBase() {
        return Stream.of(
                "北京", "上海", "广州", "深圳", "成都", "杭州", "南京", "西安", "重庆", "武汉",
                "长沙", "昆明", "丽江", "大理", "苏州", "厦门", "青岛", "三亚", "哈尔滨", "桂林",
                "贵阳", "珠海", "南宁", "海口", "大连", "洛阳", "敦煌", "张家界", "黄山", "九寨沟",
                "乌鲁木齐", "拉萨", "西宁", "呼和浩特", "银川", "济南", "太原", "合肥", "南昌", "兰州",
                "福州", "沈阳", "长春", "天津", "无锡", "扬州", "宁波", "绍兴", "泉州", "威海"
        );
    }

    private static FileBasedAttractionCatalog catalog;
    private static FileBasedXHSNoteSource source;

    @BeforeAll
    static void setUpOnce() {
        // 使用真实 knowledge-base.json，不 mock catalog
        catalog = new FileBasedAttractionCatalog();
        source = new FileBasedXHSNoteSource(catalog);
    }

    // ── Test A：多字符前缀城市 search() 返回 ≥1 笔记（RED before fix）──

    @ParameterizedTest(name = "Test A [{index}] {0} search() 返回 ≥1 条笔记")
    @ValueSource(strings = {"哈尔滨", "张家界", "九寨沟", "乌鲁木齐", "呼和浩特", "大连", "沈阳", "威海"})
    @DisplayName("Test A: 多字符前缀城市直接调用 search() 返回 ≥1 条笔记（不经 GovernedExternalCaller）")
    void multiCharPrefixCityReturnsNotes(String city) throws Exception {
        List<XHSNote> notes = source.search(XHSAnalysisQuery.of(city, List.of()));
        assertFalse(notes.isEmpty(),
                city + " 应返回 ≥1 条笔记，实际返回空列表（可能是 prefix substring(0,2) bug）");
        assertTrue(notes.size() <= 12,
                city + " 笔记数量应在 1–12 条，实际: " + notes.size());
    }

    // ── Test B：多字符前缀城市 mention ID 前缀正确（RED for mis-mapped cities）──

    @Test
    @DisplayName("Test B-1: 大连的笔记 mentions 以 DLN 开头（不混入大理 DL 的景点）")
    void dalianMentionsShouldHaveDlnPrefix() throws Exception {
        List<XHSNote> notes = source.search(XHSAnalysisQuery.of("大连", List.of()));
        assertFalse(notes.isEmpty(), "大连应返回笔记");
        notes.stream()
                .flatMap(n -> n.mentions().stream())
                .forEach(m -> assertTrue(m.attractionId().startsWith("DLN"),
                        "大连笔记的提及应以 DLN 开头，实际: " + m.attractionId()
                        + "（可能混入了大理 DL 的景点）"));
    }

    @Test
    @DisplayName("Test B-2: 沈阳的笔记 mentions 以 SYC 开头（不混入三亚 SY 的景点）")
    void shenyangMentionsShouldHaveSycPrefix() throws Exception {
        List<XHSNote> notes = source.search(XHSAnalysisQuery.of("沈阳", List.of()));
        assertFalse(notes.isEmpty(), "沈阳应返回笔记");
        notes.stream()
                .flatMap(n -> n.mentions().stream())
                .forEach(m -> assertTrue(m.attractionId().startsWith("SYC"),
                        "沈阳笔记的提及应以 SYC 开头，实际: " + m.attractionId()
                        + "（可能混入了三亚 SY 的景点）"));
    }

    @Test
    @DisplayName("Test B-3: 威海的笔记 mentions 以 WHI 开头（不混入武汉 WH 的景点）")
    void weihaiMentionsShouldHaveWhiPrefix() throws Exception {
        List<XHSNote> notes = source.search(XHSAnalysisQuery.of("威海", List.of()));
        assertFalse(notes.isEmpty(), "威海应返回笔记");
        notes.stream()
                .flatMap(n -> n.mentions().stream())
                .forEach(m -> assertTrue(m.attractionId().startsWith("WHI"),
                        "威海笔记的提及应以 WHI 开头，实际: " + m.attractionId()
                        + "（可能混入了武汉 WH 的景点）"));
    }

    @Test
    @DisplayName("Test B-4: 哈尔滨的笔记 mentions 以 HRB 开头")
    void harbinMentionsShouldHaveHrbPrefix() throws Exception {
        List<XHSNote> notes = source.search(XHSAnalysisQuery.of("哈尔滨", List.of()));
        assertFalse(notes.isEmpty(), "哈尔滨应返回笔记");
        notes.stream()
                .flatMap(n -> n.mentions().stream())
                .forEach(m -> assertTrue(m.attractionId().startsWith("HRB"),
                        "哈尔滨笔记的提及应以 HRB 开头，实际: " + m.attractionId()));
    }

    // ── Test C：全部 50 个城市均返回 ≥1 笔记（参数化，RED for 8 cities before fix）──

    @ParameterizedTest(name = "Test C [{index}] {0} 返回 ≥1 条笔记")
    @MethodSource("allCitiesInKnowledgeBase")
    @DisplayName("Test C: knowledge-base.json 中所有 50 个城市均能产出 ≥1 条笔记")
    void allCitiesInKnowledgeBaseReturnNotes(String city) throws Exception {
        List<XHSNote> notes = source.search(XHSAnalysisQuery.of(city, List.of()));
        assertFalse(notes.isEmpty(),
                "knowledge-base.json 城市「" + city + "」应返回 ≥1 条笔记，但返回空列表");
    }

    @ParameterizedTest(name = "C-012-E [{index}] {0} 识别为 {1}")
    @org.junit.jupiter.params.provider.CsvSource({
        "云南大理, 大理",
        "想去云南大理玩, 大理",
        "北京市, 北京"
    })
    @DisplayName("C-012-E: 意图式目的地可匹配真实 knowledge-base 城市并产出笔记")
    void intentDestinationReturnsNotesFromKnowledgeBase(String input, String expectedCity) throws Exception {
        List<XHSNote> notes = source.search(XHSAnalysisQuery.of(input, List.of("古镇", "美食")));

        assertFalse(notes.isEmpty(), input + " 应识别到知识库城市并返回笔记");
        notes.stream()
                .flatMap(n -> n.mentions().stream())
                .forEach(m -> assertEquals(expectedCity,
                        catalog.findById(m.attractionId()).orElseThrow().location().city(),
                        input + " 的提及应属于「" + expectedCity + "」，实际: " + m.attractionId()));
    }

    static Stream<Arguments> provinceIntentProvider() {
        return Stream.of(
                Arguments.of("云南", java.util.Set.of("大理", "昆明", "丽江")),
                Arguments.of("想去云南玩", java.util.Set.of("大理", "昆明", "丽江")),
                Arguments.of("四川", java.util.Set.of("成都", "九寨沟"))
        );
    }

    @ParameterizedTest(name = "C-012-E [{index}] {0} 覆盖多城市")
    @MethodSource("provinceIntentProvider")
    @DisplayName("C-012-E: 省份意图式目的地产出多城市笔记")
    void intentProvinceReturnsMultiCityNotes(String input, java.util.Set<String> expectedCities) throws Exception {
        List<XHSNote> notes = source.search(XHSAnalysisQuery.of(input, List.of("古镇", "美食")));

        assertFalse(notes.isEmpty(), input + " 应识别到知识库城市并返回笔记");
        notes.stream()
                .flatMap(n -> n.mentions().stream())
                .forEach(m -> {
                    String city = catalog.findById(m.attractionId()).orElseThrow().location().city();
                    assertTrue(expectedCities.contains(city),
                            input + " 的提及城市「" + city + "」应在 " + expectedCities + " 内");
                });
    }
}
