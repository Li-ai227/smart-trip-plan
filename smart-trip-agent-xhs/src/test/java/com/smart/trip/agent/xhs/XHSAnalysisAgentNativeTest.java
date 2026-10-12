package com.smart.trip.agent.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RetryPolicy;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.xhs.AttractionCandidate;
import com.smart.trip.skills.xhs.AttractionCatalog;
import com.smart.trip.skills.xhs.CacheOutcome;
import com.smart.trip.skills.xhs.XHSAgentRunner;
import com.smart.trip.skills.xhs.XHSAnalysisQuery;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import com.smart.trip.skills.xhs.XHSNoteAnalyzer;
import com.smart.trip.skills.xhs.XHSNoteCache;
import com.smart.trip.skills.xhs.XHSNoteSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link XHSAnalysisAgent} 原生路（增量 C）契约测试：注入桩 {@link XHSAgentRunner}（确定性、无真实 LLM），
 * 验证 ①原生路成功 → 透传权威候选、{@code fallback=false}；②原生路抛异常 → 降级回确定性直路、
 * {@code fallback=true}、不抛裸异常；③原生路返回 {@code null}（LLM 未产出权威结果）→ 同样降级回直路。
 *
 * <p>不触网、不依赖 LLM 密钥——真实 HarnessAgent（主 deepseek-v4-pro/降级 qwen3-max）链路由 skills 的 env-gated 集成测试覆盖。</p>
 */
class XHSAnalysisAgentNativeTest {

    private static final String DEST = "成都";
    private static final String SUPERVISOR = "agent://supervisor";

    private final AgentMetrics metrics = new AgentMetrics(new SimpleMeterRegistry());

    /** 确定性兜底分析器：桩源返回一篇命中 A-001(4.6) 的笔记。 */
    private XHSNoteAnalyzer fallbackAnalyzer() {
        XHSNoteSource source = q -> List.of(new XHSNote("N1", "t", "c", List.of(), 1,
                List.of(new AttractionMention("A-001", SentimentType.POSITIVE))));
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
        AttractionCatalog catalog = id -> "A-001".equals(id)
                ? Optional.of(new Attraction("A-001", "宽窄巷子",
                        new GeoLocation(0d, 0d, null, DEST), 4.6, 90, new BigDecimal("60"), List.of()))
                : Optional.empty();
        return new XHSNoteAnalyzer(source, cache, catalog,
                new GovernedExternalCaller(RetryPolicy.defaultPolicy(), ms -> { }));
    }

    private Msg assign() {
        return Msg.taskAssign(SUPERVISOR, "agent://xhs-analyst", "T-native-1",
                Map.of(Msg.KEY_TASK_TYPE, XHSAnalysisAgent.TASK_TYPE,
                        XHSAnalysisAgent.KEY_CONTEXT,
                        Map.of(XHSAnalysisAgent.CTX_DESTINATION, DEST)));
    }

    @SuppressWarnings("unchecked")
    private static List<AttractionCandidate> candidates(Msg result) {
        XHSAnalysisResult xhsResult = (XHSAnalysisResult) result.payload().get(Msg.KEY_RESULT);
        return xhsResult.candidates();
    }

    @Test
    @DisplayName("原生路成功：透传 HarnessAgent 产出的权威候选，fallback=false")
    void nativePathSuccess() {
        Attraction a = new Attraction("A-NATIVE", "锦里", new GeoLocation(0d, 0d, null, DEST),
                4.8, 120, new BigDecimal("0"), List.of());
        XHSAnalysisResult nativeResult = new XHSAnalysisResult(
                List.of(new AttractionCandidate(a, 3, 3, 0, 0)), false, null,
                new XHSAnalysisResult.Telemetry(false, CacheOutcome.NOT_CONSULTED));
        XHSAgentRunner runner = query -> nativeResult;

        XHSAnalysisAgent agent = new XHSAnalysisAgent(runner, fallbackAnalyzer(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertFalse(result.isFallback());
        assertEquals(1, candidates(result).size());
        assertEquals("A-NATIVE", candidates(result).get(0).attraction().attractionId());
    }

    @Test
    @DisplayName("原生路抛异常：降级回确定性直路，fallback=true，不抛裸异常")
    void nativePathFailureFallsBackToDeterministic() {
        XHSAgentRunner runner = query -> {
            throw new java.util.concurrent.TimeoutException("llm slow / no key");
        };

        XHSAnalysisAgent agent = new XHSAnalysisAgent(runner, fallbackAnalyzer(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertTrue(result.isFallback(), "原生路失败应显式降级 fallback=true");
        // 降级直路命中确定性兜底候选 A-001
        assertEquals(1, candidates(result).size());
        assertEquals("A-001", candidates(result).get(0).attraction().attractionId());
    }

    @Test
    @DisplayName("原生路返回 null（无权威产物）：同样降级回确定性直路，fallback=true")
    void nativePathNullFallsBack() {
        XHSAgentRunner runner = query -> null;

        XHSAnalysisAgent agent = new XHSAnalysisAgent(runner, fallbackAnalyzer(), metrics);
        Msg result = agent.receive(assign());

        assertEquals(MessageType.TASK_RESULT, result.type());
        assertTrue(result.isFallback());
        assertEquals("A-001", candidates(result).get(0).attraction().attractionId());
    }
}
