package com.smart.trip.agent.budget;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.skills.budget.BudgetAgentRunner;
import com.smart.trip.skills.budget.BudgetCalculationQuery;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.budget.BudgetCalculationService;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.observability.AgentMetrics;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 费用统筹 Agent（{@code agent://budget-controller}，C-009 AC-1/AC-2/AC-7/AC-13/AC-14）。
 *
 * <p>仅受理 {@code TASK_ASSIGN(taskType=COST_CALCULATION)}，把 {@code context} 解析为
 * {@link BudgetCalculationQuery}，装配为 {@code TASK_RESULT}（降级显式 {@code fallback=true}）。
 * Agent 间仅经 A2A 通信、不直接依赖其它 Agent。</p>
 *
 * <p><b>增量 B 原生接缝</b>：注入 {@link BudgetAgentRunner} 后，先走原生 HarnessAgent 路，
 * 失败/无权威产物降级回确定性 {@link BudgetCalculationService} 直路（{@code fallback=true}）。
 * 未注入 runner 时为纯确定性直路（增量 A 行为）。</p>
 */
public final class BudgetAgent {

    /** Agent 裸标识。 */
    public static final String AGENT_ID = "budget-controller";

    /** Agent URI（A2A 通信地址）。 */
    public static final String AGENT_URI = "agent://" + AGENT_ID;

    /** 受理的任务类型。 */
    public static final String TASK_TYPE = "COST_CALCULATION";

    /** payload 键：上下文。 */
    public static final String KEY_CONTEXT = "context";

    /** context 键：用户规划请求（必填）。 */
    public static final String CTX_REQUEST = "request";

    /** context 键：行程编排结果（必填）。 */
    public static final String CTX_ITINERARY_RESULT = "itineraryResult";

    private static final String ERROR_CODE_AGENT_FAILURE = "AGENT_FAILURE";
    private static final System.Logger LOG = System.getLogger(BudgetAgent.class.getName());

    private final BudgetCalculationService calculationService;
    private final AgentMetrics metrics;
    private final BudgetAgentRunner runner;
    private final AtomicReference<AgentStatus> status = new AtomicReference<>(AgentStatus.IDLE);

    /**
     * 构造 BudgetAgent（纯确定性直路，增量 A 行为）。
     *
     * @param calculationService 费用核算 Skill，不可为 null
     * @param metrics            指标埋点，不可为 null
     */
    public BudgetAgent(BudgetCalculationService calculationService, AgentMetrics metrics) {
        this(null, calculationService, metrics);
    }

    /**
     * 构造 BudgetAgent（增量 B 原生路）：经 {@link BudgetAgentRunner} 驱动 HarnessAgent
     * 执行费用核算，失败/无权威产物降级回确定性直路。
     *
     * @param runner             原生 Agent 运行器，可为 null（null 时走纯确定性直路）
     * @param calculationService 费用核算 Skill（确定性核心 + 降级兜底），不可为 null
     * @param metrics            指标埋点，不可为 null
     */
    public BudgetAgent(BudgetAgentRunner runner, BudgetCalculationService calculationService,
                       AgentMetrics metrics) {
        if (calculationService == null) {
            throw new InvalidRequestException("BudgetAgent calculationService 不可为 null");
        }
        if (metrics == null) {
            throw new InvalidRequestException("BudgetAgent metrics 不可为 null");
        }
        this.runner = runner;
        this.calculationService = calculationService;
        this.metrics = metrics;
    }

    /**
     * 返回 Agent URI（A2A 通信地址）。
     *
     * @return {@code agent://budget-controller}
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
                    "BudgetAgent 仅受理 TASK_ASSIGN: "
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
        BudgetCalculationQuery query = parseQuery(assign);

        long startNanos = System.nanoTime();
        Resolved resolved = resolve(query);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        metrics.recordCallLatency(AGENT_URI, TASK_TYPE, elapsedMillis);

        boolean fallback = resolved.result().fallback() || resolved.llmDegraded();
        return Msg.taskResult(AGENT_URI, assign.from(), assign.traceId(),
                resolved.result(), fallback);
    }

    private Resolved resolve(BudgetCalculationQuery query) {
        if (runner != null) {
            try {
                BudgetCalculationResult nativeResult = runner.run(query);
                if (nativeResult != null) {
                    return new Resolved(nativeResult, false);
                }
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.WARNING,
                        "BudgetAgent 原生路失败，降级确定性直路: agent={0}, reason={1}",
                        AGENT_URI, ex.getMessage());
                metrics.incrementCallError(AGENT_URI);
            }
            return new Resolved(calculationService.calculate(query), true);
        }
        return new Resolved(calculationService.calculate(query), false);
    }

    private record Resolved(BudgetCalculationResult result, boolean llmDegraded) {
    }

    private static void requireTaskType(Msg assign) {
        Object taskType = assign.payload().get(Msg.KEY_TASK_TYPE);
        if (!TASK_TYPE.equals(taskType)) {
            throw new InvalidRequestException(
                    "BudgetAgent 仅受理 taskType=COST_CALCULATION: " + taskType);
        }
    }

    @SuppressWarnings("unchecked")
    private static BudgetCalculationQuery parseQuery(Msg assign) {
        Map<String, Object> context = asMap(assign.payload().get(KEY_CONTEXT));
        Object reqObj = context.get(CTX_REQUEST);
        if (!(reqObj instanceof TripPlanRequest request)) {
            throw new InvalidRequestException("COST_CALCULATION context.request 不可为空或类型错误");
        }
        Object itinObj = context.get(CTX_ITINERARY_RESULT);
        if (!(itinObj instanceof ItineraryDesignResult itineraryResult)) {
            throw new InvalidRequestException(
                    "COST_CALCULATION context.itineraryResult 不可为空或类型错误");
        }
        return new BudgetCalculationQuery(request, itineraryResult);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
