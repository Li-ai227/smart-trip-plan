package com.smart.trip.agent.itinerary;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.skills.itinerary.ItineraryAgentRunner;
import com.smart.trip.skills.itinerary.ItineraryDesignQuery;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.itinerary.ItineraryDesignService;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.RoutePlanResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 行程编排 Agent（{@code agent://itinerary-designer}，C-008 AC-1/AC-2/AC-7）。
 *
 * <p>仅受理 {@code TASK_ASSIGN(taskType=ITINERARY_DESIGN)}，把 {@code context} 解析为
 * {@link ItineraryDesignQuery}，装配为 {@code TASK_RESULT}（降级显式 {@code fallback=true}）。
 * Agent 间仅经 A2A 通信、不直接依赖其它 Agent（工程结构.md R1）。</p>
 *
 * <p>增量 D（AgentScope 原生 A2A 适配）：移除废弃 {@code BaseAgent}/{@code AgentId}/{@code AgentReply}
 * 依赖，{@code receive(Msg)} 模板方法内联，用 {@link Msg#taskResult}/{@link Msg#taskError} 直接构造
 * 结果消息。</p>
 *
 * <p><b>增量 B 原生接缝</b>：注入 {@link ItineraryAgentRunner} 后，先走原生 HarnessAgent 路，
 * 失败/无权威产物降级回确定性 {@link ItineraryDesignService} 直路（{@code fallback=true}）。
 * 未注入 runner 时为纯确定性直路（增量 A 行为）。业务不变量（六项强规则）始终由确定性核心强制，
 * 不交给 LLM 裁决。</p>
 */
public final class ItineraryAgent {

    /** Agent 裸标识。 */
    public static final String AGENT_ID = "itinerary-designer";

    /** Agent URI（A2A 通信地址）。 */
    public static final String AGENT_URI = "agent://" + AGENT_ID;

    /** 受理的任务类型。 */
    public static final String TASK_TYPE = "ITINERARY_DESIGN";

    /** payload 键：上下文。 */
    public static final String KEY_CONTEXT = "context";

    /** context 键：用户规划请求（必填）。 */
    public static final String CTX_REQUEST = "request";

    /** context 键：候选景点列表。 */
    public static final String CTX_ATTRACTIONS = "attractions";

    /** context 键：路线规划结果。 */
    public static final String CTX_ROUTE_RESULT = "routeResult";

    private static final String ERROR_CODE_AGENT_FAILURE = "AGENT_FAILURE";
    private static final System.Logger LOG = System.getLogger(ItineraryAgent.class.getName());

    private final ItineraryDesignService designService;
    private final AgentMetrics metrics;
    private final ItineraryAgentRunner runner;
    private final AtomicReference<AgentStatus> status = new AtomicReference<>(AgentStatus.IDLE);

    /**
     * 构造 ItineraryAgent（纯确定性直路，增量 A 行为）。
     *
     * @param designService 行程编排 Skill，不可为 null
     * @param metrics       指标埋点，不可为 null
     */
    public ItineraryAgent(ItineraryDesignService designService, AgentMetrics metrics) {
        this(null, designService, metrics);
    }

    /**
     * 构造 ItineraryAgent（增量 B 原生路）：经 {@link ItineraryAgentRunner} 驱动 HarnessAgent
     * 执行行程编排，失败/无权威产物降级回确定性 {@link ItineraryDesignService} 直路。
     *
     * @param runner        原生 Agent 运行器，可为 null（null 时走纯确定性直路）
     * @param designService 行程编排 Skill（确定性核心 + 降级兜底），不可为 null
     * @param metrics       指标埋点，不可为 null
     */
    public ItineraryAgent(ItineraryAgentRunner runner, ItineraryDesignService designService,
                          AgentMetrics metrics) {
        if (designService == null) {
            throw new InvalidRequestException("ItineraryAgent designService 不可为 null");
        }
        if (metrics == null) {
            throw new InvalidRequestException("ItineraryAgent metrics 不可为 null");
        }
        this.runner = runner;
        this.designService = designService;
        this.metrics = metrics;
    }

    /**
     * 返回 Agent URI（A2A 通信地址）。
     *
     * @return {@code agent://itinerary-designer}
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
                    "ItineraryAgent 仅受理 TASK_ASSIGN: "
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
        ItineraryDesignQuery query = parseQuery(assign);

        long startNanos = System.nanoTime();
        Resolved resolved = resolve(query);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        metrics.recordCallLatency(AGENT_URI, TASK_TYPE, elapsedMillis);

        boolean fallback = resolved.result().fallback() || resolved.llmDegraded();
        return Msg.taskResult(AGENT_URI, assign.from(), assign.traceId(),
                resolved.result(), fallback);
    }

    private Resolved resolve(ItineraryDesignQuery query) {
        if (runner != null) {
            try {
                ItineraryDesignResult nativeResult = runner.run(query);
                if (nativeResult != null) {
                    return new Resolved(nativeResult, false);
                }
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.WARNING,
                        "ItineraryAgent 原生路失败，降级确定性直路: agent={0}, reason={1}",
                        AGENT_URI, ex.getMessage());
                metrics.incrementCallError(AGENT_URI);
            }
            return new Resolved(designService.design(query), true);
        }
        return new Resolved(designService.design(query), false);
    }

    private record Resolved(ItineraryDesignResult result, boolean llmDegraded) {
    }

    private static void requireTaskType(Msg assign) {
        Object taskType = assign.payload().get(Msg.KEY_TASK_TYPE);
        if (!TASK_TYPE.equals(taskType)) {
            throw new InvalidRequestException(
                    "ItineraryAgent 仅受理 taskType=ITINERARY_DESIGN: " + taskType);
        }
    }

    @SuppressWarnings("unchecked")
    private static ItineraryDesignQuery parseQuery(Msg assign) {
        Map<String, Object> context = asMap(assign.payload().get(KEY_CONTEXT));
        Object reqObj = context.get(CTX_REQUEST);
        if (!(reqObj instanceof TripPlanRequest request)) {
            throw new InvalidRequestException("ITINERARY_DESIGN context.request 不可为空或类型错误");
        }
        List<Attraction> attractions = asList(context.get(CTX_ATTRACTIONS));
        RoutePlanResult routeResult = context.get(CTX_ROUTE_RESULT) instanceof RoutePlanResult rr
                ? rr : null;
        return new ItineraryDesignQuery(request, attractions, routeResult);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Attraction> asList(Object value) {
        return value instanceof List<?> list ? (List<Attraction>) list : List.of();
    }
}
