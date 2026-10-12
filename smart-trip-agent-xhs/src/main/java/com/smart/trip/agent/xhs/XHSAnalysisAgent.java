package com.smart.trip.agent.xhs;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.skills.governance.RateLimiter;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.xhs.XHSAgentRunner;
import com.smart.trip.skills.xhs.XHSAnalysisQuery;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import com.smart.trip.skills.xhs.XHSNoteAnalyzer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 小红书笔记分析 Agent（{@code agent://xhs-analyst}，C-006 AC-1/AC-2/AC-7/AC-17~AC-20）。
 *
 * <p>仅受理 {@code TASK_ASSIGN(taskType=XHS_ANALYSIS)}，把 {@code context} 解析为
 * {@link XHSAnalysisQuery}，装配为 {@code TASK_RESULT}（降级显式 {@code fallback=true}）。
 * Agent 间仅经 A2A 通信、不直接依赖其它 Agent（工程结构.md R1）。</p>
 *
 * <p>增量 D（AgentScope 原生 A2A 适配）：移除废弃 {@code BaseAgent}/{@code AgentId}/{@code AgentReply}
 * 依赖，{@code receive(Msg)} 模板方法内联，用 {@link Msg#taskResult}/{@link Msg#taskError} 直接构造
 * 结果消息，agent 元数据经 {@code TripAgentCardFactory} 构建 {@code ConfigurableAgentCard} 接入
 * 框架 A2A 服务发现。</p>
 *
 * <p>增量 C（AgentScope 原生化）：注入 {@link XHSAgentRunner} 时走原生路——由 {@code HarnessAgent}
 * （主 deepseek-v4-pro / 降级 qwen3-max + {@code @Tool} + SKILL.md）驱动分析，原生路失败/无权威产物时
 * 降级回确定性 {@link XHSNoteAnalyzer} 直路（{@code fallback=true}）。未注入 runner 时为纯确定性直路
 * （增量 A/B 行为）。业务不变量（评分≥3.5、负面剔除、降级）始终由确定性核心强制，不交给 LLM 裁决。</p>
 */
public final class XHSAnalysisAgent {

    /** Agent 裸标识（规范化为 {@code agent://xhs-analyst}）。 */
    public static final String AGENT_ID = "xhs-analyst";

    /** Agent URI（A2A 通信地址）。 */
    public static final String AGENT_URI = "agent://" + AGENT_ID;

    /** 受理的任务类型（接口协议.md §3）。 */
    public static final String TASK_TYPE = "XHS_ANALYSIS";

    /** payload 键：上下文。 */
    public static final String KEY_CONTEXT = "context";

    /** context 键：目的地（必填）。 */
    public static final String CTX_DESTINATION = "destination";

    /** context 键：偏好标签。 */
    public static final String CTX_PREFERENCES = "preferences";

    /** context 键：出行人数。 */
    public static final String CTX_HEADCOUNT = "headcount";

    /** context 键：出行风格。 */
    public static final String CTX_STYLE = "style";

    private static final String ERROR_CODE_AGENT_FAILURE = "AGENT_FAILURE";

    private final XHSNoteAnalyzer analyzer;
    private final AgentMetrics metrics;
    private final XHSAgentRunner runner;
    private final AtomicReference<AgentStatus> status = new AtomicReference<>(AgentStatus.IDLE);

    /**
     * 构造 XHSAnalysisAgent（确定性直路，无 LLM；增量 A/B 行为）。
     *
     * @param analyzer 笔记分析 Skill，不可为 null
     * @param metrics  指标埋点，不可为 null
     */
    public XHSAnalysisAgent(XHSNoteAnalyzer analyzer, AgentMetrics metrics) {
        this(null, analyzer, metrics);
    }

    /**
     * 构造 XHSAnalysisAgent（增量 C 原生路）：经 {@link XHSAgentRunner} 驱动 {@code HarnessAgent}
     * （主 deepseek-v4-pro / 降级 qwen3-max）跑 XHS 分析，{@code runner} 失败/无权威产物时降级回确定性直路。
     *
     * @param runner   原生 Agent 运行器（驱动 HarnessAgent），可为 null（null 时退化为纯确定性直路）
     * @param analyzer 笔记分析 Skill（确定性核心 + 降级兜底），不可为 null
     * @param metrics  指标埋点，不可为 null
     */
    public XHSAnalysisAgent(XHSAgentRunner runner, XHSNoteAnalyzer analyzer, AgentMetrics metrics) {
        if (analyzer == null) {
            throw new InvalidRequestException("XHSAnalysisAgent analyzer 不可为 null");
        }
        if (metrics == null) {
            throw new InvalidRequestException("XHSAnalysisAgent metrics 不可为 null");
        }
        this.analyzer = analyzer;
        this.metrics = metrics;
        this.runner = runner;
    }

    /**
     * 返回 Agent URI（A2A 通信地址）。
     *
     * @return {@code agent://xhs-analyst}
     */
    public String agentUri() {
        return AGENT_URI;
    }

    /**
     * 返回当前生命周期状态。
     *
     * @return Agent 运行状态
     */
    public AgentStatus status() {
        return status.get();
    }

    /**
     * 受理一条委派消息并返回结果消息（A2A 收发统一入口）。
     *
     * @param assign 委派消息，必须为 {@code TASK_ASSIGN}
     * @return {@code TASK_RESULT}（正常/降级）或 {@code TASK_ERROR}（失败），{@code traceId} 透传
     * @throws InvalidRequestException 当消息为 null 或类型非 {@code TASK_ASSIGN}
     */
    public Msg receive(Msg assign) {
        if (assign == null || assign.type() != MessageType.TASK_ASSIGN) {
            throw new InvalidRequestException(
                    "XHSAnalysisAgent 仅受理 TASK_ASSIGN: "
                            + (assign == null ? "null" : String.valueOf(assign.type())));
        }
        status.set(AgentStatus.THINKING);
        try {
            status.set(AgentStatus.ACTING);
            return handleTaskAssign(assign);
        } catch (BaseException ex) {
            return Msg.taskError(AGENT_URI, assign.from(), assign.traceId(),
                    ex.getCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            return Msg.taskError(AGENT_URI, assign.from(), assign.traceId(),
                    ERROR_CODE_AGENT_FAILURE, String.valueOf(ex.getMessage()));
        } finally {
            status.set(AgentStatus.DONE);
        }
    }

    private Msg handleTaskAssign(Msg assign) {
        requireTaskType(assign);
        XHSAnalysisQuery query = parseQuery(assign);

        long startNanos = System.nanoTime();
        Resolved resolved = resolve(query);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        metrics.recordCallLatency(AGENT_URI, TASK_TYPE, elapsedMillis);
        emitTelemetry(resolved.result().telemetry());

        boolean fallback = resolved.result().fallback() || resolved.llmDegraded();
        return Msg.taskResult(AGENT_URI, assign.from(), assign.traceId(),
                resolved.result(), fallback);
    }

    /**
     * 解析候选：有 {@code runner} 则先走原生 HarnessAgent 路，失败/无权威产物降级回确定性直路；
     * 无 {@code runner} 则直接走确定性直路（增量 A/B 行为）。
     */
    private Resolved resolve(XHSAnalysisQuery query) {
        if (runner != null) {
            try {
                XHSAnalysisResult nativeResult = runner.run(query);
                // 有权威候选才采信原生路；null 或空候选（如限频/LLM 传错目的地）降级确定性直路
                if (nativeResult != null && !nativeResult.candidates().isEmpty()) {
                    return new Resolved(nativeResult, false);
                }
            } catch (Exception ex) {
                metrics.incrementCallError(AGENT_URI);
            }
            return new Resolved(analyzer.analyze(query), true);
        }
        return new Resolved(analyzer.analyze(query), false);
    }

    /** 候选解析产物：确定性结果 + 是否因原生路失败而降级。 */
    private record Resolved(XHSAnalysisResult result, boolean llmDegraded) {
    }

    /**
     * 派发 AC-7 可观测指标：限频命中 {@code api.rate_limit.hit}、缓存命中率 {@code memory.cache.hit}
     * （仅在降级真正咨询缓存时计入，实时成功的 {@code NOT_CONSULTED} 不污染命中率分母）。
     */
    private void emitTelemetry(XHSAnalysisResult.Telemetry telemetry) {
        if (telemetry.rateLimited()) {
            metrics.incrementRateLimitHit(RateLimiter.API_XHS);
        }
        switch (telemetry.cacheOutcome()) {
            case HIT -> metrics.recordCacheAccess(RateLimiter.API_XHS, true);
            case MISS -> metrics.recordCacheAccess(RateLimiter.API_XHS, false);
            default -> { /* NOT_CONSULTED：不计入命中率分母 */ }
        }
    }

    /** 校验仅受理 {@code taskType=XHS_ANALYSIS}，否则快速失败（→ TASK_ERROR，编码规范 §5）。 */
    private static void requireTaskType(Msg assign) {
        Object taskType = assign.payload().get(Msg.KEY_TASK_TYPE);
        if (!TASK_TYPE.equals(taskType)) {
            throw new InvalidRequestException(
                    "XHSAnalysisAgent 仅受理 taskType=XHS_ANALYSIS: " + taskType);
        }
    }

    /** 解析 {@code context} 为分析请求；目的地缺失/空白由 {@link XHSAnalysisQuery} 防御式快速失败。 */
    private static XHSAnalysisQuery parseQuery(Msg assign) {
        Map<String, Object> context = asMap(assign.payload().get(KEY_CONTEXT));
        String destination = asString(context.get(CTX_DESTINATION));
        if (destination == null || destination.isBlank()) {
            throw new InvalidRequestException("XHS_ANALYSIS context.destination 不可为空白");
        }
        return new XHSAnalysisQuery(destination, asStringList(context.get(CTX_PREFERENCES)),
                asInt(context.get(CTX_HEADCOUNT)), asStyle(context.get(CTX_STYLE)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static int asInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    /** 容错解析出行风格：非法/缺失值归一为 null（向后兼容，未知字段忽略而非失败）。 */
    private static TravelStyle asStyle(Object value) {
        if (!(value instanceof String s) || s.isBlank()) {
            return null;
        }
        try {
            return TravelStyle.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
