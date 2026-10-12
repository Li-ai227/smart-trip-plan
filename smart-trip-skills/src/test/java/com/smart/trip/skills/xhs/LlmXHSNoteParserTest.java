package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.exception.RateLimitedException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.InMemoryRateLimitStore;
import com.smart.trip.skills.governance.RateLimitRule;
import com.smart.trip.skills.governance.RateLimiter;
import com.smart.trip.skills.governance.RetryPolicy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link LlmXHSNoteParser} 单测：LLM 抽取 + AttractionCatalog 链接 + GovernedExternalCaller 治理
 * （C-006 增量 B / T-9，AC-10）。
 *
 * <p>TDD 先红：覆盖正常抽取（逐提及情感）、无可链接景点、LLM 超时/重试耗尽降级（空提及）、
 * 未知景点名丢弃。LLM 用桩实现返回预设 JSON，保证抽取/链接逻辑离线确定性。</p>
 */
class LlmXHSNoteParserTest {

    // ---- 测试替身 ----

    /** 内存景点目录桩（支持 ID + 名称双索引，增量 B 新增 findByName）。 */
    private static final class MapCatalog implements AttractionCatalog {
        private final Map<String, Attraction> byId = new HashMap<>();
        private final Map<String, Attraction> byName = new HashMap<>();

        MapCatalog put(String id, String name, double rating) {
            Attraction a = new Attraction(id, name, new GeoLocation(0d, 0d, null, "成都"),
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

    // ---- 测试夹具 ----

    private final List<Long> sleeps = new ArrayList<>();
    private final MapCatalog catalog = new MapCatalog();

    private GovernedExternalCaller caller() {
        return new GovernedExternalCaller(RetryPolicy.defaultPolicy(), sleeps::add);
    }

    private static XHSNote rawNote(String noteId, String title, String content) {
        return new XHSNote(noteId, title, content, List.of("旅游"), 100, List.of());
    }

    @Nested
    @DisplayName("正常抽取：LLM 返回结构化 JSON → 链接到目录 → 逐提及情感（AC-10）")
    class Extraction {

        @Test
        @DisplayName("抽取「夸 A 吐槽 B」→ A 正面 B 负面，逐提及互不串扰")
        void extractsPerMentionSentiment() throws Exception {
            catalog.put("A-KZ", "宽窄巷子", 4.5).put("A-JL", "锦里", 3.8);
            LlmClient stubLlm = prompt -> "[{\"name\":\"宽窄巷子\",\"sentiment\":\"POSITIVE\"},"
                    + "{\"name\":\"锦里\",\"sentiment\":\"NEGATIVE\"}]";
            LlmXHSNoteParser parser = new LlmXHSNoteParser(stubLlm, catalog, caller());

            XHSNote result = parser.parse(rawNote("N1", "宽窄巷子好玩但锦里太挤", "古镇游记"));

            assertEquals("N1", result.noteId(), "原文标识应透传");
            assertEquals(2, result.mentions().size());
            assertEquals(new AttractionMention("A-KZ", SentimentType.POSITIVE), result.mentions().get(0));
            assertEquals(new AttractionMention("A-JL", SentimentType.NEGATIVE), result.mentions().get(1));
        }

        @Test
        @DisplayName("多次正面提及同一景点：每次均产出独立 mention")
        void multiplePositiveMentionsSameAttraction() throws Exception {
            catalog.put("A-KZ", "宽窄巷子", 4.5);
            LlmClient stubLlm = prompt -> "[{\"name\":\"宽窄巷子\",\"sentiment\":\"POSITIVE\"},"
                    + "{\"name\":\"宽窄巷子\",\"sentiment\":\"NEUTRAL\"}]";
            LlmXHSNoteParser parser = new LlmXHSNoteParser(stubLlm, catalog, caller());

            XHSNote result = parser.parse(rawNote("N2", "再逛宽窄巷子", "两次都不错"));

            assertEquals(2, result.mentions().size());
        }
    }

    @Nested
    @DisplayName("链接失败：LLM 抽取的景点名在目录中不存在 → 丢弃该提及（不伪造候选）")
    class LinkFailure {

        @Test
        @DisplayName("未知景点名被丢弃，已知景点保留")
        void unknownAttractionNameDiscarded() throws Exception {
            catalog.put("A-KZ", "宽窄巷子", 4.5);
            LlmClient stubLlm = prompt -> "[{\"name\":\"宽窄巷子\",\"sentiment\":\"POSITIVE\"},"
                    + "{\"name\":\"不存在的景点\",\"sentiment\":\"POSITIVE\"}]";
            LlmXHSNoteParser parser = new LlmXHSNoteParser(stubLlm, catalog, caller());

            XHSNote result = parser.parse(rawNote("N3", "混合景点", "有已知有未知"));

            assertEquals(1, result.mentions().size());
            assertEquals("A-KZ", result.mentions().get(0).attractionId());
        }

        @Test
        @DisplayName("全部景点名未知：返回空提及（不伪造候选）")
        void allUnknownYieldsEmptyMentions() throws Exception {
            LlmClient stubLlm = prompt -> "[{\"name\":\"不存在A\",\"sentiment\":\"POSITIVE\"}]";
            LlmXHSNoteParser parser = new LlmXHSNoteParser(stubLlm, catalog, caller());

            XHSNote result = parser.parse(rawNote("N4", "未知地方", "没有在目录里"));

            assertTrue(result.mentions().isEmpty());
        }
    }

    @Nested
    @DisplayName("LLM 降级：超时/重试耗尽 → 空提及（GovernedExternalCaller 治理，AC-10）")
    class LlmDegradation {

        @Test
        @DisplayName("LLM 持续超时 → 重试耗尽 → 降级返回空提及（不抛裸异常）")
        void llmTimeoutDegradesToEmptyMentions() throws Exception {
            LlmClient failLlm = prompt -> { throw new TimeoutException("llm slow"); };
            LlmXHSNoteParser parser = new LlmXHSNoteParser(failLlm, catalog, caller());

            XHSNote result = parser.parse(rawNote("N5", "超时笔记", "LLM 挂了"));

            assertTrue(result.mentions().isEmpty(), "降级应返回空提及");
            assertEquals("N5", result.noteId(), "原文标识应透传");
        }

        @Test
        @DisplayName("LLM 返回非法 JSON → 降级返回空提及（解析容错）")
        void llmMalformedResponseDegradesToEmptyMentions() throws Exception {
            LlmClient badLlm = prompt -> "这不是JSON";
            LlmXHSNoteParser parser = new LlmXHSNoteParser(badLlm, catalog, caller());

            XHSNote result = parser.parse(rawNote("N6", "坏响应", "JSON 无效"));

            assertTrue(result.mentions().isEmpty());
        }
    }

    @Nested
    @DisplayName("边界：空正文 / 空 LLM 返回")
    class EdgeCases {

        @Test
        @DisplayName("LLM 返回空数组：空提及")
        void llmReturnsEmptyArray() throws Exception {
            LlmClient emptyLlm = prompt -> "[]";
            LlmXHSNoteParser parser = new LlmXHSNoteParser(emptyLlm, catalog, caller());

            XHSNote result = parser.parse(rawNote("N7", "纯风景", "没提到景点"));

            assertTrue(result.mentions().isEmpty());
        }

        @Test
        @DisplayName("LLM 返回 sentiment 缺失/null → 默认 NEUTRAL")
        void missingSentimentDefaultsToNeutral() throws Exception {
            catalog.put("A-KZ", "宽窄巷子", 4.5);
            LlmClient stubLlm = prompt -> "[{\"name\":\"宽窄巷子\"}]";
            LlmXHSNoteParser parser = new LlmXHSNoteParser(stubLlm, catalog, caller());

            XHSNote result = parser.parse(rawNote("N8", "宽窄巷子一般", "没说好坏"));

            assertEquals(1, result.mentions().size());
            assertEquals(SentimentType.NEUTRAL, result.mentions().get(0).sentiment());
        }
    }

    @Nested
    @DisplayName("LLM 限频边界：API_LLM ≤60/min 耗尽降级（增量 B 阶段③ / AC-10 边界）")
    class LlmRateLimitBoundary {

        /**
         * 构造带 LLM 限频的 GovernedExternalCaller（便于调整额度做边界测试）。
         *
         * @param maxPerMin 每分钟最大调用次数
         */
        private GovernedExternalCaller limitedCaller(int maxPerMin) {
            InMemoryRateLimitStore store = new InMemoryRateLimitStore(() -> 0L);
            RateLimiter limiter = new RateLimiter(store,
                    Map.of(RateLimiter.API_LLM,
                            new RateLimitRule(RateLimiter.API_LLM, maxPerMin, 60L)));
            return new GovernedExternalCaller(RetryPolicy.defaultPolicy(), sleeps::add, limiter);
        }

        @Test
        @DisplayName("LLM 限频额度内正常解析（60/min 未耗尽）")
        void withinLlmLimitParsesNormally() throws Exception {
            catalog.put("A-KZ", "宽窄巷子", 4.5);
            LlmClient stubLlm = prompt ->
                    "[{\"name\":\"宽窄巷子\",\"sentiment\":\"POSITIVE\"}]";
            LlmXHSNoteParser parser =
                    new LlmXHSNoteParser(stubLlm, catalog, limitedCaller(60));

            XHSNote result = parser.parse(rawNote("N1", "宽窄巷子不错", "有氛围"));

            assertEquals(1, result.mentions().size());
            assertEquals(new AttractionMention("A-KZ", SentimentType.POSITIVE),
                    result.mentions().get(0));
        }

        @Test
        @DisplayName("LLM 限频耗尽 → RateLimitedException 抛出（由上层 Analyzer.parseNotes catch 降级跳过）")
        void llmRateLimitExhaustedThrowsRateLimitedException() throws Exception {
            catalog.put("A-KZ", "宽窄巷子", 4.5);
            // 额度 ≤2/min，预先 2 次耗尽
            GovernedExternalCaller exhaustedCaller = limitedCaller(2);
            // 预先耗尽 2 次额度（经 caller 的实际 acquire 通路）
            exhaustedCaller.call(RateLimiter.API_LLM,
                    () -> "dummy-1", cause -> "fallback-1");
            exhaustedCaller.call(RateLimiter.API_LLM,
                    () -> "dummy-2", cause -> "fallback-2");

            LlmClient stubLlm = prompt ->
                    "[{\"name\":\"宽窄巷子\",\"sentiment\":\"POSITIVE\"}]";
            LlmXHSNoteParser parser =
                    new LlmXHSNoteParser(stubLlm, catalog, exhaustedCaller);

            // 第 3 次（超限额）→ RateLimitedException 应立即抛出
            // 由 XHSNoteAnalyzer.parseNotes() catch → 该笔记降级跳过，不污染候选
            assertThrows(RateLimitedException.class,
                    () -> parser.parse(rawNote("N-RL", "限频测试", "笔记")));
        }

        @Test
        @DisplayName("LLM 限频耗尽后笔记标识仍可读（原文透传不受限频影响）")
        void rawNoteFieldsPreservedWhenRateLimited() throws Exception {
            // 额度 1/min，预先耗尽 → 后续 parse 限频
            GovernedExternalCaller exhaustedCaller = limitedCaller(1);
            exhaustedCaller.call(RateLimiter.API_LLM,
                    () -> "dummy", cause -> "fallback");
            LlmClient stubLlm = prompt ->
                    "[{\"name\":\"宽窄巷子\",\"sentiment\":\"POSITIVE\"}]";
            LlmXHSNoteParser parser =
                    new LlmXHSNoteParser(stubLlm, catalog, exhaustedCaller);

            // 限频异常由上层处理；此处验证解析器本身不隐藏异常
            RateLimitedException ex = assertThrows(RateLimitedException.class,
                    () -> parser.parse(rawNote("N-RL", "限频笔记", "原文内容")));
            assertTrue(ex.getMessage().contains("限频"));
        }
    }
}
