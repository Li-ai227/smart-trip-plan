package com.smart.trip.skills.xhs;

import com.smart.trip.common.util.JsonUtils;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * XHS 分析业务工具（C-006 增量 C / AC-原生化）：把确定性 {@link XHSNoteAnalyzer} 暴露为 AgentScope
 * {@code @Tool}，供 {@code HarnessAgent}（主 deepseek-v4-pro / 降级 qwen3-max）在 ReAct 循环中调用。
 *
 * <p><b>不变量留在 Java（绝不交给 LLM 裁决）</b>：评分阈值 {@code rating≥3.5}、负面情感剔除、提及频次
 * 排序、检索故障/限频/空结果的 {@code fallback} 降级与缓存,全部由既有确定性 {@link XHSNoteAnalyzer}
 * 强制——本工具只是其门面。LLM 的职责是理解请求、调用本工具、解释结果,而非伪造或放宽候选。</p>
 *
 * <p><b>权威产物</b>：每次调用把确定性 {@link XHSAnalysisResult}（候选 + fallback + 遥测）捕获到
 * {@link #lastResult()},供 Agent 适配层回写 A2A {@code TASK_RESULT}——不依赖解析 LLM 自由文本,
 * 因此 LLM 非确定性不会污染候选正确性。</p>
 */
public final class XHSAnalysisTools {

    private final XHSNoteAnalyzer analyzer;
    private final AtomicReference<XHSAnalysisResult> lastResult = new AtomicReference<>();

    /**
     * @param analyzer 确定性分析器(承载全部业务不变量与降级),非 null
     */
    public XHSAnalysisTools(XHSNoteAnalyzer analyzer) {
        if (analyzer == null) {
            throw new IllegalArgumentException("analyzer 不可为 null");
        }
        this.analyzer = analyzer;
    }

    /**
     * 检索并分析指定目的地的小红书旅游笔记,产出满足业务不变量的候选景点。
     *
     * @param destination 目的地自然语言(如 成都/云南大理/想去大理玩),非空白
     * @param preferences 偏好标签,逗号分隔,可空(如 "美食,小众")
     * @return 候选景点摘要 JSON(name/rating/mentionCount/情感计数)+ fallback 标记 + 可读说明
     */
    @Tool(name = "analyze_xhs_notes",
            description = "检索并分析指定目的地的小红书旅游笔记，产出满足业务不变量"
                    + "（评分≥3.5、负面情感占比过高剔除、按提及频次排序）的候选景点。"
                    + "评分阈值与情感筛选已在工具内强制，禁止在工具外伪造、放宽或新增候选。")
    public String analyzeXhsNotes(
            @ToolParam(name = "destination", description = "目的地自然语言，如 成都/云南大理/想去大理玩") String destination,
            @ToolParam(name = "preferences", required = false,
                    description = "偏好标签，逗号分隔，可空，如 美食,小众") String preferences) {
        XHSAnalysisResult result =
                analyzer.analyze(XHSAnalysisQuery.of(destination, parsePreferences(preferences)));
        lastResult.set(result);
        com.smart.trip.skills.supervisor.SupervisorNativeRunContext ctx =
                com.smart.trip.skills.supervisor.SupervisorHarnessAgentFactory.activeRunContext();
        if (ctx != null) { ctx.setXhsResult(result); }
        return toJson(result);
    }

    /**
     * 取最近一次工具调用产出的确定性结果(权威候选 + fallback + 遥测),Agent 适配层据此回写
     * {@code TASK_RESULT}。尚未调用则返回 {@code null}。
     *
     * @return 最近一次 {@link XHSAnalysisResult},或 {@code null}
     */
    public XHSAnalysisResult lastResult() {
        return lastResult.get();
    }

    private static List<String> parsePreferences(String preferences) {
        if (preferences == null || preferences.isBlank()) {
            return List.of();
        }
        List<String> prefs = new ArrayList<>();
        for (String token : Arrays.asList(preferences.split(","))) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                prefs.add(trimmed);
            }
        }
        return prefs;
    }

    private static String toJson(XHSAnalysisResult result) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (AttractionCandidate candidate : result.candidates()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("attractionId", candidate.attraction().attractionId());
            view.put("name", candidate.attraction().name());
            view.put("rating", candidate.attraction().rating());
            // 补全 Attraction 业务字段：下游 plan_routes / design_itinerary 解析 Attraction record 时需要
            // durationMin：RoutePlanningService 用于路线时长估算；为 0 会导致"景点数不足 2"误判
            // ticketPrice：BudgetCalculationService 门票拆分依赖
            // tags：行程编排偏好过滤依赖
            view.put("durationMin", candidate.attraction().durationMin());
            view.put("ticketPrice", candidate.attraction().ticketPrice());
            view.put("tags", candidate.attraction().tags());
            view.put("mentionCount", candidate.mentionCount());
            view.put("positive", candidate.positiveMentions());
            view.put("neutral", candidate.neutralMentions());
            view.put("negative", candidate.negativeMentions());
            candidates.add(view);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidates", candidates);
        payload.put("fallback", result.fallback());
        payload.put("message", result.message());
        return JsonUtils.toJson(payload);
    }
}
