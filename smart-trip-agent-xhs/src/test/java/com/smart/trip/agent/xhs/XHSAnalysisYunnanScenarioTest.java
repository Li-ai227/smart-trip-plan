package com.smart.trip.agent.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import com.smart.trip.skills.xhs.XHSNoteAnalyzer;
import com.smart.trip.skills.xhs.XHSNoteCache;
import com.smart.trip.skills.xhs.XHSNoteSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 端到端切片场景：前端表单「云南 / 古镇·美食·小众 / 2人 / 情侣」喂入 {@link XHSAnalysisAgent}，
 * 用内存桩端口（无需真实小红书/Redis）跑通 检索→聚合→评分≥3.5筛选→负面剔除→频次排序，
 * 并打印 Agent 实际返回的候选景点列表，肉眼可验。
 *
 * <p>注意：xhs 只消费表单中的 destination/preferences/headcount/style；出发日期/天数/预算
 * 归 itinerary/budget Agent，不在本切片内。</p>
 */
class XHSAnalysisYunnanScenarioTest {

    private static final String SUPERVISOR = "agent://supervisor";
    private static final String TRACE = "T-yunnan-001";
    private static final String DEST = "云南";

    /** 景点目录桩：ID → 真实评分（古镇 ≥3.5 入选；网红栈道 3.2 应被过滤）。 */
    private static AttractionCatalog yunnanCatalog() {
        Map<String, Attraction> kb = new LinkedHashMap<>();
        kb.put("A-LIJIANG", attraction("A-LIJIANG", "丽江古城", 4.7));
        kb.put("A-DALI", attraction("A-DALI", "大理古城", 4.5));
        kb.put("A-SHAXI", attraction("A-SHAXI", "沙溪古镇", 4.3));
        kb.put("A-GLASS", attraction("A-GLASS", "玉龙网红玻璃栈道", 3.2));
        kb.put("A-COMMERCIAL", attraction("A-COMMERCIAL", "古城商业街", 4.0));
        return attractionId -> Optional.ofNullable(kb.get(attractionId));
    }

    private static Attraction attraction(String id, String name, double rating) {
        return new Attraction(id, name, new GeoLocation(0d, 0d, null, DEST),
                rating, 120, new BigDecimal("80"), List.of());
    }

    /** 云南古镇/美食/小众笔记桩（逐提及情感，对齐增量 B XHSNote 契约）。 */
    private static List<XHSNote> yunnanNotes() {
        return List.of(
                new XHSNote("N1", "丽江古城慢生活", "古城里晒太阳喝茶", List.of("古镇", "美食"), 1580,
                        mentions(SentimentType.POSITIVE, "A-LIJIANG", "A-DALI")),
                new XHSNote("N2", "大理洱海小众机位", "苍山洱海拍照绝了", List.of("小众", "美食"), 990,
                        mentions(SentimentType.POSITIVE, "A-LIJIANG", "A-SHAXI")),
                new XHSNote("N3", "沙溪古镇宝藏", "没什么人的茶马古道", List.of("古镇", "小众"), 640,
                        mentions(SentimentType.POSITIVE, "A-LIJIANG", "A-DALI")),
                new XHSNote("N4", "玻璃栈道踩雷", "网红打卡但没意思", List.of("小众"), 75,
                        mentions(SentimentType.POSITIVE, "A-GLASS")),
                new XHSNote("N5", "商业街太宰客", "全是义乌小商品还贵", List.of("古镇"), 40,
                        mentions(SentimentType.NEGATIVE, "A-COMMERCIAL")));
    }

    /** 以同一情感为多个景点 ID 构造逐提及列表（整篇单一情感笔记的等价迁移）。 */
    private static List<AttractionMention> mentions(SentimentType sentiment, String... ids) {
        return Arrays.stream(ids).map(id -> new AttractionMention(id, sentiment)).toList();
    }

    private static final XHSNoteCache EMPTY_CACHE = new XHSNoteCache() {
        @Override
        public Optional<List<XHSNote>> get(String destination) {
            return Optional.empty();
        }

        @Override
        public void put(String destination, List<XHSNote> notes) {
            // 场景测试无需校验缓存写入
        }
    };

    @Test
    @DisplayName("云南/古镇·美食·小众/2人情侣 → 候选景点列表（评分≥3.5 + 负面剔除 + 频次排序）")
    void yunnanCoupleScenario() {
        XHSNoteSource source = query -> yunnanNotes();
        GovernedExternalCaller caller =
                new GovernedExternalCaller(RetryPolicy.defaultPolicy(), millis -> { });
        XHSNoteAnalyzer analyzer =
                new XHSNoteAnalyzer(source, EMPTY_CACHE, yunnanCatalog(), caller);
        AgentMetrics metrics = new AgentMetrics(new SimpleMeterRegistry());
        XHSAnalysisAgent agent = new XHSAnalysisAgent(analyzer, metrics);

        // 前端表单 → A2A context（情侣 → TravelStyle.COUPLE；日期/天数/预算不归 xhs）。
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(XHSAnalysisAgent.CTX_DESTINATION, "云南");
        context.put(XHSAnalysisAgent.CTX_PREFERENCES, List.of("古镇", "美食", "小众"));
        context.put(XHSAnalysisAgent.CTX_HEADCOUNT, 2);
        context.put(XHSAnalysisAgent.CTX_STYLE, "COUPLE");
        Map<String, Object> payload = Map.of(
                Msg.KEY_TASK_TYPE, XHSAnalysisAgent.TASK_TYPE,
                XHSAnalysisAgent.KEY_CONTEXT, context);

        Msg result = agent.receive(
                Msg.taskAssign(SUPERVISOR, "agent://" + XHSAnalysisAgent.AGENT_ID, TRACE, payload));

        // ---- 肉眼可验：打印 Agent 实际返回 ----
        XHSAnalysisResult xhsResult = assertInstanceOf(XHSAnalysisResult.class, result.payload().get(Msg.KEY_RESULT));
        List<AttractionCandidate> candidates = xhsResult.candidates();
        System.out.println("\n========== XHSAnalysisAgent 返回（云南/情侣/古镇·美食·小众）==========");
        System.out.println("消息类型=" + result.type() + "  fallback=" + result.isFallback()
                + "  traceId=" + result.traceId());
        System.out.println("候选数=" + candidates.size() + "（共喂入 5 篇笔记、5 个景点）");
        System.out.printf("%-4s %-16s %-6s %-8s %-12s%n", "排名", "景点", "评分", "提及次数", "情感(正/中/负)");
        int rank = 1;
        for (AttractionCandidate c : candidates) {
            System.out.printf("%-4d %-16s %-6.1f %-8d %d/%d/%d%n",
                    rank++, c.attraction().name(), c.attraction().rating(), c.mentionCount(),
                    c.positiveMentions(), c.neutralMentions(), c.negativeMentions());
        }
        System.out.println("被过滤：玉龙网红玻璃栈道(评分3.2<3.5) / 古城商业街(负面占比100%>50%)");
        System.out.println("====================================================================\n");

        // ---- 断言：业务规则确实生效（可提交的回归用例）----
        assertEquals(MessageType.TASK_RESULT, result.type());
        assertFalse(result.isFallback(), "有效笔记+合格候选，应为实时成功而非降级");
        List<String> names = candidates.stream()
                .map(c -> c.attraction().name())
                .toList();
        assertEquals(List.of("丽江古城", "大理古城", "沙溪古镇"), names,
                "频次降序：丽江(3)>大理(2)>沙溪(1)；网红栈道(<3.5)与商业街(负面)被剔除");
    }
}
