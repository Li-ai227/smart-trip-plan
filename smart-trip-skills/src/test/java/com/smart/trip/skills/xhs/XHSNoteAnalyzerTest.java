package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.InMemoryRateLimitStore;
import com.smart.trip.skills.governance.RateLimitRule;
import com.smart.trip.skills.governance.RateLimiter;
import com.smart.trip.skills.governance.RetryPolicy;
import com.smart.trip.skills.governance.Sleeper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link XHSNoteAnalyzer} 单测：检索→聚合→候选筛选→降级（C-006 AC-2~AC-6 / 验收用例 Case-1~4）。
 *
 * <p>遵循 TDD：先红后绿。覆盖评分阈值（≥3.5）、负面情感剔除、提及频次排序、空检索/检索故障/
 * 限频三类降级（{@code fallback=true} 不静默移除），以及「全部&lt;3.5」与降级的语义区分。
 * 退避用记录式 {@link Sleeper} 不真实阻塞（测试确定性）。</p>
 */
class XHSNoteAnalyzerTest {

    private static final String DEST = "成都";

    // ---- 测试替身（桩） ----

    /**
     * 可编排的检索源：返回脚本结果或抛指定异常，并记录调用次数。
     *
     * <p>{@code failuresBeforeSuccess} 支持「瞬时故障 N 次后恢复」场景（超时重试回归）：
     * 前 N 次抛 {@code failure}，第 N+1 次返回 {@code result}；为 0 时按 {@code failure}
     * 是否存在决定持续故障或持续成功（向后兼容既有用例）。</p>
     */
    private static final class ScriptedSource implements XHSNoteSource {
        private final AtomicInteger calls = new AtomicInteger();
        private List<XHSNote> result = List.of();
        private Exception failure;
        private int failuresBeforeSuccess;

        @Override
        public List<XHSNote> search(XHSAnalysisQuery query) throws Exception {
            int attempt = calls.incrementAndGet();
            if (failuresBeforeSuccess > 0) {
                if (attempt <= failuresBeforeSuccess) {
                    throw failure != null ? failure : new TimeoutException("transient");
                }
                return result;
            }
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    /** 内存笔记缓存桩。 */
    private static final class MapCache implements XHSNoteCache {
        private final Map<String, List<XHSNote>> store = new HashMap<>();

        @Override
        public Optional<List<XHSNote>> get(String destination) {
            return Optional.ofNullable(store.get(destination));
        }

        @Override
        public void put(String destination, List<XHSNote> notes) {
            store.put(destination, List.copyOf(notes));
        }
    }

    /** 内存景点目录桩（ID→Attraction）。 */
    private static final class MapCatalog implements AttractionCatalog {
        private final Map<String, Attraction> store = new HashMap<>();

        MapCatalog put(String id, double rating) {
            store.put(id, attraction(id, rating));
            return this;
        }

        @Override
        public Optional<Attraction> findById(String attractionId) {
            return Optional.ofNullable(store.get(attractionId));
        }
    }

    // ---- 测试夹具 ----

    private final List<Long> sleeps = new ArrayList<>();
    private final ScriptedSource source = new ScriptedSource();
    private final MapCache cache = new MapCache();
    private final MapCatalog catalog = new MapCatalog();

    private GovernedExternalCaller caller() {
        return new GovernedExternalCaller(RetryPolicy.defaultPolicy(), sleeps::add);
    }

    private XHSNoteAnalyzer analyzer() {
        return new XHSNoteAnalyzer(source, cache, catalog, caller());
    }

    private XHSNoteAnalyzer analyzerWithParser(XHSNoteParser parser) {
        return new XHSNoteAnalyzer(source, cache, catalog, caller(), new SensitiveContentScreen(),
                parser);
    }

    private static Attraction attraction(String id, double rating) {
        return new Attraction(id, "景点-" + id, new GeoLocation(0d, 0d, null, DEST),
                rating, 90, new BigDecimal("60"), List.of("热门"));
    }

    private static XHSNote note(String noteId, SentimentType sentiment, String... mentions) {
        List<AttractionMention> perMention = Arrays.stream(mentions)
                .map(id -> new AttractionMention(id, sentiment))
                .toList();
        return new XHSNote(noteId, "标题-" + noteId, "正文", List.of("美食"), 100, perMention);
    }

    private static XHSAnalysisQuery query() {
        return XHSAnalysisQuery.of(DEST, List.of("美食", "小众"));
    }

    @Nested
    @DisplayName("Happy Path：候选聚合 + 阈值 + 频次排序（Case-1）")
    class HappyPath {

        @Test
        @DisplayName("返回评分≥3.5 候选，按提及频次降序，fallback=false")
        void candidatesAboveThresholdSortedByFrequency() {
            catalog.put("A-001", 4.6).put("A-002", 4.0);
            // A-001 提及 3 次，A-002 提及 1 次
            source.result = List.of(
                    note("N1", SentimentType.POSITIVE, "A-001", "A-002"),
                    note("N2", SentimentType.POSITIVE, "A-001"),
                    note("N3", SentimentType.NEUTRAL, "A-001"));

            XHSAnalysisResult result = analyzer().analyze(query());

            assertFalse(result.fallback());
            assertEquals(2, result.candidates().size());
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
            assertEquals(3, result.candidates().get(0).mentionCount());
            assertEquals("A-002", result.candidates().get(1).attraction().attractionId());
        }
    }

    @Nested
    @DisplayName("候选筛选：评分阈值 + 负面情感（AC-4/AC-5 / Case-4）")
    class Filtering {

        @Test
        @DisplayName("评分 < 3.5 的景点不进入推荐池")
        void belowRatingThresholdExcluded() {
            catalog.put("A-001", 4.5).put("A-LOW", 3.4);
            source.result = List.of(
                    note("N1", SentimentType.POSITIVE, "A-001"),
                    note("N2", SentimentType.POSITIVE, "A-LOW"));

            XHSAnalysisResult result = analyzer().analyze(query());

            assertEquals(1, result.candidates().size());
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
        }

        @Test
        @DisplayName("负面情感占多数的景点被剔除（即便评分达标）")
        void negativeSentimentMajorityExcluded() {
            catalog.put("A-NEG", 4.8);
            source.result = List.of(
                    note("N1", SentimentType.NEGATIVE, "A-NEG"),
                    note("N2", SentimentType.NEGATIVE, "A-NEG"),
                    note("N3", SentimentType.POSITIVE, "A-NEG"));

            XHSAnalysisResult result = analyzer().analyze(query());

            assertTrue(result.candidates().isEmpty());
            assertFalse(result.fallback());
        }

        @Test
        @DisplayName("未知景点 ID（目录无解析）被跳过，不污染候选")
        void unknownAttractionIdSkipped() {
            catalog.put("A-001", 4.2);
            source.result = List.of(note("N1", SentimentType.POSITIVE, "A-001", "A-UNKNOWN"));

            XHSAnalysisResult result = analyzer().analyze(query());

            assertEquals(1, result.candidates().size());
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
        }

        @Test
        @DisplayName("全部候选 < 3.5：空推荐池且 fallback=false（质量不足，非降级）")
        void allBelowThresholdIsQualityNotFallback() {
            catalog.put("A-L1", 3.0).put("A-L2", 2.5);
            source.result = List.of(
                    note("N1", SentimentType.POSITIVE, "A-L1"),
                    note("N2", SentimentType.POSITIVE, "A-L2"));

            XHSAnalysisResult result = analyzer().analyze(query());

            assertTrue(result.candidates().isEmpty());
            assertFalse(result.fallback());
            assertTrue(result.message() != null && result.message().contains("质量"));
        }
    }

    @Nested
    @DisplayName("笔记过滤：空提及 / 敏感内容（边界 #4）")
    class NoteScreening {

        @Test
        @DisplayName("mentions 为空的笔记被跳过")
        void emptyMentionNoteSkipped() {
            catalog.put("A-001", 4.2);
            source.result = List.of(
                    note("N-EMPTY", SentimentType.POSITIVE),
                    note("N1", SentimentType.POSITIVE, "A-001"));

            XHSAnalysisResult result = analyzer().analyze(query());

            assertEquals(1, result.candidates().size());
        }

        @Test
        @DisplayName("注入准入断言可剔除敏感笔记（不污染候选）")
        void sensitiveNoteFilteredByAdmission() {
            catalog.put("A-001", 4.2).put("A-BAD", 4.9);
            XHSNote bad = note("N-BAD", SentimentType.POSITIVE, "A-BAD");
            source.result = List.of(bad, note("N1", SentimentType.POSITIVE, "A-001"));

            XHSNoteAnalyzer analyzer = new XHSNoteAnalyzer(source, cache, catalog, caller(),
                    n -> !"N-BAD".equals(n.noteId()));
            XHSAnalysisResult result = analyzer.analyze(query());

            assertEquals(1, result.candidates().size());
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
        }
    }

    @Nested
    @DisplayName("降级路径：空检索 / 检索故障 / 限频（AC-6 / 边界 #1#2 / Case-2#3）")
    class Degradation {

        @Test
        @DisplayName("检索为空：空候选 + fallback=true + 可读说明（Case-3）")
        void emptySearchReturnsFallbackTrue() {
            source.result = List.of();

            XHSAnalysisResult result = analyzer().analyze(query());

            assertTrue(result.candidates().isEmpty());
            assertTrue(result.fallback());
            assertTrue(result.message() != null && !result.message().isBlank());
        }

        @Test
        @DisplayName("检索持续故障：重试耗尽 → 走缓存 → fallback=true（Case-2）")
        void sourceFailureFallsBackToCache() {
            catalog.put("A-001", 4.6);
            cache.put(DEST, List.of(note("C1", SentimentType.POSITIVE, "A-001")));
            source.failure = new TimeoutException("slow");

            XHSAnalysisResult result = analyzer().analyze(query());

            assertTrue(result.fallback());
            assertEquals(1, result.candidates().size());
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
            // 默认重试 3 次：共 4 次尝试
            assertEquals(4, source.calls.get());
        }

        @Test
        @DisplayName("检索故障且无缓存：空候选 + fallback=true，不抛裸异常")
        void sourceFailureNoCacheReturnsEmptyFallback() {
            source.failure = new TimeoutException("slow");

            XHSAnalysisResult result = analyzer().analyze(query());

            assertTrue(result.candidates().isEmpty());
            assertTrue(result.fallback());
        }

        @Test
        @DisplayName("检索源返回 null：按空结果处理，空候选 + fallback=true（边界 #1 空结果）")
        void nullSourceResultTreatedAsEmpty() {
            source.result = null;

            XHSAnalysisResult result = analyzer().analyze(query());

            assertTrue(result.candidates().isEmpty());
            assertTrue(result.fallback());
            assertEquals(1, source.calls.get(), "成功返回 null 不应触发重试");
        }

        @Test
        @DisplayName("故障且缓存为空列表：视为未命中（MISS）+ 空候选 + fallback=true")
        void emptyCachedListIsMissNotHit() {
            cache.put(DEST, List.of());
            source.failure = new TimeoutException("slow");

            XHSAnalysisResult result = analyzer().analyze(query());

            assertTrue(result.candidates().isEmpty());
            assertTrue(result.fallback());
            assertEquals(CacheOutcome.MISS, result.telemetry().cacheOutcome());
        }

        @Test
        @DisplayName("瞬时超时 2 次后恢复：重试成功 → fallback=false、不咨询缓存、回填缓存（超时回归）")
        void transientTimeoutRecoversWithoutFallback() {
            catalog.put("A-001", 4.6);
            source.failuresBeforeSuccess = 2;
            source.failure = new TimeoutException("transient");
            source.result = List.of(note("N1", SentimentType.POSITIVE, "A-001"));

            XHSAnalysisResult result = analyzer().analyze(query());

            assertFalse(result.fallback(), "重试内恢复不应降级");
            assertEquals(CacheOutcome.NOT_CONSULTED, result.telemetry().cacheOutcome());
            assertEquals(1, result.candidates().size());
            // 2 次失败 + 第 3 次成功
            assertEquals(3, source.calls.get());
            // 退避两次：1s、2s（接口协议 §4 指数退避）
            assertEquals(List.of(1_000L, 2_000L), sleeps);
            // 实时成功 → 缓存被回填为最新
            assertEquals(List.of("N1"),
                    cache.get(DEST).orElseThrow().stream().map(XHSNote::noteId).toList());
        }

        @Test
        @DisplayName("命中限频：绝不调用检索源，走缓存降级 fallback=true（边界 #2）")
        void rateLimitedDoesNotCallSourceUsesCache() {
            catalog.put("A-001", 4.6);
            cache.put(DEST, List.of(note("C1", SentimentType.POSITIVE, "A-001")));
            // 额度 1/分钟，预先耗尽 → 分析时必命中限频
            InMemoryRateLimitStore store = new InMemoryRateLimitStore();
            RateLimiter limiter = new RateLimiter(store,
                    Map.of(RateLimiter.API_XHS, new RateLimitRule(RateLimiter.API_XHS, 1, 60L)));
            limiter.acquireOrThrow(RateLimiter.API_XHS);
            GovernedExternalCaller limitedCaller =
                    new GovernedExternalCaller(RetryPolicy.defaultPolicy(), sleeps::add, limiter);

            XHSNoteAnalyzer analyzer = new XHSNoteAnalyzer(source, cache, catalog, limitedCaller);
            XHSAnalysisResult result = analyzer.analyze(query());

            assertTrue(result.fallback());
            assertEquals(0, source.calls.get(), "限频命中绝不可调用检索源（不超频）");
            assertEquals(1, result.candidates().size());
        }
    }

    @Nested
    @DisplayName("可观测遥测：限频命中 + 缓存命中/未命中（AC-7 / 接口协议 §6）")
    class Telemetry {

        @Test
        @DisplayName("实时检索成功：缓存未咨询（NOT_CONSULTED）、未限频")
        void liveSuccessNotConsulted() {
            catalog.put("A-001", 4.6);
            source.result = List.of(note("N1", SentimentType.POSITIVE, "A-001"));

            XHSAnalysisResult result = analyzer().analyze(query());

            assertFalse(result.telemetry().rateLimited());
            assertEquals(CacheOutcome.NOT_CONSULTED, result.telemetry().cacheOutcome());
        }

        @Test
        @DisplayName("检索故障且缓存有数据：缓存命中（HIT）、未限频")
        void sourceFailureCacheHit() {
            catalog.put("A-001", 4.6);
            cache.put(DEST, List.of(note("C1", SentimentType.POSITIVE, "A-001")));
            source.failure = new TimeoutException("slow");

            XHSAnalysisResult result = analyzer().analyze(query());

            assertFalse(result.telemetry().rateLimited());
            assertEquals(CacheOutcome.HIT, result.telemetry().cacheOutcome());
        }

        @Test
        @DisplayName("检索故障且无缓存：缓存未命中（MISS）")
        void sourceFailureCacheMiss() {
            source.failure = new TimeoutException("slow");

            XHSAnalysisResult result = analyzer().analyze(query());

            assertEquals(CacheOutcome.MISS, result.telemetry().cacheOutcome());
        }

        @Test
        @DisplayName("命中限频：rateLimited=true 且走缓存命中（HIT），绝不调用检索源")
        void rateLimitedFlaggedWithCacheHit() {
            catalog.put("A-001", 4.6);
            cache.put(DEST, List.of(note("C1", SentimentType.POSITIVE, "A-001")));
            InMemoryRateLimitStore store = new InMemoryRateLimitStore();
            RateLimiter limiter = new RateLimiter(store,
                    Map.of(RateLimiter.API_XHS, new RateLimitRule(RateLimiter.API_XHS, 1, 60L)));
            limiter.acquireOrThrow(RateLimiter.API_XHS);
            GovernedExternalCaller limitedCaller =
                    new GovernedExternalCaller(RetryPolicy.defaultPolicy(), sleeps::add, limiter);

            XHSNoteAnalyzer analyzer = new XHSNoteAnalyzer(source, cache, catalog, limitedCaller);
            XHSAnalysisResult result = analyzer.analyze(query());

            assertTrue(result.telemetry().rateLimited());
            assertEquals(CacheOutcome.HIT, result.telemetry().cacheOutcome());
            assertEquals(0, source.calls.get());
        }
    }

    @Nested
    @DisplayName("缓存刷新：实时成功后回填（边界 #5 实时优先）")
    class CacheRefresh {

        @Test
        @DisplayName("实时检索成功后刷新缓存为最新笔记")
        void liveSuccessRefreshesCache() {
            catalog.put("A-001", 4.6);
            cache.put(DEST, List.of(note("STALE", SentimentType.NEGATIVE, "A-001")));
            source.result = List.of(note("FRESH", SentimentType.POSITIVE, "A-001"));

            analyzer().analyze(query());

            assertEquals(List.of("FRESH"),
                    cache.get(DEST).orElseThrow().stream().map(XHSNote::noteId).toList());
        }
    }

    @Nested
    @DisplayName("限频边界：小红书 ≤10/min 真实额度（AC-6 / 边界 #2 / 接口协议 §4）")
    class RateLimitBoundary {

        /** 以接口协议 §4 默认额度（xhs=10/min）构造受治理调用器，固定时钟避免窗口翻转。 */
        private XHSNoteAnalyzer analyzerWithDefaultLimit() {
            RateLimiter limiter = RateLimiter.withDefaults(new InMemoryRateLimitStore(() -> 0L));
            GovernedExternalCaller limitedCaller =
                    new GovernedExternalCaller(RetryPolicy.defaultPolicy(), sleeps::add, limiter);
            return new XHSNoteAnalyzer(source, cache, catalog, limitedCaller);
        }

        @Test
        @DisplayName("第 10 次仍实时检索（额度内不降级），第 11 次命中限频走缓存（不超频）")
        void tenthSucceedsEleventhRateLimited() {
            catalog.put("A-001", 4.6);
            cache.put(DEST, List.of(note("C1", SentimentType.POSITIVE, "A-001")));
            source.result = List.of(note("N1", SentimentType.POSITIVE, "A-001"));
            XHSNoteAnalyzer analyzer = analyzerWithDefaultLimit();

            // 前 10 次：额度内，实时检索成功、不降级
            for (int i = 1; i <= 10; i++) {
                XHSAnalysisResult ok = analyzer.analyze(query());
                assertFalse(ok.fallback(), "第 " + i + " 次应在 10/min 额度内实时返回");
                assertFalse(ok.telemetry().rateLimited());
            }
            assertEquals(10, source.calls.get(), "额度内每次都应真实调用检索源");

            // 第 11 次：命中限频 → 短路、走缓存降级，绝不第 11 次调用检索源
            XHSAnalysisResult limited = analyzer.analyze(query());
            assertTrue(limited.fallback());
            assertTrue(limited.telemetry().rateLimited());
            assertEquals(CacheOutcome.HIT, limited.telemetry().cacheOutcome());
            assertEquals(10, source.calls.get(), "命中限频不得令检索源调用次数超过 10（不超频）");
        }
    }

    @Nested
    @DisplayName("回归：降级取自缓存的笔记仍受评分/情感不变量约束（防规则漂移）")
    class CacheFallbackRegression {

        @Test
        @DisplayName("缓存命中但景点评分 <3.5：降级 fallback=true 但候选为空（不变量不被绕过）")
        void cachedLowRatingStillFiltered() {
            catalog.put("A-LOW", 3.4);
            cache.put(DEST, List.of(note("C1", SentimentType.POSITIVE, "A-LOW")));
            source.failure = new TimeoutException("slow");

            XHSAnalysisResult result = analyzer().analyze(query());

            assertTrue(result.fallback());
            assertEquals(CacheOutcome.HIT, result.telemetry().cacheOutcome());
            assertTrue(result.candidates().isEmpty(), "评分 <3.5 即便来自缓存也不得入选");
        }

        @Test
        @DisplayName("缓存命中但负面情感占多数：降级仍剔除该候选（情感规则不漂移）")
        void cachedNegativeSentimentStillExcluded() {
            catalog.put("A-NEG", 4.8);
            cache.put(DEST, List.of(
                    note("C1", SentimentType.NEGATIVE, "A-NEG"),
                    note("C2", SentimentType.NEGATIVE, "A-NEG"),
                    note("C3", SentimentType.POSITIVE, "A-NEG")));
            source.failure = new TimeoutException("slow");

            XHSAnalysisResult result = analyzer().analyze(query());

            assertTrue(result.fallback());
            assertTrue(result.candidates().isEmpty(), "负面占多数即便来自缓存也应剔除");
        }

        @Test
        @DisplayName("缓存命中且达标：降级仍产出有效候选（fallback=true 不等于无结果）")
        void cachedValidCandidateSurvivesDegradation() {
            catalog.put("A-001", 4.6);
            cache.put(DEST, List.of(note("C1", SentimentType.POSITIVE, "A-001")));
            source.failure = new TimeoutException("slow");

            XHSAnalysisResult result = analyzer().analyze(query());

            assertTrue(result.fallback());
            assertEquals(1, result.candidates().size());
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
        }
    }

    @Nested
    @DisplayName("Source→Parser→Analyzer 链路（增量 B / T-10 / AC-12）")
    class SourceParserAnalyzerChain {

        /** 原始笔记：只有标题/正文，无 mentions（模拟 XHSNoteSource 只取原文）。 */
        private static XHSNote rawNote(String noteId, String title, String content) {
            return new XHSNote(noteId, title, content, List.of("旅游"), 100, List.of());
        }

        /** 测试替身 Parser：按预设词表抽取景点名 → AttractionMention。 */
        private XHSNoteParser dictionaryParser() {
            return raw -> {
                List<AttractionMention> mentions = new ArrayList<>();
                String text = raw.title() + raw.content();
                // 景点名→(ID, 情感) 词表
                Map<String, AttractionMention> lexicon = Map.of(
                        "宽窄巷子", new AttractionMention("A-001", SentimentType.POSITIVE),
                        "锦里", new AttractionMention("A-002", SentimentType.NEGATIVE),
                        "武侯祠", new AttractionMention("A-003", SentimentType.NEUTRAL));
                lexicon.forEach((name, mention) -> {
                    if (text.contains(name)) {
                        mentions.add(mention);
                    }
                });
                mentions.sort((a, b) -> a.attractionId().compareTo(b.attractionId()));
                return new XHSNote(raw.noteId(), raw.title(), raw.content(),
                        raw.tags(), raw.likeCount(), mentions);
            };
        }

        @Test
        @DisplayName("原始笔记 → Parser 抽取 → Analyzer 聚合筛选 → 候选产出（Case-6 等价）")
        void rawNoteParsedThenAnalyzed() {
            catalog.put("A-001", 4.6).put("A-002", 3.8).put("A-003", 4.2);
            source.result = List.of(
                    rawNote("N1", "宽窄巷子超赞", "但锦里太挤了"),
                    rawNote("N2", "武侯祠值得一去", "很有文化氛围"));

            XHSAnalysisResult result = analyzerWithParser(dictionaryParser()).analyze(query());

            assertFalse(result.fallback());
            // A-001(宽窄巷子/正面) + A-003(武侯祠/中性) 入选；A-002(锦里/负面100%) 被剔除
            assertEquals(2, result.candidates().size());
        }

        @Test
        @DisplayName("Parser 对某笔记抛异常 → 该笔记跳过，其余正常聚合（Case-7 等价）")
        void parserFailureOnOneNoteSkipsIt() {
            catalog.put("A-001", 4.6);
            source.result = List.of(
                    rawNote("N-FAIL", "会触发解析异常的笔记", ""),
                    rawNote("N-OK", "宽窄巷子不错", "值得再来"));

            XHSNoteParser failFirstParser = raw -> {
                if ("N-FAIL".equals(raw.noteId())) {
                    throw new TimeoutException("llm timeout on this note");
                }
                // 正常解析
                List<AttractionMention> mentions = List.of(
                        new AttractionMention("A-001", SentimentType.POSITIVE));
                return new XHSNote(raw.noteId(), raw.title(), raw.content(),
                        raw.tags(), raw.likeCount(), mentions);
            };

            XHSAnalysisResult result = analyzerWithParser(failFirstParser).analyze(query());

            assertFalse(result.fallback());
            assertEquals(1, result.candidates().size());
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
        }

        @Test
        @DisplayName("Parser 全部失败 → 等同空检索（空候选 + fallback=true）")
        void allParserFailuresFallbackEmpty() {
            source.result = List.of(
                    rawNote("N1", "笔记一", ""),
                    rawNote("N2", "笔记二", ""));

            XHSNoteParser alwaysFails = raw -> {
                throw new TimeoutException("llm always fails");
            };

            XHSAnalysisResult result = analyzerWithParser(alwaysFails).analyze(query());

            assertTrue(result.candidates().isEmpty());
            assertTrue(result.fallback());
        }

        @Test
        @DisplayName("无 Parser（null）→ 笔记直接使用（向后兼容增量 A）")
        void noParserUsesNotesAsIs() {
            catalog.put("A-001", 4.6);
            source.result = List.of(note("N1", SentimentType.POSITIVE, "A-001"));

            XHSAnalysisResult result = analyzerWithParser(null).analyze(query());

            assertFalse(result.fallback());
            assertEquals(1, result.candidates().size());
        }

        @Test
        @DisplayName("「夸 A 吐槽 B」经 Parser 抽取 → A 入选 B 因负面过半剔除（AC-11 情感粒度）")
        void praisABlameB() {
            catalog.put("A-001", 4.5).put("A-002", 4.0);
            source.result = List.of(
                    rawNote("N1", "宽窄巷子不错", "但锦里太挤不推荐"),
                    rawNote("N2", "锦里确实不行", "到处都是商业化"),
                    rawNote("N3", "宽窄巷子氛围好", "推荐"));

            XHSNoteParser sentimentParser = raw -> {
                List<AttractionMention> mentions = new ArrayList<>();
                String text = raw.title() + raw.content();
                if (text.contains("宽窄巷子")) {
                    mentions.add(new AttractionMention("A-001", SentimentType.POSITIVE));
                }
                if (text.contains("锦里")) {
                    mentions.add(new AttractionMention("A-002", SentimentType.NEGATIVE));
                }
                return new XHSNote(raw.noteId(), raw.title(), raw.content(),
                        raw.tags(), raw.likeCount(), mentions);
            };

            XHSAnalysisResult result = analyzerWithParser(sentimentParser).analyze(query());

            assertEquals(1, result.candidates().size(), "B（锦里）负面占多数应被剔除");
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
        }

        // ---- Case-6/7/8 端到端验证（增量 B 阶段③ / AC-8~AC-11） ----

        @Test
        @DisplayName("Case-6 完整链路：XHSOpenApiNoteSource → LlmXHSNoteParser → Analyzer → 候选产出")
        void case6FullChainOfficialApiToCandidates() throws Exception {
            catalog.put("A-001", 4.6).put("A-002", 3.8);
            // 官方 API 返回 2 篇原始笔记（mentions 空，经 HttpClient 桩返回预设 JSON）
            String apiResponse = "[{\"noteId\":\"N1\",\"title\":\"宽窄巷子超赞\","
                    + "\"content\":\"很有氛围\",\"tags\":[\"旅游\"],\"likeCount\":200},"
                    + "{\"noteId\":\"N2\",\"title\":\"锦里太商业化\","
                    + "\"content\":\"人多拥挤\",\"tags\":[\"避雷\"],\"likeCount\":50}]";
            XHSOpenApiNoteSource apiSource =
                    new XHSOpenApiNoteSource(req -> apiResponse, "test-api-key");

            // LlmXHSNoteParser：LLM 桩返回逐提及情感（宽窄巷子正面 + 锦里负面）
            NameCatalog nc = nameCatalog();
            nc.put("A-001", "宽窄巷子", 4.6).put("A-002", "锦里", 3.8);
            LlmClient stubLlm = prompt -> "[{\"name\":\"宽窄巷子\",\"sentiment\":\"POSITIVE\"},"
                    + "{\"name\":\"锦里\",\"sentiment\":\"NEGATIVE\"}]";
            GovernedExternalCaller llmCaller =
                    new GovernedExternalCaller(RetryPolicy.defaultPolicy(), sleeps::add);
            LlmXHSNoteParser parser = new LlmXHSNoteParser(stubLlm, nc, llmCaller);

            // 组装：API Source + Parser + Analyzer（各自经独立治理）
            GovernedExternalCaller sourceCaller =
                    new GovernedExternalCaller(RetryPolicy.defaultPolicy(), sleeps::add);
            XHSNoteAnalyzer analyzer = new XHSNoteAnalyzer(apiSource, cache, catalog, sourceCaller,
                    new SensitiveContentScreen(), parser);

            XHSAnalysisResult result = analyzer.analyze(query());

            assertFalse(result.fallback(), "正常链路不应降级");
            // A-001(宽窄巷子/正面/4.6) 入选；A-002(锦里/负面占比100%) 被剔除
            assertEquals(1, result.candidates().size());
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
        }

        @Test
        @DisplayName("Case-6 密钥缺失：XHSOpenApiNoteSource 构造快速失败 → 驱劀 Analyzer 降级")
        void case6MissingApiKeyFailsFast() {
            // 密钥缺失/空白 → 构造时抛 IllegalArgumentException（快速失败）
            assertThrows(IllegalArgumentException.class,
                    () -> new XHSOpenApiNoteSource(req -> "[]", null));
        }

        @Test
        @DisplayName("Case-7 LLM 限频耗尽：LlmXHSNoteParser 所有笔记降级跳过 → 空候选 "
                + "+ fallback=true（不抛裸异常）")
        void case7LlmRateLimitExhaustedAllNotesSkipped() {
            catalog.put("A-001", 4.6);
            source.result = List.of(
                    rawNote("N1", "宽窄巷子游记", "值得一逛"),
                    rawNote("N2", "成都美食", "火锅好吃"));

            // LLM 限频 ≤2/min，预先耗尽 → 后续 parse 抛 RateLimitedException
            InMemoryRateLimitStore llmStore = new InMemoryRateLimitStore(() -> 0L);
            RateLimiter llmLimiter = new RateLimiter(llmStore,
                    Map.of(RateLimiter.API_LLM,
                            new RateLimitRule(RateLimiter.API_LLM, 2, 60L)));
            llmLimiter.acquireOrThrow(RateLimiter.API_LLM);
            llmLimiter.acquireOrThrow(RateLimiter.API_LLM);
            GovernedExternalCaller exhaustedLlmCaller = new GovernedExternalCaller(
                    RetryPolicy.defaultPolicy(), sleeps::add, llmLimiter);

            NameCatalog nc2 = nameCatalog();
            nc2.put("A-001", "宽窄巷子", 4.6);
            LlmClient stubLlm = prompt -> "[{\"name\":\"宽窄巷子\",\"sentiment\":\"POSITIVE\"}]";
            LlmXHSNoteParser parser =
                    new LlmXHSNoteParser(stubLlm, nc2, exhaustedLlmCaller);

            XHSAnalysisResult result = analyzerWithParser(parser).analyze(query());

            // 全部笔记因 LLM 限频被跳过 → 等同空检索
            assertTrue(result.candidates().isEmpty());
            assertTrue(result.fallback());
        }

        @Test
        @DisplayName("Case-7 部分 LLM 限频 + 部分正常：限频笔记跳过，正常笔记保留")
        void case7PartialLlmRateLimit() {
            catalog.put("A-001", 4.6);
            source.result = List.of(
                    rawNote("N-OK", "宽窄巷子不错", "推荐"),
                    rawNote("N-RL", "会被限频的笔记", "无关"));

            // LLM 限频 ≤1/min，预先消耗 → 第一篇正常、第二篇限频
            InMemoryRateLimitStore llmStore = new InMemoryRateLimitStore(() -> 0L);
            RateLimiter llmLimiter = new RateLimiter(llmStore,
                    Map.of(RateLimiter.API_LLM,
                            new RateLimitRule(RateLimiter.API_LLM, 1, 60L)));
            GovernedExternalCaller partialLlmCaller = new GovernedExternalCaller(
                    RetryPolicy.defaultPolicy(), sleeps::add, llmLimiter);

            NameCatalog nc3 = nameCatalog();
            nc3.put("A-001", "宽窄巷子", 4.6);
            LlmClient stubLlm = prompt -> "[{\"name\":\"宽窄巷子\",\"sentiment\":\"POSITIVE\"}]";
            LlmXHSNoteParser parser =
                    new LlmXHSNoteParser(stubLlm, nc3, partialLlmCaller);

            XHSAnalysisResult result = analyzerWithParser(parser).analyze(query());

            // N-OK 正常解析，N-RL 被限频跳过
            assertFalse(result.fallback());
            assertEquals(1, result.candidates().size());
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
        }

        @Test
        @DisplayName("Case-8 情感粒度：逐提及互不串扰 ——「夸 A 吐槽 B」A 入选 B 剔除（AC-11）")
        void case8PerMentionSentimentIndependence() {
            catalog.put("A-001", 4.5).put("A-002", 4.0);
            // 3 篇笔记：N1 同时夸 A 吐槽 B、N2 吐槽 B、N3 夸 A
            source.result = List.of(
                    rawNote("N1", "宽窄巷子不错", "但锦里太挤不推荐"),
                    rawNote("N2", "锦里确实不行", "到处都是商业化"),
                    rawNote("N3", "宽窄巷子氛围好", "推荐"));

            XHSNoteParser explicitSentimentParser = raw -> {
                List<AttractionMention> mentions = new ArrayList<>();
                String text = raw.title() + raw.content();
                if (text.contains("宽窄巷子")) {
                    mentions.add(new AttractionMention("A-001", SentimentType.POSITIVE));
                }
                if (text.contains("锦里")) {
                    mentions.add(new AttractionMention("A-002", SentimentType.NEGATIVE));
                }
                return new XHSNote(raw.noteId(), raw.title(), raw.content(),
                        raw.tags(), raw.likeCount(), mentions);
            };

            XHSAnalysisResult result =
                    analyzerWithParser(explicitSentimentParser).analyze(query());

            // A-001（宽窄巷子）：2 次正面 / 0 次负面 → 入选
            // A-002（锦里）：0 次正面 / 2 次负面（100% 负面，>50%）→ 剔除
            assertEquals(1, result.candidates().size(),
                    "A（宽窄巷子）正面应保留，B（锦里）全负面应剔除，互不串扰");
            assertEquals("A-001", result.candidates().get(0).attraction().attractionId());
            // 验证 A 的提及计数不受 B 影响
            assertEquals(2, result.candidates().get(0).mentionCount(),
                    "宽窄巷子被提及 2 次（N1 + N3）");
            assertEquals(0.0, result.candidates().get(0).negativeRatio(),
                    "宽窄巷子负面占比应为 0（不受锦里负面串扰）");
        }
    }

    /**
     * 支持名称查找的景点目录桩（供 LlmXHSNoteParser 的 findByName 链接使用）。
     *
     * <p>返回具体类以支持测试方法链式 {@code put(id, name, rating)} 调用。</p>
     */
    private static NameCatalog nameCatalog() {
        return new NameCatalog();
    }

    /** 测试替身：同时支持 {@code findById} 与 {@code findByName} 的景点目录。 */
    private static final class NameCatalog implements AttractionCatalog {
        private final Map<String, Attraction> byId = new HashMap<>();
        private final Map<String, Attraction> byName = new HashMap<>();

        NameCatalog put(String id, String name, double rating) {
            Attraction a = new Attraction(id, name, new GeoLocation(0d, 0d, null, DEST),
                    rating, 90, new BigDecimal("60"), List.of("热门"));
            byId.put(id, a);
            byName.put(name, a);
            return this;
        }

        @Override
        public Optional<Attraction> findById(String attractionId) {
            return Optional.ofNullable(byId.get(attractionId));
        }

        @Override
        public Optional<Attraction> findByName(String name) {
            return Optional.ofNullable(byName.get(name));
        }
    }
}
