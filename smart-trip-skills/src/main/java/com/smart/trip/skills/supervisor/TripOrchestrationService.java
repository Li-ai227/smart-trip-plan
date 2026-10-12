package com.smart.trip.skills.supervisor;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.MemoryContext;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.common.util.IdUtils;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.memory.MemoryRecaller;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.skills.quality.QualityGateResult;
import com.smart.trip.skills.quality.QualityViolation;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.trace.TraceDelegationRecord;
import com.smart.trip.skills.trace.TracePersistenceService;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 确定性顺序编排核心（C-010 AC-4）：XHS→Route→Itinerary→Budget 链式委派 + OutputQualityGate 复核。
 *
 * <p>通过 {@link SubAgentDispatcher} 向子 Agent 发送 {@code TASK_ASSIGN}，不直接 import 任何子 Agent 类（R1）。
 * 任一子 Agent 降级（{@code fallback=true}）或返回 {@code TASK_ERROR} 时整体仍继续，标记 {@code fallback}。</p>
 *
 * <p><b>C-010 trace</b>：注入 {@link TracePersistenceService} 后，每次子 Agent 委派自动异步写入
 * {@code trip_agent_delegation_log}；tracePersistence 为 null 时跳过（向后兼容）。
 * 编排汇总（{@code trip_orchestration_summary}）改由 {@code SupervisorAgent} 统一写入（C-017 FIX-3：
 * 避免此处与调用方各自写一次导致 traceId 唯一键冲突），本类只负责透传/复用调用方给定的 traceId。</p>
 *
 * <p><b>进度推送</b>：注入 {@link SupervisorNativeRunContext.ProgressListener} 后，编排过程中
 * 每完成一个子 Agent 步骤自动推送状态（planning / assembling / budgeting），
 * 供前端轮询感知实时进度。原生 LLM 路与确定性 fallback 路均使用同一机制。</p>
 */
public final class TripOrchestrationService {

    static final String SUPERVISOR_URI = "agent://supervisor";
    static final String XHS_URI = "agent://xhs-analyst";
    static final String ROUTE_URI = "agent://route-planner";
    static final String ITINERARY_URI = "agent://itinerary-designer";
    static final String BUDGET_URI = "agent://budget-controller";

    private static final String PROGRESS_PLANNING = "planning";
    private static final String PROGRESS_ASSEMBLING = "assembling";
    private static final String PROGRESS_BUDGETING = "budgeting";
    private static final System.Logger LOG =
            System.getLogger(TripOrchestrationService.class.getName());

    private final SubAgentDispatcher dispatcher;
    private final OutputQualityGate qualityGate;
    private final TracePersistenceService tracePersistence;
    private final AtomicReference<SupervisorNativeRunContext.ProgressListener> progressListener =
            new AtomicReference<>(null);
    private MemoryRecaller memoryRecaller;

    /**
     * @param dispatcher  子 Agent 委派器，非 null
     * @param qualityGate 全局复核门禁，非 null
     */
    public TripOrchestrationService(SubAgentDispatcher dispatcher, OutputQualityGate qualityGate) {
        this(dispatcher, qualityGate, null);
    }

    /**
     * @param dispatcher       子 Agent 委派器，非 null
     * @param qualityGate      全局复核门禁，非 null
     * @param tracePersistence 审计追踪持久化，可为 null（null 时跳过审计）
     */
    public TripOrchestrationService(SubAgentDispatcher dispatcher, OutputQualityGate qualityGate,
                                     TracePersistenceService tracePersistence) {
        if (dispatcher == null) {
            throw new InvalidRequestException("TripOrchestrationService dispatcher 不可为 null");
        }
        if (qualityGate == null) {
            throw new InvalidRequestException("TripOrchestrationService qualityGate 不可为 null");
        }
        this.dispatcher = dispatcher;
        this.qualityGate = qualityGate;
        this.tracePersistence = tracePersistence;
    }

    /**
     * 设置进度监听器（原生路与确定性 fallback 路共用）。<br/>
     * 可在 Bean 装配后调用；为 null 时跳过进度推送。
     *
     * @param progressListener 进度监听器，可为 null
     */
    public void setProgressListener(SupervisorNativeRunContext.ProgressListener listener) {
        this.progressListener.set(listener);
    }

    /**
     * 设置记忆召回器（C-015 T-6）。
     * 可为 null，null 时不召回记忆，向后兼容。
     *
     * @param memoryRecaller 记忆召回端口，可为 null
     */
    public void setMemoryRecaller(MemoryRecaller memoryRecaller) {
        this.memoryRecaller = memoryRecaller;
    }

    /**
     * 确定性顺序编排。
     *
     * @param query 编排请求
     * @return 编排结果
     */
    public TripPlanResult orchestrate(TripOrchestrationQuery query) {
        long startNanos = System.nanoTime();
        TripPlanRequest request = query.request();
        if (request == null) {
            throw new InvalidRequestException("TripOrchestrationService: request 不可为 null");
        }

        MemoryContext memoryContext = resolveMemoryContext(query);

        // C-017 FIX-3：优先复用调用方（SupervisorAgent）透传的全链路 traceId，
        // 保证委派记录与 SupervisorAgent 统一写入的 orchestration_summary/llm_call 可关联；
        // 未透传时（如测试直调 orchestrate()）退化为自生成，不影响独立可用性。
        String traceId = query.traceId() != null && !query.traceId().isBlank()
                ? query.traceId() : IdUtils.newTraceId();
        int fallbackCount = 0;

        // Step 1-4: XHS → Route → Itinerary → Budget
        // Step 1: XHS 分析 → 挖掘候选景点（C-015 T-6: 注入记忆上下文）
        StepResult<XHSAnalysisResult> xhsStep =
                executeStep("XHS_ANALYSIS", XHS_URI, traceId, query.planId(),
                        () -> dispatchXHS(request, memoryContext, traceId),
                        summaryOf(request));
        if (xhsStep.fallback) { fallbackCount++; }
        notifyProgress(query.planId(), PROGRESS_PLANNING);

        List<Attraction> attractions = extractAttractions(xhsStep.result);
        // Step 2: 路线规划 → 计算景点间路线（依赖 Step 1）
        StepResult<RoutePlanResult> routeStep =
                executeStep("ROUTE_PLANNING", ROUTE_URI, traceId, query.planId(),
                        () -> dispatchRoute(request, attractions, traceId),
                        "attractions=" + attractions.size());
        if (routeStep.fallback) { fallbackCount++; }
        notifyProgress(query.planId(), PROGRESS_ASSEMBLING);
        // Step 3: 行程编排 → 景点填入每日时间槽（C-015 T-6: 注入记忆上下文）
        StepResult<ItineraryDesignResult> itinStep =
                executeStep("ITINERARY_DESIGN", ITINERARY_URI, traceId, query.planId(),
                        () -> dispatchItinerary(request, attractions, routeStep.result, memoryContext, traceId),
                        "days=" + request.days() + " attractions=" + attractions.size());
        if (itinStep.fallback) { fallbackCount++; }
        notifyProgress(query.planId(), PROGRESS_BUDGETING);
        // Step 4: 费用计算 → 预算核算（依赖 Step 3）
        StepResult<BudgetCalculationResult> budgetStep =
                executeStep("COST_CALCULATION", BUDGET_URI, traceId, query.planId(),
                        () -> dispatchBudget(request, itinStep.result, traceId),
                        "budget=" + request.budget() + " days=" + request.days());
        if (budgetStep.fallback) { fallbackCount++; }

        // Assemble + Gate + Result（planId 由上游透传，不在此重新生成）
        TripPlan plan = TripPlanAssembler.assemble(
                query.planId(), request, itinStep.result, budgetStep.result);
        String planId = plan.planId();
        QualityGateResult gateResult = qualityGate.review(plan);
        List<String> hitlReasons = gateResult.violations().stream()
                .map(QualityViolation::detail).collect(Collectors.toList());
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        boolean anyFallback = fallbackCount > 0;

        // C-017 FIX-3：orchestration_summary 改由 SupervisorAgent 统一写入（唯一写入者，
        // 见 SupervisorAgent#traceOrchestrationPath），避免与此处重复写入同一 traceId 导致
        // trip_orchestration_summary.trace_id 唯一键冲突。

        return new TripPlanResult(plan, gateResult.requiresHITL(), hitlReasons,
                anyFallback,
                new TripPlanResult.Telemetry(fallbackCount, gateResult.requiresHITL(), latencyMs),
                TripPlanResult.PATH_DETERMINISTIC,
                memoryContext.memoryFallback());
    }

    // ===== 审计追踪 =====

    private void traceDelegation(String traceId, String planId, String subAgentUri,
                                  String taskType, String taskSummary, String responseSummary,
                                  long durationMs, boolean fallback, String errorMessage) {
        if (tracePersistence == null) {
            return;
        }
        try {
            TraceDelegationRecord record = new TraceDelegationRecord(
                    traceId, planId, SUPERVISOR_URI, subAgentUri, taskType,
                    taskSummary, responseSummary, durationMs, fallback,
                    errorMessage, Instant.now());
            tracePersistence.saveDelegation(record);
        } catch (Exception ex) {
            // 审计失败不影响主链路
        }
    }

    // ===== 结果摘要 =====

    private static String summaryOf(TripPlanRequest request) {
        if (request == null) {
            return "null";
        }
        return String.format("destination=%s days=%d budget=%s",
                request.destination(), request.days(), request.budget());
    }

    private static String summaryOf(StepResult<?> step) {
        if (step == null) {
            return "null";
        }
        if (step.errorMessage != null && !step.errorMessage.isEmpty()) {
            return "ERROR: " + step.errorMessage;
        }
        if (step.result == null) {
            return "null result (fallback=" + step.fallback + ")";
        }
        if (step.result instanceof XHSAnalysisResult xhs) {
            return "candidates=" + xhs.candidates().size() + " fallback=" + xhs.fallback();
        }
        if (step.result instanceof RoutePlanResult route) {
            return "routes=" + route.routes().size()
                    + " overLimit=" + route.overLimitRoutes().size()
                    + " fallback=" + route.fallback();
        }
        if (step.result instanceof ItineraryDesignResult itin) {
            return "days=" + itin.days().size() + " risks=" + itin.risks().size()
                    + " fallback=" + itin.fallback();
        }
        if (step.result instanceof BudgetCalculationResult budget) {
            return "totalCost=" + budget.totalCost()
                    + " overrunRate=" + budget.overrunRate()
                    + " requiresHITL=" + budget.requiresHITL()
                    + " fallback=" + budget.fallback();
        }
        return step.result.getClass().getSimpleName();
    }

    // ===== C-015 T-6: 记忆上下文解析 =====

    /**
     * 解析记忆上下文：优先使用 query 已携带的 memoryContext；
     * 当 query 未携带且设定了 {@link #memoryRecaller} 时，主动召回。
     *
     * @param query 编排请求
     * @return 记忆上下文（失败时返回 {@link MemoryContext#FALLBACK}，不会为 null）
     */
    private MemoryContext resolveMemoryContext(TripOrchestrationQuery query) {
        if (query.memoryContext() != null) {
            return query.memoryContext();
        }
        if (memoryRecaller == null || query.userId() == null || query.userId().isBlank()) {
            return MemoryContext.EMPTY;
        }
        TripPlanRequest req = query.request();
        if (req == null) {
            return MemoryContext.EMPTY;
        }
        try {
            String dest = req.destination();
            String style = req.travelStyle() != null ? req.travelStyle().name() : null;
            return memoryRecaller.recall(query.userId(), dest, style);
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "记忆召回异常，降级空上下文: userId={0}, error={1}",
                    query.userId(), ex.getMessage());
            return MemoryContext.FALLBACK;
        }
    }

    // ===== 委派方法 =====

    private StepResult<XHSAnalysisResult> dispatchXHS(
            TripPlanRequest request, MemoryContext memoryContext, String traceId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("destination", request.destination());
        context.put("preferences", request.preferences());
        context.put("travelStyle", request.travelStyle());
        if (memoryContext != null && !memoryContext.isEmpty()) {
            context.put("memoryContext", memoryContext);
        }
        Map<String, Object> payload = Map.of(
                Msg.KEY_TASK_TYPE, "XHS_ANALYSIS",
                "context", context);
        Msg assign = Msg.taskAssign(SUPERVISOR_URI, XHS_URI, traceId, payload);
        return dispatchAndUnwrap(XHS_URI, assign, XHSAnalysisResult.class);
    }

    @SuppressWarnings("unchecked")
    private StepResult<RoutePlanResult> dispatchRoute(
            TripPlanRequest request, List<Attraction> attractions, String traceId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("destination", request.destination());
        context.put("attractions", attractions);
        context.put("transportMode", "driving");
        Map<String, Object> payload = Map.of(
                Msg.KEY_TASK_TYPE, "ROUTE_PLANNING",
                "context", context);
        Msg assign = Msg.taskAssign(SUPERVISOR_URI, ROUTE_URI, traceId, payload);
        return dispatchAndUnwrap(ROUTE_URI, assign, RoutePlanResult.class);
    }

    private StepResult<ItineraryDesignResult> dispatchItinerary(
            TripPlanRequest request, List<Attraction> attractions,
            RoutePlanResult routeResult, MemoryContext memoryContext, String traceId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("request", request);
        context.put("attractions", attractions);
        if (routeResult != null) {
            context.put("routeResult", routeResult);
        }
        if (memoryContext != null && !memoryContext.isEmpty()) {
            context.put("memoryContext", memoryContext);
        }
        Map<String, Object> payload = Map.of(
                Msg.KEY_TASK_TYPE, "ITINERARY_DESIGN",
                "context", context);
        Msg assign = Msg.taskAssign(SUPERVISOR_URI, ITINERARY_URI, traceId, payload);
        return dispatchAndUnwrap(ITINERARY_URI, assign, ItineraryDesignResult.class);
    }

    private StepResult<BudgetCalculationResult> dispatchBudget(
            TripPlanRequest request, ItineraryDesignResult itineraryResult, String traceId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("request", request);
        if (itineraryResult != null) {
            context.put("itineraryResult", itineraryResult);
        }
        Map<String, Object> payload = Map.of(
                Msg.KEY_TASK_TYPE, "COST_CALCULATION",
                "context", context);
        Msg assign = Msg.taskAssign(SUPERVISOR_URI, BUDGET_URI, traceId, payload);
        return dispatchAndUnwrap(BUDGET_URI, assign, BudgetCalculationResult.class);
    }

    /**
     * 向独立 status key 推送进度（若已配置监听器）。
     *
     * @param planId 方案 ID（可为 null，null 时跳过）
     * @param step   进度标识（planning / assembling / budgeting）
     */
    private void notifyProgress(String planId, String step) {
        SupervisorNativeRunContext.ProgressListener l = progressListener.get();
        if (l != null && planId != null) {
            l.onStep(planId, step);
        }
    }

    /** 包装步：计时 + dispatch + 审计追踪。 */
    @FunctionalInterface
    private interface StepSupplier<T> {
        StepResult<T> get();
    }

    private <T> StepResult<T> executeStep(String taskType, String agentUri, String traceId,
                                            String planId, StepSupplier<T> supplier,
                                            String taskSummary) {
        long start = System.nanoTime();
        StepResult<T> step = supplier.get();
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        traceDelegation(traceId, planId, agentUri, taskType,
                taskSummary, summaryOf(step), durationMs,
                step.fallback, step.errorMessage);
        return step;
    }

    private static List<Attraction> extractAttractions(XHSAnalysisResult xhs) {
        if (xhs == null) {
            return List.of();
        }
        return xhs.candidates().stream()
                .map(c -> c.attraction())
                .collect(Collectors.toList());
    }

    /** 统一 dispatch + 类型解包。 */
    @SuppressWarnings("unchecked")
    private <T> StepResult<T> dispatchAndUnwrap(
            String agentUri, Msg assign, Class<T> expectedType) {
        try {
            Msg response = dispatcher.dispatch(agentUri, assign);
            if (response.type() == MessageType.TASK_ERROR) {
                String error = String.valueOf(response.payload().getOrDefault(
                        Msg.KEY_RESULT, "unknown error"));
                return new StepResult<>(null, true, error);
            }
            Object result = response.payload().get(Msg.KEY_RESULT);
            boolean fallback = response.isFallback();
            if (expectedType.isInstance(result)) {
                return new StepResult<>((T) result, fallback, null);
            }
            return new StepResult<>(null, fallback,
                    "unexpected result type: " + (result != null ? result.getClass().getName() : "null"));
        } catch (Exception ex) {
            return new StepResult<>(null, true, ex.getMessage());
        }
    }

    private record StepResult<T>(T result, boolean fallback, String errorMessage) {
    }
}
