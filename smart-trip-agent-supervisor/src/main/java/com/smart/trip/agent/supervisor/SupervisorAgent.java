package com.smart.trip.agent.supervisor;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.MemoryContext;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.util.IdUtils;
import com.smart.trip.skills.memory.MemoryRecaller;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.supervisor.PlanDraftPause;
import com.smart.trip.skills.supervisor.SupervisorAgentRunner;
import com.smart.trip.skills.supervisor.TripOrchestrationQuery;
import com.smart.trip.skills.supervisor.TripOrchestrationService;
import com.smart.trip.skills.supervisor.TripPlanResult;
import com.smart.trip.skills.trace.TraceOrchestrationSummary;
import com.smart.trip.skills.trace.TracePersistenceService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 主管 Agent（{@code agent://supervisor}，C-010 AC-1/AC-2/AC-3/AC-4）。
 *
 * <p>仅受理 {@code TASK_ASSIGN(taskType=TRIP_PLAN)}，把 {@code context} 解析为
 * {@link TripOrchestrationQuery}，装配为 {@code TASK_RESULT}（降级显式 {@code fallback=true}）。
 * Agent 间仅经 A2A 通信、不直接依赖其它 Agent（R1）。</p>
 *
 * <p><b>原生接缝</b>：注入 {@link SupervisorAgentRunner} 后，先走原生 HarnessAgent 路
 * （主 deepseek-v4-pro / 降级 qwen3-max + {@code agent_spawn} 委派子 Agent），
 * 失败/无权威产物降级回确定性 {@link TripOrchestrationService} 直路（{@code fallback=true}）。
 * 未注入 runner 时为纯确定性直路。</p>
 *
 * <p><b>C-010/C-017 trace</b>：注入 {@link TracePersistenceService} 后，编排路径（NATIVE/DETERMINISTIC）
 * 的汇总记录（{@code trip_orchestration_summary}）与 LLM 调用记录（{@code trip_llm_call_log}）
 * 统一由本类写入，全链路复用同一个 traceId（优先取 A2A 信封的 {@code assign.traceId()}）。
 * 逐步子 Agent 委派记录（{@code trip_agent_delegation_log}）：确定性/降级路由
 * {@code TripOrchestrationService} 内部追踪写入；native 成功路由
 * {@code SupervisorHarnessAgentFactory#finalizeNative} 写入——两条路径都会产出委派记录。</p>
 */
public final class SupervisorAgent {

    /** Agent 裸标识。 */
    public static final String AGENT_ID = "supervisor";

    /** Agent URI（A2A 通信地址）。 */
    public static final String AGENT_URI = "agent://" + AGENT_ID;

    /** 受理的任务类型。 */
    public static final String TASK_TYPE = "TRIP_PLAN";

    /** payload 键：上下文。 */
    public static final String KEY_CONTEXT = "context";

    /** context 键：用户规划请求（必填）。 */
    public static final String CTX_REQUEST = "request";

    /** context 键：方案 ID（由 TripPlanFacade.submitPlan() 生成并透传）。 */
    public static final String CTX_PLAN_ID = "planId";

    /** context 键：用户 ID（C-015 AC-7，ReMe workspace 隔离维度）。 */
    public static final String CTX_USER_ID = "userId";

    /** context 键：记忆召回上下文（C-015 T-6）。 */
    public static final String CTX_MEMORY_CONTEXT = "memoryContext";

    private static final String ERROR_CODE_AGENT_FAILURE = "AGENT_FAILURE";
    private static final System.Logger LOG = System.getLogger(SupervisorAgent.class.getName());

    private final TripOrchestrationService orchestrationService;
    private final AgentMetrics metrics;
    private final SupervisorAgentRunner runner;
    private final TracePersistenceService tracePersistence;
    private final MemoryRecaller memoryRecaller;
    private final AtomicReference<AgentStatus> status = new AtomicReference<>(AgentStatus.IDLE);

    /**
     * 构造 SupervisorAgent（纯确定性直路）。
     *
     * @param orchestrationService 确定性编排 Skill，不可为 null
     * @param metrics              指标埋点，不可为 null
     */
    public SupervisorAgent(TripOrchestrationService orchestrationService, AgentMetrics metrics) {
        this(null, orchestrationService, metrics, null, null);
    }

    /**
     * 构造 SupervisorAgent（原生路）：经 {@link SupervisorAgentRunner} 驱动 HarnessAgent
     * 执行编排，失败/无权威产物降级回确定性直路。
     *
     * @param runner               原生 Agent 运行器，可为 null（null 时走纯确定性直路）
     * @param orchestrationService 确定性编排 Skill（确定性核心 + 降级兜底），不可为 null
     * @param metrics              指标埋点，不可为 null
     */
    public SupervisorAgent(SupervisorAgentRunner runner,
                           TripOrchestrationService orchestrationService,
                           AgentMetrics metrics) {
        this(runner, orchestrationService, metrics, null, null);
    }

    /**
     * 构造 SupervisorAgent（完整参数，含审计追踪和记忆召回）。
     *
     * @param runner               原生 Agent 运行器，可为 null
     * @param orchestrationService 确定性编排 Skill，不可为 null
     * @param metrics              指标埋点，不可为 null
     * @param tracePersistence     审计追踪持久化，可为 null（null 时跳过审计）
     * @param memoryRecaller       记忆召回端口，可为 null（null 时不召回记忆）
     */
    public SupervisorAgent(SupervisorAgentRunner runner,
                           TripOrchestrationService orchestrationService,
                           AgentMetrics metrics,
                           TracePersistenceService tracePersistence,
                           MemoryRecaller memoryRecaller) {
        if (orchestrationService == null) {
            throw new InvalidRequestException("SupervisorAgent orchestrationService 不可为 null");
        }
        if (metrics == null) {
            throw new InvalidRequestException("SupervisorAgent metrics 不可为 null");
        }
        this.runner = runner;
        this.orchestrationService = orchestrationService;
        this.metrics = metrics;
        this.tracePersistence = tracePersistence;
        this.memoryRecaller = memoryRecaller;
    }

    /**
     * 构造 SupervisorAgent（含审计追踪，无记忆召回 — 保持向后兼容）。
     *
     * @param runner               原生 Agent 运行器，可为 null
     * @param orchestrationService 确定性编排 Skill，不可为 null
     * @param metrics              指标埋点，不可为 null
     * @param tracePersistence     审计追踪持久化，可为 null（null 时跳过审计）
     */
    public SupervisorAgent(SupervisorAgentRunner runner,
                           TripOrchestrationService orchestrationService,
                           AgentMetrics metrics,
                           TracePersistenceService tracePersistence) {
        this(runner, orchestrationService, metrics, tracePersistence, null);
    }

    /**
     * 返回 Agent URI（A2A 通信地址）。
     *
     * @return {@code agent://supervisor}
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
                    "SupervisorAgent 仅受理 TASK_ASSIGN: "
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
        TripOrchestrationQuery baseQuery = parseQuery(assign);

        // C-017 FIX-3：复用 A2A 信封自带的 traceId 贯穿本次请求的全部审计记录
        // （委派/LLM 调用/编排汇总），使三张 trace 表可通过同一 traceId 关联；
        // 信封缺失时兜底自生成，不阻塞主链路。
        String traceId = assign.traceId() != null && !assign.traceId().isBlank()
                ? assign.traceId() : IdUtils.newTraceId();

        // C-015 T-6: Supervisor 在编排前调用 MemoryRecallService 召回记忆
        MemoryContext memoryContext = recallMemory(baseQuery);
        TripOrchestrationQuery query = new TripOrchestrationQuery(
                baseQuery.request(), baseQuery.planId(), baseQuery.userId(), memoryContext, traceId);

        long startNanos = System.nanoTime();
        Resolved resolved = resolve(query);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        metrics.recordCallLatency(AGENT_URI, TASK_TYPE, elapsedMillis);

        // 审计：编排路径汇总（唯一写入者，见 traceOrchestrationPath 注释）
        traceOrchestrationPath(resolved, elapsedMillis, traceId);

        boolean fallback = resolved.result().fallback() || resolved.llmDegraded();
        return Msg.taskResult(AGENT_URI, assign.from(), assign.traceId(),
                resolved.result(), fallback);
    }

    /**
     * C-015 T-6: 调用 MemoryRecaller 按用户维度召回记忆上下文。
     * 无 memoryRecaller 或 userId 缺失时返回空上下文，不阻塞主链路。
     */
    private MemoryContext recallMemory(TripOrchestrationQuery query) {
        if (memoryRecaller == null || query.userId() == null || query.userId().isBlank()) {
            return MemoryContext.EMPTY;
        }
        String destination = query.request() != null ? query.request().destination() : null;
        String travelStyleName = query.request() != null && query.request().travelStyle() != null
                ? query.request().travelStyle().name() : null;
        try {
            return memoryRecaller.recall(query.userId(), destination, travelStyleName);
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "SupervisorAgent 记忆召回异常，降级空上下文: userId={0}, error={1}",
                    query.userId(), ex.getMessage());
            return MemoryContext.FALLBACK;
        }
    }

    private Resolved resolve(TripOrchestrationQuery query) {
        // ── 无 runner → 纯确定性直路（不涉及原生 LLM，不触发降级标记） ──
        if (runner == null) {
            TripPlanResult detResult = orchestrationService.orchestrate(query);
            return new Resolved(detResult, false, TraceOrchestrationSummary.PATH_DETERMINISTIC);
        }

        // ── 原生路（有 runner）：优先走 HarnessAgent → agent_spawn 四个子 Agent ──
        try {
            long nativeStart = System.nanoTime();
            TripPlanResult nativeResult = runner.run(query);
            long nativeMs = (System.nanoTime() - nativeStart) / 1_000_000L;

            if (nativeResult != null) {
                // C-012-C：使用 TripPlanResult.path 区分 native vs fallback。
                // Factory 已处理内部降级（返回 null 而非自行调用 orchestrate），
                // 因此此处任何非 null 结果都是权威的——直接返回，不触发二次降级。
                String path = nativeResult.path() != null
                        ? nativeResult.path() : TraceOrchestrationSummary.PATH_NATIVE;
                boolean isNative = TripPlanResult.PATH_NATIVE.equals(path);
                traceLlmCall(query, nativeMs, isNative);
                return new Resolved(nativeResult, !isNative, path);
            }

            // 原生路返回 null（LLM 未产出权威结果）→ 降级确定性（仅此一轮，无并发）
            LOG.log(System.Logger.Level.WARNING,
                    "SupervisorAgent 原生路返回 null，降级确定性直路: agent={0}", AGENT_URI);
            metrics.incrementCallError(AGENT_URI);
            traceLlmCall(query, 0, false);
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "SupervisorAgent 原生路失败，降级确定性直路: agent={0}, reason={1}",
                    AGENT_URI, ex.getMessage());
            metrics.incrementCallError(AGENT_URI);
            traceLlmCall(query, 0, false);
        }

        // 降级确定性（仅原生路失败或返回 null 时触发，确保单次请求只触发一轮 A2A 调度）
        TripPlanResult fallbackResult = orchestrationService.orchestrate(query);
        return new Resolved(fallbackResult, true, TraceOrchestrationSummary.PATH_DETERMINISTIC);
    }

    // ===== 审计追踪 =====

    /**
     * 编排汇总审计（C-017 FIX-3：唯一写入者）。
     *
     * <p>无论 native 成功、native 降级、还是纯确定性直路，{@link #handleTaskAssign} 每次请求都
     * 恰好调用一次本方法——{@code TripOrchestrationService#orchestrate} 不再自行写入 summary，
     * 避免同一请求产生两条写入尝试导致 {@code trip_orchestration_summary.trace_id} 唯一键冲突。</p>
     */
    private void traceOrchestrationPath(Resolved resolved, long totalMs, String traceId) {
        if (tracePersistence == null) {
            return;
        }
        try {
            TripPlanResult r = resolved.result();
            TraceOrchestrationSummary summary = new TraceOrchestrationSummary(
                    traceId,
                    r.plan() != null ? r.plan().planId() : "",
                    resolved.orchestrationPath(),
                    totalMs,
                    r.telemetry().subAgentFallbackCount(),
                    r.requiresHITL(),
                    TraceOrchestrationSummary.joinReasons(r.hitlReasons()),
                    r.plan() != null ? r.plan().status().name() : "",
                    Instant.now());
            tracePersistence.saveOrchestrationSummary(summary);
        } catch (Exception ex) {
            // 审计失败不影响主链路
        }
    }

    /**
     * 记录 LLM 调用审计（原生路）。
     * 注：详细的逐步子 Agent 委派记录由 HarnessAgent 框架内部产出，
     * TripOrchestrationService 的委派记录在确定性路产出。
     */
    private void traceLlmCall(TripOrchestrationQuery query, long durationMs, boolean success) {
        if (tracePersistence == null) {
            return;
        }
        try {
            com.smart.trip.skills.trace.TraceLlmCallRecord record =
                    new com.smart.trip.skills.trace.TraceLlmCallRecord(
                            query.traceId(),
                            query.planId(),
                            "deepseek-v4-flash", // 主模型 deepseek-v4-pro
                            "TripPlanRequest: " + (query.request() != null
                                    ? query.request().destination() : "null"),
                            success ? "orchestrate_trip 收口成功" : "原生路失败",
                            durationMs,
                            0, // token 估算由框架提供
                            Instant.now());
            tracePersistence.saveLlmCall(record);
        } catch (Exception ex) {
            // 审计失败不影响主链路
        }
    }

    private record Resolved(TripPlanResult result, boolean llmDegraded, String orchestrationPath) {
    }

    /**
     * C-018 AC-4：恢复因 {@code plan_exit} 暂停的计划模式执行。runner 为 null 或无 resume 支持时返回 null。
     */
    public TripPlanResult resumeFromPlanPause(TripOrchestrationQuery query, PlanDraftPause pause)
            throws Exception {
        return runner != null ? runner.resume(query, pause) : null;
    }

    private static void requireTaskType(Msg assign) {
        Object taskType = assign.payload().get(Msg.KEY_TASK_TYPE);
        if (!TASK_TYPE.equals(taskType)) {
            throw new InvalidRequestException(
                    "SupervisorAgent 仅受理 taskType=TRIP_PLAN: " + taskType);
        }
    }

    @SuppressWarnings("unchecked")
    private static TripOrchestrationQuery parseQuery(Msg assign) {
        Map<String, Object> context = asMap(assign.payload().get(KEY_CONTEXT));
        Object reqObj = context.get(CTX_REQUEST);
        if (!(reqObj instanceof TripPlanRequest request)) {
            throw new InvalidRequestException("TRIP_PLAN context.request 不可为空或类型错误");
        }
        Object planIdObj = context.get(CTX_PLAN_ID);
        String planId = planIdObj instanceof String s ? s : null;
        Object userIdObj = context.get(CTX_USER_ID);
        String userId = userIdObj instanceof String s2 ? s2 : null;
        Object memoryCtxObj = context.get(CTX_MEMORY_CONTEXT);
        MemoryContext memoryContext = memoryCtxObj instanceof MemoryContext mc ? mc : null;
        return new TripOrchestrationQuery(request, planId, userId, memoryContext);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
