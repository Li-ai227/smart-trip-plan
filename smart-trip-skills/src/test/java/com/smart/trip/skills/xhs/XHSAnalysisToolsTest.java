package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RetryPolicy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link XHSAnalysisTools} 确定性单测(C-006 增量 C)：验证把确定性分析暴露为 {@code @Tool} 后,
 * 业务不变量(评分≥3.5、负面剔除)与 {@code fallback} 语义不被绕过,且 {@link XHSAnalysisTools#lastResult()}
 * 与 {@link XHSNoteAnalyzer#analyze} 产物一致(权威候选来自确定性核心,不依赖 LLM)。
 *
 * <p>不涉及任何 LLM/HarnessAgent——本层只锁工具门面的确定性正确性。</p>
 */
class XHSAnalysisToolsTest {

    private static final String DEST = "成都";

    private final List<Long> sleeps = new ArrayList<>();
    private final Map<String, List<XHSNote>> sourceData = new HashMap<>();
    private final Map<String, Attraction> catalogData = new HashMap<>();

    private XHSAnalysisTools tools() {
        XHSNoteSource source = query -> sourceData.getOrDefault(query.destination(), List.of());
        XHSNoteCache cache = new XHSNoteCache() {
            @Override
            public Optional<List<XHSNote>> get(String destination) {
                return Optional.empty();
            }

            @Override
            public void put(String destination, List<XHSNote> notes) {
                // no-op
            }
        };
        AttractionCatalog catalog = id -> Optional.ofNullable(catalogData.get(id));
        GovernedExternalCaller caller =
                new GovernedExternalCaller(RetryPolicy.defaultPolicy(), sleeps::add);
        return new XHSAnalysisTools(new XHSNoteAnalyzer(source, cache, catalog, caller));
    }

    private void putAttraction(String id, double rating) {
        catalogData.put(id, new Attraction(id, "景点-" + id, new GeoLocation(0d, 0d, null, DEST),
                rating, 90, new BigDecimal("60"), List.of("热门")));
    }

    private static XHSNote note(String noteId, SentimentType sentiment, String... ids) {
        List<AttractionMention> mentions = Arrays.stream(ids)
                .map(id -> new AttractionMention(id, sentiment)).toList();
        return new XHSNote(noteId, "标题-" + noteId, "正文", List.of("美食"), 100, mentions);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        return JsonUtils.fromJson(json, Map.class);
    }

    @Test
    @DisplayName("构造器拒绝 null analyzer")
    void rejectsNullAnalyzer() {
        assertThrows(IllegalArgumentException.class, () -> new XHSAnalysisTools(null));
    }

    @Test
    @DisplayName("Happy Path：达标候选入选，JSON 与 lastResult 一致，fallback=false")
    void analyzeReturnsCandidates() {
        putAttraction("A-001", 4.6);
        putAttraction("A-002", 4.0);
        sourceData.put(DEST, List.of(
                note("N1", SentimentType.POSITIVE, "A-001", "A-002"),
                note("N2", SentimentType.POSITIVE, "A-001")));

        XHSAnalysisTools tools = tools();
        String json = tools.analyzeXhsNotes(DEST, "美食,小众");

        Map<String, Object> payload = parse(json);
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) payload.get("candidates");
        assertEquals(2, candidates.size());
        assertEquals("A-001", candidates.get(0).get("attractionId"));
        assertEquals(2, ((Number) candidates.get(0).get("mentionCount")).intValue());
        assertEquals(Boolean.FALSE, payload.get("fallback"));

        // 权威产物：lastResult 与确定性分析一致
        XHSAnalysisResult last = tools.lastResult();
        assertNotNull(last);
        assertFalse(last.fallback());
        assertEquals(2, last.candidates().size());
        assertEquals("A-001", last.candidates().get(0).attraction().attractionId());
    }

    @Test
    @DisplayName("不变量：评分 <3.5 的景点不入选（工具不得放宽阈值）")
    void belowThresholdExcluded() {
        putAttraction("A-OK", 4.5);
        putAttraction("A-LOW", 3.4);
        sourceData.put(DEST, List.of(
                note("N1", SentimentType.POSITIVE, "A-OK"),
                note("N2", SentimentType.POSITIVE, "A-LOW")));

        XHSAnalysisTools tools = tools();
        tools.analyzeXhsNotes(DEST, null);

        List<AttractionCandidate> candidates = tools.lastResult().candidates();
        assertEquals(1, candidates.size());
        assertEquals("A-OK", candidates.get(0).attraction().attractionId());
    }

    @Test
    @DisplayName("不变量：负面情感占多数的景点被剔除（即便评分达标）")
    void negativeMajorityExcluded() {
        putAttraction("A-NEG", 4.8);
        sourceData.put(DEST, List.of(
                note("N1", SentimentType.NEGATIVE, "A-NEG"),
                note("N2", SentimentType.NEGATIVE, "A-NEG"),
                note("N3", SentimentType.POSITIVE, "A-NEG")));

        XHSAnalysisTools tools = tools();
        tools.analyzeXhsNotes(DEST, "");

        assertTrue(tools.lastResult().candidates().isEmpty());
    }

    @Test
    @DisplayName("降级：检索为空 → 空候选 + fallback=true（语义透传到 JSON）")
    void emptySearchFallback() {
        XHSAnalysisTools tools = tools();
        String json = tools.analyzeXhsNotes("无人之境", null);

        Map<String, Object> payload = parse(json);
        assertEquals(Boolean.TRUE, payload.get("fallback"));
        assertTrue(((List<?>) payload.get("candidates")).isEmpty());
        assertTrue(tools.lastResult().fallback());
    }

    @Test
    @DisplayName("preferences 解析：null / 空白 / 逗号分隔均稳健（不抛异常）")
    void preferencesParsedRobustly() {
        putAttraction("A-001", 4.6);
        sourceData.put(DEST, List.of(note("N1", SentimentType.POSITIVE, "A-001")));

        XHSAnalysisTools tools = tools();
        // 三种偏好形态都应正常产出同一候选
        for (String prefs : Arrays.asList(null, "  ", "美食, ,小众")) {
            String json = tools.analyzeXhsNotes(DEST, prefs);
            assertFalse((Boolean) parse(json).get("fallback"));
            assertEquals(1, tools.lastResult().candidates().size());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("C-012-E: 工具层支持目的地自然语言并返回非降级候选")
    void analyzeXhsNotesRecognizesIntentDestination() {
        FileBasedAttractionCatalog catalog = new FileBasedAttractionCatalog();
        XHSNoteSource source = new FileBasedXHSNoteSource(catalog);
        XHSNoteCache cache = new XHSNoteCache() {
            @Override
            public Optional<List<XHSNote>> get(String destination) {
                return Optional.empty();
            }

            @Override
            public void put(String destination, List<XHSNote> notes) {
                // no-op
            }
        };
        GovernedExternalCaller caller = new GovernedExternalCaller(new RetryPolicy(0, 1L), millis -> { });
        XHSAnalysisTools tools = new XHSAnalysisTools(new XHSNoteAnalyzer(source, cache, catalog, caller));

        String json = tools.analyzeXhsNotes("云南大理", "古镇,美食");
        Map<String, Object> payload = parse(json);
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) payload.get("candidates");

        assertEquals(Boolean.FALSE, payload.get("fallback"));
        assertFalse(candidates.isEmpty(), "云南大理 应识别到大理并返回候选");
        assertNotNull(tools.lastResult());
        assertFalse(tools.lastResult().candidates().isEmpty());
        assertTrue(tools.lastResult().candidates().stream()
                .allMatch(c -> "大理".equals(c.attraction().location().city())),
                "候选景点均应来自大理");
    }
}
