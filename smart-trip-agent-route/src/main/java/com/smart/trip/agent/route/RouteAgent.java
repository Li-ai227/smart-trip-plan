package com.smart.trip.agent.route;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.RouteAgentRunner;
import com.smart.trip.skills.route.RoutePlanQuery;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.route.RoutePlanningService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 路线规划 Agent（{@code agent://route-planner}，C-007 AC-1/AC-2/AC-7/AC-13/AC-14）。
 *
 * <p>仅受理 {@code TASK_ASSIGN(taskType=ROUTE_PLANNING)}，把 {@code context} 解析为
 * {@link RoutePlanQuery}，装配为 {@code TASK_RESULT}（降级显式 {@code fallback=true}）。
 * Agent 间仅经 A2A 通信、不直接依赖其它 Agent（工程结构.md R1）。</p>
 *
 * <p>增量 D（AgentScope 原生 A2A 适配）：移除废弃 {@code BaseAgent}/{@code AgentId}/{@code AgentReply}
 * 依赖，{@code receive(Msg)} 模板方法内联，用 {@link Msg#taskResult}/{@link Msg#taskError} 直接构造
 * 结果消息。</p>
 *
 * <p><b>增量 C 原生接缝</b>：注入 {@link RouteAgentRunner} 后，先走原生 HarnessAgent 路
 * （主 deepseek-v4-pro / 降级 qwen3-max + {@code @Tool plan_routes}），失败/无权威产物
 * 降级回确定性 {@link RoutePlanningService} 直路（{@code fallback=true}）。未注入 runner 时为纯确定性直路
 * （增量 A/B 行为）。业务不变量（耗时≤120min 标记、三类降级、限频）始终由确定性核心强制，不交给 LLM 裁决。</p>
 */
public final class RouteAgent {

    /** Agent 裸标识。 */
    public static final String AGENT_ID = "route-planner";

    /** Agent URI（A2A 通信地址）。 */
    public static final String AGENT_URI = "agent://" + AGENT_ID;

    /** 受理的任务类型。 */
    public static final String TASK_TYPE = "ROUTE_PLANNING";

    /** payload 键：上下文。 */
    public static final String KEY_CONTEXT = "context";

    /** context 键：目的地（必填）。 */
    public static final String CTX_DESTINATION = "destination";

    /** context 键：景点列表。 */
    public static final String CTX_ATTRACTIONS = "attractions";

    /** context 键：交通方式。 */
    public static final String CTX_TRANSPORT_MODE = "transportMode";

    private static final String ERROR_CODE_AGENT_FAILURE = "AGENT_FAILURE";
    private static final System.Logger LOG = System.getLogger(RouteAgent.class.getName());

    private final RoutePlanningService planningService;
    private final AgentMetrics metrics;
    private final RouteAgentRunner runner;
    private final AtomicReference<AgentStatus> status = new AtomicReference<>(AgentStatus.IDLE);

    /**
     * 构造 RouteAgent（纯确定性直路，增量 A/B 行为）。
     *
     * @param planningService 路线规划 Skill，不可为 null
     * @param metrics         指标埋点，不可为 null
     */
    public RouteAgent(RoutePlanningService planningService, AgentMetrics metrics) {
        this(null, planningService, metrics);
    }

    /**
     * 构造 RouteAgent（增量 C 原生路）：经 {@link RouteAgentRunner} 驱动 {@code HarnessAgent}
     * 执行路线规划，失败/无权威产物降级回确定性 {@link RoutePlanningService} 直路。
     *
     * @param runner          原生 Agent 运行器，可为 null（null 时走纯确定性直路）
     * @param planningService 路线规划 Skill（确定性核心 + 降级兜底），不可为 null
     * @param metrics         指标埋点，不可为 null
     */
    public RouteAgent(RouteAgentRunner runner, RoutePlanningService planningService,
                      AgentMetrics metrics) {
        if (planningService == null) {
            throw new InvalidRequestException("RouteAgent planningService 不可为 null");
        }
        if (metrics == null) {
            throw new InvalidRequestException("RouteAgent metrics 不可为 null");
        }
        this.runner = runner;
        this.planningService = planningService;
        this.metrics = metrics;
    }

    /**
     * 返回 Agent URI（A2A 通信地址）。
     *
     * @return {@code agent://route-planner}
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
                    "RouteAgent 仅受理 TASK_ASSIGN: "
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
        RoutePlanQuery query = parseQuery(assign);

        long startNanos = System.nanoTime();
        Resolved resolved = resolve(query);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        metrics.recordCallLatency(AGENT_URI, TASK_TYPE, elapsedMillis);

        boolean fallback = resolved.result().fallback() || resolved.llmDegraded();
        return Msg.taskResult(AGENT_URI, assign.from(), assign.traceId(),
                resolved.result(), fallback);
    }

    /**
     * 解析路线：有 {@code runner} 则先走原生 HarnessAgent 路，失败/无权威产物降级回确定性直路；
     * 无 {@code runner} 则直接走确定性直路（增量 A/B 行为）。
     */
    private Resolved resolve(RoutePlanQuery query) {
        if (runner != null) {
            try {
                RoutePlanResult nativeResult = runner.run(query);
                if (nativeResult != null) {
                    return new Resolved(nativeResult, false);
                }
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.WARNING,
                        "RouteAgent 原生路失败，降级确定性直路: agent={0}, reason={1}",
                        AGENT_URI, ex.getMessage());
                metrics.incrementCallError(AGENT_URI);
            }
            return new Resolved(planningService.plan(query), true);
        }
        return new Resolved(planningService.plan(query), false);
    }

    /** 路线解析产物：确定性结果 + 是否因原生路失败而降级。 */
    private record Resolved(RoutePlanResult result, boolean llmDegraded) {
    }

    private static void requireTaskType(Msg assign) {
        Object taskType = assign.payload().get(Msg.KEY_TASK_TYPE);
        if (!TASK_TYPE.equals(taskType)) {
            throw new InvalidRequestException(
                    "RouteAgent 仅受理 taskType=ROUTE_PLANNING: " + taskType);
        }
    }

    @SuppressWarnings("unchecked")
    private static RoutePlanQuery parseQuery(Msg assign) {
        Map<String, Object> context = asMap(assign.payload().get(KEY_CONTEXT));
        String destination = asString(context.get(CTX_DESTINATION));
        if (destination == null || destination.isBlank()) {
            throw new InvalidRequestException("ROUTE_PLANNING context.destination 不可为空白");
        }
        List<Attraction> attractions = asList(context.get(CTX_ATTRACTIONS));
        String transportMode = asString(context.get(CTX_TRANSPORT_MODE));
        return new RoutePlanQuery(destination, attractions, transportMode);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Attraction> asList(Object value) {
        return value instanceof List<?> list ? (List<Attraction>) list : List.of();
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }
}
