package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.exception.RateLimitedException;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.skills.governance.CallResult;
import com.smart.trip.skills.governance.FallbackHandler;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.InMemoryRateLimitStore;
import com.smart.trip.skills.governance.RateLimitRule;
import com.smart.trip.skills.governance.RateLimiter;
import com.smart.trip.skills.governance.RetryPolicy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link XHSOpenApiNoteSource} 单测（C-006 增量 B / T-11，AC-8）。
 *
 * <p>TDD 先红：覆盖正常返回原始笔记（mentions 为空 · XHSNoteSource 退化为只取原文）、
 * API 密钥缺失/授权失败抛异常（驱动统一治理降级）、禁模拟登录。HTTP 调用用桩实现
 * （{@link HttpClient}），保证确定性可离线覆盖。</p>
 */
class XHSOpenApiNoteSourceTest {

    @Nested
    @DisplayName("正常检索：返回原始笔记（mentions 为空，交由 Parser 解析）")
    class NormalSearch {

        @Test
        @DisplayName("API 返回有效 JSON → 解析为原始 XHSNote 列表（mentions 空）")
        void returnsRawNotesWithEmptyMentions() throws Exception {
            String apiResponse = "[{\"noteId\":\"N1\",\"title\":\"宽窄巷子真好\",\"content\":\"推荐\","
                    + "\"tags\":[\"旅游\"],\"likeCount\":200}]";
            XHSOpenApiNoteSource source = new XHSOpenApiNoteSource(
                    req -> apiResponse, "test-api-key");

            List<XHSNote> notes = source.search(XHSAnalysisQuery.of("成都", List.of("美食")));

            assertEquals(1, notes.size());
            assertEquals("N1", notes.get(0).noteId());
            assertEquals("宽窄巷子真好", notes.get(0).title());
            assertTrue(notes.get(0).mentions().isEmpty(), "XHSNoteSource 只取原文，mentions 应为空");
        }

        @Test
        @DisplayName("API 返回空数组 → 空列表（非 null）")
        void emptyArrayReturnsEmptyList() throws Exception {
            XHSOpenApiNoteSource source = new XHSOpenApiNoteSource(req -> "[]", "test-api-key");

            List<XHSNote> notes = source.search(XHSAnalysisQuery.of("成都", List.of()));

            assertTrue(notes.isEmpty());
        }
    }

    @Nested
    @DisplayName("密钥与授权（AC-8：API Key 从环境变量读取，禁硬编码）")
    class ApiKeyAndAuth {

        @Test
        @DisplayName("密钥为 null → 构造时抛异常（快速失败）")
        void nullApiKeyThrowsOnConstruction() {
            assertThrows(IllegalArgumentException.class,
                    () -> new XHSOpenApiNoteSource(req -> "[]", null));
        }

        @Test
        @DisplayName("密钥为空白 → 构造时抛异常（快速失败）")
        void blankApiKeyThrowsOnConstruction() {
            assertThrows(IllegalArgumentException.class,
                    () -> new XHSOpenApiNoteSource(req -> "[]", "  "));
        }

        @Test
        @DisplayName("HTTP 返回 401/403 → 抛异常驱动降级（绝不降级为模拟登录）")
        void authFailureThrowsException() {
            XHSOpenApiNoteSource source = new XHSOpenApiNoteSource(
                    req -> { throw new IOException("HTTP 403 Forbidden"); }, "test-key");

            assertThrows(IOException.class,
                    () -> source.search(XHSAnalysisQuery.of("成都", List.of())));
        }
    }

    @Nested
    @DisplayName("合规：不含模拟登录/登录态代码")
    class Compliance {

        @Test
        @DisplayName("HttpClient 请求不含 Cookie/登录态头")
        void noCookieOrLoginHeaders() throws Exception {
            String[] capturedRequest = {null};
            XHSOpenApiNoteSource source = new XHSOpenApiNoteSource(
                    req -> { capturedRequest[0] = req; return "[]"; }, "test-key");

            source.search(XHSAnalysisQuery.of("成都", List.of()));

            assertFalse(capturedRequest[0] == null);
            assertFalse(capturedRequest[0].toLowerCase().contains("cookie"),
                    "请求不得包含 Cookie（合规红线：禁模拟登录）");
        }
    }

    @Nested
    @DisplayName("GovernedExternalCaller 治理集成：xhs ≤10/min 限频 + 重试 + 降级缓存（增量 B 阶段③ / AC-8）")
    class GovernedIntegration {

        private final List<Long> sleeps = new ArrayList<>();

        /**
         * 构造带 xhs 限频的治理执行器。
         *
         * @param maxPerMin 每分钟最大调用次数
         */
        private GovernedExternalCaller governedCaller(int maxPerMin) {
            InMemoryRateLimitStore store = new InMemoryRateLimitStore(() -> 0L);
            RateLimiter limiter = new RateLimiter(store,
                    Map.of(RateLimiter.API_XHS,
                            new RateLimitRule(RateLimiter.API_XHS, maxPerMin, 60L)));
            return new GovernedExternalCaller(RetryPolicy.defaultPolicy(), sleeps::add, limiter);
        }

        @Test
        @DisplayName("额度内调用 XHSOpenApiNoteSource 正常返回原始笔记（mentions 空，经治理包装）")
        void withinRateLimitReturnsRawNotes() throws Exception {
            XHSOpenApiNoteSource source = new XHSOpenApiNoteSource(
                    req -> "[{\"noteId\":\"N1\",\"title\":\"宽窄巷子\",\"content\":\"好玩\","
                            + "\"tags\":[\"旅游\"],\"likeCount\":100}]",
                    "test-key");
            GovernedExternalCaller caller = governedCaller(10);

            CallResult<List<XHSNote>> result = caller.call(
                    RateLimiter.API_XHS,
                    () -> source.search(XHSAnalysisQuery.of("成都", List.of("美食"))),
                    cause -> List.of());

            assertFalse(result.fallback());
            assertEquals(1, result.value().size());
            assertEquals("N1", result.value().get(0).noteId());
            assertTrue(result.value().get(0).mentions().isEmpty(),
                    "XHSNoteSource 只取原文，mentions 应为空");
        }

        @Test
        @DisplayName("xhs ≤10/min 前 10 次实时检索成功，第 11 次命中限频（不超频）")
        void tenthCallSucceedsEleventhRateLimited() throws Exception {
            AtomicInteger httpCalls = new AtomicInteger();
            String singleNote = "[{\"noteId\":\"N1\",\"title\":\"测试\",\"content\":\"正文\","
                    + "\"tags\":[\"旅游\"],\"likeCount\":10}]";
            XHSOpenApiNoteSource source = new XHSOpenApiNoteSource(
                    req -> { httpCalls.incrementAndGet(); return singleNote; }, "test-key");
            GovernedExternalCaller caller = governedCaller(10);

            XHSAnalysisQuery query = XHSAnalysisQuery.of("成都", List.of("美食"));
            FallbackHandler<List<XHSNote>> fallback = cause -> List.of();

            // 前 10 次：额度内，实时检索成功
            for (int i = 1; i <= 10; i++) {
                CallResult<List<XHSNote>> ok = caller.call(
                        RateLimiter.API_XHS, () -> source.search(query), fallback);
                assertFalse(ok.fallback(), "第 " + i + " 次应在 10/min 额度内");
                assertEquals(1, ok.value().size());
            }
            assertEquals(10, httpCalls.get(), "额度内每次都真实调用 HTTP");

            // 第 11 次：命中限频 → RateLimitedException 抛出（GovernedExternalCaller
            // 设计：限频在重试/降级前短路，由上层 Analyzer.retrieveNotes catch 走缓存）
            assertThrows(RateLimitedException.class,
                    () -> caller.call(RateLimiter.API_XHS,
                            () -> source.search(query), fallback));
            assertEquals(10, httpCalls.get(), "命中限频不得额外调用 HTTP（不超频）");
        }

        @Test
        @DisplayName("授权失败/401 → 重试耗尽 → 降级 fallback=true（绝不降级为模拟登录）")
        void authFailureExhaustsRetriesThenFallback() throws Exception {
            AtomicInteger attempts = new AtomicInteger();
            XHSOpenApiNoteSource source = new XHSOpenApiNoteSource(
                    req -> { attempts.incrementAndGet(); throw new IOException("HTTP 401"); },
                    "test-key");
            GovernedExternalCaller caller = governedCaller(10);

            CallResult<List<XHSNote>> result = caller.call(
                    RateLimiter.API_XHS,
                    () -> source.search(XHSAnalysisQuery.of("成都", List.of())),
                    cause -> List.of());

            assertTrue(result.fallback(), "授权失败经重试耗尽后应降级");
            assertTrue(result.value().isEmpty(), "降级返回空列表（缓存/默认）");
            assertEquals(4, attempts.get(), "默认重试 3 次 → 总共 4 次尝试");
        }

        @Test
        @DisplayName("HttpClient 超时 → 重试（含退避）→ 恢复后 fallback=false")
        void transientTimeoutRecoversAfterRetries() throws Exception {
            AtomicInteger attempts = new AtomicInteger();
            String singleNote = "[{\"noteId\":\"N1\",\"title\":\"恢复\",\"content\":\"正文\","
                    + "\"tags\":[],\"likeCount\":5}]";
            XHSOpenApiNoteSource source = new XHSOpenApiNoteSource(req -> {
                int attempt = attempts.incrementAndGet();
                if (attempt <= 2) {
                    throw new TimeoutException("transient timeout");
                }
                return singleNote;
            }, "test-key");
            GovernedExternalCaller caller = governedCaller(10);

            CallResult<List<XHSNote>> result = caller.call(
                    RateLimiter.API_XHS,
                    () -> source.search(XHSAnalysisQuery.of("成都", List.of())),
                    cause -> List.of());

            assertFalse(result.fallback(), "重试内恢复不应降级");
            assertEquals(1, result.value().size());
            assertEquals("N1", result.value().get(0).noteId());
            assertEquals(3, attempts.get(), "2 次失败 + 第 3 次成功");
            // 退避：1s、2s
            assertEquals(List.of(1_000L, 2_000L), sleeps);
        }

        @Test
        @DisplayName("xhs 限频耗尽后走降级缓存（AC-6 等价：限频经 Analyzer catch → 走缓存）")
        void rateLimitExhaustionFallsBackToCache() throws Exception {
            // 额度 ≤2/min，预先耗尽
            GovernedExternalCaller caller = governedCaller(2);
            XHSAnalysisQuery query = XHSAnalysisQuery.of("成都", List.of("美食"));

            XHSOpenApiNoteSource source = new XHSOpenApiNoteSource(
                    req -> "[{\"noteId\":\"N1\",\"title\":\"实时\",\"content\":\"正文\","
                            + "\"tags\":[],\"likeCount\":10}]",
                    "test-key");
            FallbackHandler<List<XHSNote>> fallback = cause -> List.of();

            // 预先耗尽 2 次额度
            caller.call(RateLimiter.API_XHS, () -> source.search(query), fallback);
            caller.call(RateLimiter.API_XHS, () -> source.search(query), fallback);

            // 第 3 次：命中限频 → RateLimitedException 抛出
            // 由上层（XHSNoteAnalyzer.retrieveNotes catch）走缓存降级
            // 此处模拟 Analyzer 的 catch 语义：catch RateLimitedException → 走缓存
            List<XHSNote> cached = List.of(
                    new XHSNote("CACHED-1", "缓存笔记", "从缓存中恢复",
                            List.of("旅游"), 50, List.of()));
            List<XHSNote> result;
            try {
                caller.call(RateLimiter.API_XHS, () -> source.search(query), fallback);
                result = List.of(); // 不应到达
            } catch (RateLimitedException ex) {
                // Analyzer 语义：限频 → 走缓存降级（fallback=true）
                result = cached;
            }

            assertEquals(1, result.size());
            assertEquals("CACHED-1", result.get(0).noteId(),
                    "降级产物应来自缓存（模拟 Analyzer.retrieveNotes catch 语义）");
        }
    }
}
