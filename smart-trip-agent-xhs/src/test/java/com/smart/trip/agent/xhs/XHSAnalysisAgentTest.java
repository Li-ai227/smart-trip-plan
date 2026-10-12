package com.smart.trip.agent.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.skills.a2a.server.TripAgentCardFactory;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.InMemoryRateLimitStore;
import com.smart.trip.skills.governance.RateLimitRule;
import com.smart.trip.skills.governance.RateLimiter;
import com.smart.trip.skills.governance.RetryPolicy;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.xhs.AttractionCandidate;
import com.smart.trip.skills.xhs.AttractionCatalog;
import com.smart.trip.skills.xhs.XHSAgentRunner;
import com.smart.trip.skills.xhs.XHSAnalysisQuery;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import com.smart.trip.skills.xhs.XHSNoteAnalyzer;
import com.smart.trip.skills.xhs.XHSNoteCache;
import com.smart.trip.skills.xhs.XHSNoteSource;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link XHSAnalysisAgent} 单测：A2A 收发契约 + 降级标记 + 失败转 TASK_ERROR（C-006 AC-1/AC-2/AC-7）。
 *
 * <p>对齐接口协议.md §2/§3：仅受理 {@code TASK_ASSIGN(taskType=XHS_ANALYSIS)}；正常/降级装配
 * {@code TASK_RESULT}（降级显式 {@code fallback=true}）；非法 taskType/缺目的地返回可识别
 * {@code TASK_ERROR}，禁静默超时。用真实 {@link XHSNoteAnalyzer} + 内存桩端口做契约级联测。</p>
 */
class XHSAnalysisAgentTest {

    private static final String SUPERVISOR = "agent://supervisor";
    private static final String TRACE = "T-xhs-001";
    private static final String DEST = "成都";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AgentMetrics metrics = new AgentMetrics(registry);

    // ---- 桩端口 ----

    private static XHSNoteSource sourceReturning(List<XHSNote> notes) {
        return query -> notes;
    }

    private static AttractionCatalog catalogWith(String id, double rating) {
        Attraction attraction = new Attraction(id, "景点-" + id,
                new GeoLocation(0d, 0d, null, DEST), rating, 90, new BigDecimal("60"), List.of());
        return attractionId -> id.equals(attractionId) ? Optional.of(attraction) : Optional.empty();
    }

    private static final XHSNoteCache EMPTY_CACHE = new XHSNoteCache() {
        @Override
        public Optional<List<XHSNote>> get(String destination) {
            return Optional.empty();
        }

        @Override
        public void put(String destination, List<XHSNote> notes) {
            // 测试无需校验缓存写入
        }
    };

    private XHSNoteAnalyzer analyzer(XHSNoteSource source, AttractionCatalog catalog) {
        GovernedExternalCaller caller =
                new GovernedExternalCaller(RetryPolicy.defaultPolicy(), millis -> { });
        return new XHSNoteAnalyzer(source, EMPTY_CACHE, catalog, caller);
    }

    private XHSAnalysisAgent agentWith(XHSNoteSource source, AttractionCatalog catalog) {
        return new XHSAnalysisAgent(analyzer(source, catalog), metrics);
    }

    private static Msg assign(Map<String, Object> payload) {
        return Msg.taskAssign(SUPERVISOR, "agent://" + XHSAnalysisAgent.AGENT_ID, TRACE, payload);
    }

    private static Map<String, Object> xhsPayload(Map<String, Object> context) {
        return Map.of(Msg.KEY_TASK_TYPE, XHSAnalysisAgent.TASK_TYPE, XHSAnalysisAgent.KEY_CONTEXT, context);
    }

    @Test
    @DisplayName("标识规范化为 agent://xhs-analyst（AC-1 / AC-17）")
    void agentUriNormalized() {
        XHSAnalysisAgent agent = agentWith(sourceReturning(List.of()), catalogWith("A-001", 4.5));
        assertEquals("agent://xhs-analyst", agent.agentUri());
    }

    @Test
    @DisplayName("可构建 ConfigurableAgentCard 用于 A2A 服务发现（AC-19）")
    void buildableAsAgentCard() {
        ConfigurableAgentCard card = TripAgentCardFactory.buildCard(
                XHSAnalysisAgent.AGENT_ID,
                "小红书笔记分析 Agent",
                "0.0.1",
                List.of("xhs_note_analysis"));
        assertEquals("xhs-analyst", card.getName());
        assertEquals(1, card.getSkills().size());
        assertEquals("xhs_note_analysis", card.getSkills().get(0).id());
    }

    @Test
    @DisplayName("analyzer 为 null 时构造快速失败（AC-20）")
    void constructorRejectsNullAnalyzer() {
        assertThrows(InvalidRequestException.class,
                () -> new XHSAnalysisAgent(null, metrics));
    }

    @Test
    @DisplayName("metrics 为 null 时构造快速失败（AC-20）")
    void constructorRejectsNullMetrics() {
        XHSNoteAnalyzer ana = analyzer(sourceReturning(List.of()), catalogWith("A-001", 4.5));
        assertThrows(InvalidRequestException.class,
                () -> new XHSAnalysisAgent(ana, null));
    }

    @Test
    @DisplayName("receive 前状态 IDLE，receive 后状态 DONE（AC-20）")
    void statusLifecycleIdleToDone() {
        XHSAnalysisAgent agent = agentWith(sourceReturning(List.of()), catalogWith("A-001", 4.6));
        assertEquals(AgentStatus.IDLE, agent.status());
        agent.receive(assign(xhsPayload(Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));
        assertEquals(AgentStatus.DONE, agent.status());
    }

    @Nested
    @DisplayName("Happy Path：TASK_ASSIGN → TASK_RESULT 候选列表（AC-2 / Case-1）")
    class HappyPath {

        @Test
        @DisplayName("返回 TASK_RESULT，result 为候选列表，fallback=false，traceId 透传")
        void returnsCandidates() {
            XHSNote note = new XHSNote("N1", "t", "c", List.of("美食"), 100,
                    List.of(new AttractionMention("A-001", SentimentType.POSITIVE)));
            XHSAnalysisAgent agent = agentWith(sourceReturning(List.of(note)), catalogWith("A-001", 4.6));

            Msg result = agent.receive(assign(xhsPayload(Map.of(
                    XHSAnalysisAgent.CTX_DESTINATION, DEST,
                    XHSAnalysisAgent.CTX_PREFERENCES, List.of("美食")))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertEquals(TRACE, result.traceId());
            assertEquals("agent://xhs-analyst", result.from());
            assertEquals(SUPERVISOR, result.to());
            assertFalse(result.isFallback());
            Object payloadResult = result.payload().get(Msg.KEY_RESULT);
            XHSAnalysisResult xhsResult = assertInstanceOf(XHSAnalysisResult.class, payloadResult);
            assertEquals(1, xhsResult.candidates().size());
            assertInstanceOf(AttractionCandidate.class, xhsResult.candidates().get(0));
        }

        @Test
        @DisplayName("记录 agent.call.latency（agent/taskType 标签，AC-7）")
        void recordsLatencyMetric() {
            XHSAnalysisAgent agent = agentWith(sourceReturning(List.of()), catalogWith("A-001", 4.6));
            agent.receive(assign(xhsPayload(Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));

            var timer = registry.find(AgentMetrics.METRIC_CALL_LATENCY)
                    .tag("agent", "agent://xhs-analyst")
                    .tag("taskType", XHSAnalysisAgent.TASK_TYPE)
                    .timer();
            assertTrue(timer != null && timer.count() == 1);
        }

        @Test
        @DisplayName("context 含未知字段时忽略而非失败（向后兼容，边界 #5）")
        void ignoresUnknownContextFields() {
            XHSNote note = new XHSNote("N1", "t", "c", List.of(), 1,
                    List.of(new AttractionMention("A-001", SentimentType.POSITIVE)));
            XHSAnalysisAgent agent = agentWith(sourceReturning(List.of(note)), catalogWith("A-001", 4.6));

            Msg result = agent.receive(assign(xhsPayload(Map.of(
                    XHSAnalysisAgent.CTX_DESTINATION, DEST,
                    "unknownField", "ignored"))));

            assertEquals(MessageType.TASK_RESULT, result.type());
        }
    }

    @Nested
    @DisplayName("可观测：限频命中 + 缓存命中率指标（AC-7 / 接口协议 §6）")
    class Observability {

        @Test
        @DisplayName("检索故障走缓存 → 发射 memory.cache.hit{result=hit}")
        void emitsCacheHitMetric() {
            XHSNote cached = new XHSNote("C1", "t", "c", List.of(), 1,
                    List.of(new AttractionMention("A-001", SentimentType.POSITIVE)));
            XHSNoteSource failing = query -> {
                throw new java.util.concurrent.TimeoutException("slow");
            };
            XHSNoteCache cache = new XHSNoteCache() {
                @Override
                public Optional<List<XHSNote>> get(String destination) {
                    return Optional.of(List.of(cached));
                }

                @Override
                public void put(String destination, List<XHSNote> notes) {
                    // no-op
                }
            };
            GovernedExternalCaller caller =
                    new GovernedExternalCaller(RetryPolicy.defaultPolicy(), millis -> { });
            XHSNoteAnalyzer analyzer =
                    new XHSNoteAnalyzer(failing, cache, catalogWith("A-001", 4.6), caller);
            XHSAnalysisAgent agent = new XHSAnalysisAgent(analyzer, metrics);

            agent.receive(assign(xhsPayload(Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));

            var hit = registry.find(AgentMetrics.METRIC_CACHE_ACCESS)
                    .tag("cache", "xhs").tag("result", "hit").counter();
            assertTrue(hit != null && hit.count() == 1.0);
        }

        @Test
        @DisplayName("命中限频 → 发射 api.rate_limit.hit{api=xhs}")
        void emitsRateLimitHitMetric() {
            InMemoryRateLimitStore store = new InMemoryRateLimitStore();
            RateLimiter limiter = new RateLimiter(store,
                    Map.of(RateLimiter.API_XHS, new RateLimitRule(RateLimiter.API_XHS, 1, 60L)));
            limiter.acquireOrThrow(RateLimiter.API_XHS);
            GovernedExternalCaller caller =
                    new GovernedExternalCaller(RetryPolicy.defaultPolicy(), millis -> { }, limiter);
            XHSNoteAnalyzer analyzer = new XHSNoteAnalyzer(sourceReturning(List.of()),
                    EMPTY_CACHE, catalogWith("A-001", 4.6), caller);
            XHSAnalysisAgent agent = new XHSAnalysisAgent(analyzer, metrics);

            agent.receive(assign(xhsPayload(Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));

            var hit = registry.find(AgentMetrics.METRIC_RATE_LIMIT_HIT)
                    .tag("api", RateLimiter.API_XHS).counter();
            assertTrue(hit != null && hit.count() == 1.0);
        }

        @Test
        @DisplayName("实时检索成功：不发射缓存命中率指标（NOT_CONSULTED 不污染分母）")
        void liveSuccessEmitsNoCacheMetric() {
            XHSNote note = new XHSNote("N1", "t", "c", List.of(), 1,
                    List.of(new AttractionMention("A-001", SentimentType.POSITIVE)));
            XHSAnalysisAgent agent =
                    agentWith(sourceReturning(List.of(note)), catalogWith("A-001", 4.6));

            agent.receive(assign(xhsPayload(Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));

            var any = registry.find(AgentMetrics.METRIC_CACHE_ACCESS).counter();
            assertTrue(any == null);
        }
    }

    @Nested
    @DisplayName("降级与失败：fallback 透传 + TASK_ERROR（AC-2 / Case-3#5）")
    class DegradeAndError {

        @Test
        @DisplayName("分析降级（空检索）→ TASK_RESULT 显式 fallback=true（Case-3）")
        void fallbackPropagated() {
            XHSAnalysisAgent agent = agentWith(sourceReturning(List.of()), catalogWith("A-001", 4.6));

            Msg result = agent.receive(assign(xhsPayload(Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            assertTrue(result.isFallback());
        }

        @Test
        @DisplayName("命中限频 → TASK_RESULT(fallback=true) 而非 TASK_ERROR（降级不报错，回归 Case-2）")
        void rateLimitedReturnsFallbackResultNotError() {
            XHSNote cached = new XHSNote("C1", "t", "c", List.of(), 1,
                    List.of(new AttractionMention("A-001", SentimentType.POSITIVE)));
            XHSNoteCache cache = new XHSNoteCache() {
                @Override
                public Optional<List<XHSNote>> get(String destination) {
                    return Optional.of(List.of(cached));
                }

                @Override
                public void put(String destination, List<XHSNote> notes) {
                    // no-op
                }
            };
            InMemoryRateLimitStore store = new InMemoryRateLimitStore();
            RateLimiter limiter = new RateLimiter(store,
                    Map.of(RateLimiter.API_XHS, new RateLimitRule(RateLimiter.API_XHS, 1, 60L)));
            limiter.acquireOrThrow(RateLimiter.API_XHS);
            GovernedExternalCaller caller =
                    new GovernedExternalCaller(RetryPolicy.defaultPolicy(), millis -> { }, limiter);
            XHSNoteAnalyzer analyzer =
                    new XHSNoteAnalyzer(sourceReturning(List.of()), cache, catalogWith("A-001", 4.6), caller);
            XHSAnalysisAgent agent = new XHSAnalysisAgent(analyzer, metrics);

            Msg result = agent.receive(assign(xhsPayload(Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));

            assertEquals(MessageType.TASK_RESULT, result.type(), "限频是降级语义，绝不可退化为 TASK_ERROR");
            assertTrue(result.isFallback());
            Object payloadResult = result.payload().get(Msg.KEY_RESULT);
            assertEquals(1, assertInstanceOf(XHSAnalysisResult.class, payloadResult).candidates().size());
        }

        @Test
        @DisplayName("非 XHS_ANALYSIS 的 taskType → TASK_ERROR（INVALID_REQUEST，Case-5）")
        void wrongTaskTypeReturnsError() {
            XHSAnalysisAgent agent = agentWith(sourceReturning(List.of()), catalogWith("A-001", 4.6));

            Msg result = agent.receive(assign(Map.of(
                    Msg.KEY_TASK_TYPE, "ROUTE_PLANNING",
                    XHSAnalysisAgent.KEY_CONTEXT, Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));

            assertEquals(MessageType.TASK_ERROR, result.type());
            assertEquals(ErrorCode.INVALID_REQUEST.name(), result.payload().get(Msg.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("缺失/空白目的地 → TASK_ERROR（INVALID_REQUEST）")
        void missingDestinationReturnsError() {
            XHSAnalysisAgent agent = agentWith(sourceReturning(List.of()), catalogWith("A-001", 4.6));

            Msg result = agent.receive(assign(xhsPayload(Map.of(
                    XHSAnalysisAgent.CTX_PREFERENCES, List.of("美食")))));

            assertEquals(MessageType.TASK_ERROR, result.type());
            assertEquals(ErrorCode.INVALID_REQUEST.name(), result.payload().get(Msg.KEY_ERROR_CODE));
        }
    }

    @Nested
    @DisplayName("原生路降级：runner 返回空候选时回退确定性直路（C-012-D fix）")
    class NativeRunnerDegradation {

        @Test
        @DisplayName("runner 返回空候选（fallback=true，模拟限频/LLM 传错目的地）→ 降级确定性 analyzer 提供非空候选")
        void emptyNativeResultFallsBackToDeterministicAnalyzer() {
            // runner 模拟：LLM 调用 analyze_xhs_notes 返回 fallback=true（如限频或目的地错误）
            XHSAgentRunner runner = query -> new XHSAnalysisResult(
                    List.of(), true, "rate limited", null);

            XHSNote note = new XHSNote("N1", "t", "c", List.of("美食"), 100,
                    List.of(new AttractionMention("A-001", SentimentType.POSITIVE)));
            XHSNoteAnalyzer deterministicAnalyzer = analyzer(
                    sourceReturning(List.of(note)), catalogWith("A-001", 4.6));

            XHSAnalysisAgent agent = new XHSAnalysisAgent(runner, deterministicAnalyzer, metrics);
            Msg result = agent.receive(assign(xhsPayload(Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            XHSAnalysisResult xhsResult = assertInstanceOf(XHSAnalysisResult.class,
                    result.payload().get(Msg.KEY_RESULT));
            assertFalse(xhsResult.candidates().isEmpty(),
                    "runner 返回空候选时应降级到确定性 analyzer，candidates 不应为空");
        }

        @Test
        @DisplayName("runner 返回 null → 确定性 analyzer 提供非空候选（现有行为，回归）")
        void nullNativeResultFallsBackToDeterministicAnalyzer() {
            XHSAgentRunner runner = query -> null;

            XHSNote note = new XHSNote("N1", "t", "c", List.of("美食"), 100,
                    List.of(new AttractionMention("A-001", SentimentType.POSITIVE)));
            XHSNoteAnalyzer deterministicAnalyzer = analyzer(
                    sourceReturning(List.of(note)), catalogWith("A-001", 4.6));

            XHSAnalysisAgent agent = new XHSAnalysisAgent(runner, deterministicAnalyzer, metrics);
            Msg result = agent.receive(assign(xhsPayload(Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            XHSAnalysisResult xhsResult = assertInstanceOf(XHSAnalysisResult.class,
                    result.payload().get(Msg.KEY_RESULT));
            assertFalse(xhsResult.candidates().isEmpty(), "runner 返回 null 时应降级到确定性 analyzer");
        }

        @Test
        @DisplayName("runner 返回有效候选（fallback=false）→ 直接使用原生结果（回归）")
        void validNativeResultUsedDirectly() {
            Attraction attraction = new Attraction("B-001", "景点-B",
                    new GeoLocation(0d, 0d, null, DEST), 4.5, 90, new BigDecimal("60"), List.of());
            AttractionCandidate candidate = new AttractionCandidate(attraction, 3, 2, 1, 0);
            XHSAgentRunner runner = query -> new XHSAnalysisResult(
                    List.of(candidate), false, null, null);

            // 确定性 analyzer 的源是空的（用于区分哪条路被走了）
            XHSNoteAnalyzer deterministicAnalyzer = analyzer(
                    sourceReturning(List.of()), catalogWith("A-001", 4.6));

            XHSAnalysisAgent agent = new XHSAnalysisAgent(runner, deterministicAnalyzer, metrics);
            Msg result = agent.receive(assign(xhsPayload(Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST))));

            assertEquals(MessageType.TASK_RESULT, result.type());
            XHSAnalysisResult xhsResult = assertInstanceOf(XHSAnalysisResult.class,
                    result.payload().get(Msg.KEY_RESULT));
            assertFalse(xhsResult.candidates().isEmpty(), "runner 返回有效候选时直接使用");
            assertEquals("B-001", xhsResult.candidates().get(0).attraction().attractionId(),
                    "使用的是原生路候选 B-001，不是确定性路候选 A-001");
        }
    }
}
