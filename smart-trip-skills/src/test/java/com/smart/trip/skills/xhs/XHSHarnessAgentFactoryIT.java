package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RetryPolicy;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/**
 * {@link XHSHarnessAgentFactory} 端到端集成测试（C-006 增量 C / ADR-009）：真实 {@code HarnessAgent} +
 * 主 LLM deepseek-v4-pro（qwen3-max 作运行期降级）+ SKILL.md 驱动 XHS 分析，断言**权威候选仍受确定性
 * 不变量约束**（评分≥3.5、网红低评分被剔除）。
 *
 * <p>仅当存在 {@code DEEPSEEK_API_KEY}（主 LLM）时运行；否则整类自动跳过——绝不静默假装通过。
 * 这是 XHS 链真正调用大模型的验证点。</p>
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class XHSHarnessAgentFactoryIT {

    private static final String DEST = "云南";

    /** 云南景点目录桩：古镇 ≥3.5 入选；网红玻璃栈道 3.2 应被确定性工具剔除。 */
    private static AttractionCatalog yunnanCatalog() {
        Map<String, Attraction> kb = new LinkedHashMap<>();
        kb.put("A-LIJIANG", attraction("A-LIJIANG", "丽江古城", 4.7));
        kb.put("A-DALI", attraction("A-DALI", "大理古城", 4.5));
        kb.put("A-GLASS", attraction("A-GLASS", "玉龙网红玻璃栈道", 3.2));
        return id -> Optional.ofNullable(kb.get(id));
    }

    private static Attraction attraction(String id, String name, double rating) {
        return new Attraction(id, name, new GeoLocation(0d, 0d, null, DEST),
                rating, 120, new BigDecimal("80"), List.of());
    }

    private static XHSNote note(String id, SentimentType sentiment, String... ids) {
        List<AttractionMention> mentions = Arrays.stream(ids)
                .map(a -> new AttractionMention(a, sentiment)).toList();
        return new XHSNote(id, "标题-" + id, "正文", List.of("古镇"), 800, mentions);
    }

    private static XHSNoteAnalyzer yunnanAnalyzer() {
        XHSNoteSource source = q -> List.of(
                note("N1", SentimentType.POSITIVE, "A-LIJIANG", "A-DALI"),
                note("N2", SentimentType.POSITIVE, "A-LIJIANG"),
                note("N3", SentimentType.POSITIVE, "A-GLASS"));
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
        return new XHSNoteAnalyzer(source, cache, yunnanCatalog(),
                new GovernedExternalCaller(RetryPolicy.defaultPolicy(), ms -> { }));
    }

    @Test
    @DisplayName("真实 HarnessAgent（主 deepseek-v4-pro/降级 qwen3-max）跑通 XHS 分析，候选均 rating≥3.5（网红 3.2 被剔除）")
    void liveHarnessAgentProducesInvariantRespectingCandidates(@TempDir Path workspace) throws Exception {
        try (XHSHarnessAgentFactory factory =
                     XHSHarnessAgentFactory.fromEnvironment(yunnanAnalyzer(), workspace)) {
            XHSAnalysisResult result = factory.run(XHSAnalysisQuery.of(DEST, List.of("古镇", "小众")));

            assertNotNull(result, "真实 HarnessAgent 应产出权威结果（LLM 调用了 analyze_xhs_notes 工具）");
            System.out.println("[IT] 候选 = " + result.candidates().stream()
                    .map(c -> c.attraction().name() + "(" + c.attraction().rating() + ")").toList());
            assertTrue(result.candidates().stream()
                            .allMatch(c -> c.attraction().rating() >= XHSNoteAnalyzer.MIN_RECOMMEND_RATING),
                    "确定性不变量：所有候选评分应 ≥3.5（网红 3.2 必被剔除）");
            assertTrue(result.candidates().stream()
                            .noneMatch(c -> "A-GLASS".equals(c.attraction().attractionId())),
                    "评分 3.2 的网红栈道不得入选");
        }
    }
}
