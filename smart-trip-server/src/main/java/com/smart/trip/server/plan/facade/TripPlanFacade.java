package com.smart.trip.server.plan.facade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.agent.supervisor.SupervisorAgent;
import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.PlanStatus;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Budget;
import com.smart.trip.common.model.BudgetBreakdown;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.common.util.IdUtils;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.server.plan.service.PlanIdSequenceService;
import com.smart.trip.skills.memory.MemoryClearService;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.persistence.PlanHistoryQuery;
import com.smart.trip.skills.persistence.TripPlanPersistenceService;
import com.smart.trip.skills.supervisor.TripPlanResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 规划门面（C-011 AC-6/AC-7）。
 *
 * <p>编排 {@link PlanCacheService}（Redis 读写）与 {@link SupervisorAgent}（A2A 编排触发）。
 * Controller 只依赖本 Facade，不直接操作 Service、Repository 或 Agent（ArchUnit R3/R6 守护）。
 * Facade 通过 {@code SupervisorAgent.receive(Msg)} 发 A2A 消息触发编排，不直接调用
 * TripOrchestrationService 或任何子 Agent。</p>
 */
public class TripPlanFacade {

    private static final System.Logger LOG = System.getLogger(TripPlanFacade.class.getName());

    private static final String FACADE_URI = "facade://trip-plan";
    private static final String STATUS_ANALYZING = PlanStatus.ANALYZING.name().toLowerCase(Locale.ROOT);
    private static final String STATUS_PLANNING = PlanStatus.PLANNING.name().toLowerCase(Locale.ROOT);
    private static final String STATUS_PLAN_REVIEW = PlanStatus.PLAN_REVIEW.name().toLowerCase(Locale.ROOT);
    private static final String STATUS_REVIEW = PlanStatus.REVIEW.name().toLowerCase(Locale.ROOT);
    private static final String STATUS_CONFIRMED = PlanStatus.CONFIRMED.name().toLowerCase(Locale.ROOT);
    private static final String STATUS_FAILED = PlanStatus.FAILED.name().toLowerCase(Locale.ROOT);
    private static final Set<String> VALID_ACTIONS = Set.of("CONFIRM", "MODIFY", "REJECT");

    private final PlanCacheService planCacheService;
    private final SupervisorAgent supervisorAgent;
    private final ObjectMapper objectMapper;
    private final AgentMetrics agentMetrics;
    private final Executor taskExecutor;
    private final MemoryClearService memoryClearService;
    private final TripPlanPersistenceService tripPlanPersistenceService;
    private final PlanIdSequenceService planIdSequenceService;
    private final PlanSummaryUpdater summaryUpdater;
    private final PlanModeFacadeHelper planModeHelper;

    /** 简化构造（使用默认 commonPool，无记忆清除 / 历史持久化，planId 序列号退化纯内存）。 */
    public TripPlanFacade(PlanCacheService planCacheService,
                          SupervisorAgent supervisorAgent,
                          ObjectMapper objectMapper,
                          AgentMetrics agentMetrics) {
        this(planCacheService, supervisorAgent, objectMapper, agentMetrics,
                ForkJoinPool.commonPool(), null, null, new PlanIdSequenceService(null));
    }

    /**
     * 完整构造函数（含记忆清除 / 历史规划持久化 / planId 序列号服务）。
     *
     * @param planCacheService           Redis 规划缓存
     * @param supervisorAgent            主管 Agent
     * @param objectMapper                JSON 序列化
     * @param agentMetrics                指标埋点
     * @param taskExecutor                异步编排线程池
     * @param memoryClearService          记忆清除服务，可为 null
     * @param tripPlanPersistenceService  历史规划持久化服务，可为 null（null 时跳过写入，C-017 DECISION-5）
     * @param planIdSequenceService       planId 序列号服务（C-017 T-9，替换内存 AtomicInteger）
     */
    public TripPlanFacade(PlanCacheService planCacheService,
                          SupervisorAgent supervisorAgent,
                          ObjectMapper objectMapper,
                          AgentMetrics agentMetrics,
                          Executor taskExecutor,
                          MemoryClearService memoryClearService,
                          TripPlanPersistenceService tripPlanPersistenceService,
                          PlanIdSequenceService planIdSequenceService) {
        this.planCacheService = planCacheService;
        this.supervisorAgent = supervisorAgent;
        this.objectMapper = objectMapper;
        this.agentMetrics = agentMetrics;
        this.taskExecutor = taskExecutor;
        this.memoryClearService = memoryClearService;
        this.tripPlanPersistenceService = tripPlanPersistenceService;
        this.planIdSequenceService = planIdSequenceService;
        this.summaryUpdater = new PlanSummaryUpdater(planCacheService, objectMapper);
        this.planModeHelper = new PlanModeFacadeHelper(planCacheService, supervisorAgent, objectMapper, agentMetrics, tripPlanPersistenceService, summaryUpdater, taskExecutor);
    }

    /**
     * 提交规划请求：校验 → 生成 planId → 写 Redis → 异步 Supervisor → 返回 202。
     *
     * @param request 规划请求
     * @param userId  当前登录用户 ID
     * @return 包含 planId 和 status 的结果
     */
    public Map<String, Object> submitPlan(TripPlanRequest request, long userId) {
        request.validate();

        String fingerprint = computeFingerprint(request);
        Optional<String> cachedPlanId = planCacheService.getRequestFingerprint(fingerprint);
        if (cachedPlanId.isPresent()) {
            String existingId = cachedPlanId.get();
            Optional<String> status = planCacheService.getStatus(existingId);
            if (status.isPresent()
                    && (STATUS_CONFIRMED.equals(status.get()) || STATUS_REVIEW.equals(status.get()))) {
                LOG.log(System.Logger.Level.INFO,
                        "命中缓存，跳过编排: fingerprint={0}, planId={1}", fingerprint, existingId);
                return Map.of("planId", existingId, "status", status.get());
            }
        }

        LocalDate today = LocalDate.now();
        String planId = IdUtils.newPlanId(today, planIdSequenceService.nextSequence(today));
        String traceId = IdUtils.newTraceId();

        planCacheService.setStatus(planId, STATUS_ANALYZING);
        planCacheService.setUserId(planId, String.valueOf(userId));

        Map<String, Object> initialDetail = new HashMap<>();
        initialDetail.put("planId", planId);
        initialDetail.put("status", STATUS_ANALYZING);
        initialDetail.put("requiresHumanIntervention", false);
        storePlanJson(planId, initialDetail);

        long createdAt = System.currentTimeMillis();
        summaryUpdater.storeOnSubmit(planId, request, String.valueOf(userId), createdAt, STATUS_ANALYZING);

        String userIdStr = String.valueOf(userId);
        CompletableFuture.runAsync(
                () -> executeOrchestration(planId, request, traceId, fingerprint, userIdStr),
                taskExecutor);

        return Map.of("planId", planId, "status", STATUS_ANALYZING);
    }

    /** 查询规划详情（带 userId 隔离）。 */
    public Map<String, Object> queryPlan(String planId, long userId) {
        checkOwnership(planId, userId);
        return loadDetail(planId);
    }

    /** 管理端查询（跳过 userId 数据隔离，ADMIN 角色专用）。 */
    public Map<String, Object> queryPlanAsAdmin(String planId) {
        if (!planCacheService.exists(planId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "规划不存在: " + planId);
        }
        return loadDetail(planId);
    }

    /** HITL 人工介入：CONFIRM/MODIFY/REJECT 三动作状态机（AC-9），仅 review 态可操作。 */
    public Map<String, Object> intervene(String planId, String action, Integer dayIndex,
                                         Map<String, Object> changes, long userId) {
        if (action == null || !VALID_ACTIONS.contains(action)) {
            throw new InvalidRequestException("action 必须是 CONFIRM/MODIFY/REJECT 之一: " + action);
        }
        checkOwnership(planId, userId);
        Map<String, Object> detail = loadDetail(planId);
        String currentStatus = String.valueOf(detail.get("status"));
        if (STATUS_PLAN_REVIEW.equals(currentStatus)) {
            return planModeHelper.intervenePlanReview(planId, action, userId);
        }
        if (!STATUS_REVIEW.equals(currentStatus) && !STATUS_PLANNING.equals(currentStatus)) {
            throw new InvalidRequestException(
                    "仅 review/plan_review/planning 态可执行 intervene，当前状态: " + currentStatus);
        }

        String newStatus = resolveInterveneAction(action, changes, detail);

        planCacheService.setStatus(planId, newStatus);
        detail.put("status", newStatus);
        detail.put("requiresHumanIntervention", false);
        storePlanJson(planId, detail);
        summaryUpdater.updateOnCompletion(planId, newStatus, null);
        agentMetrics.incrementHumanIntervention();
        return detail;
    }

    /** 提交用户反馈，写入 Redis（AC-6）。 */
    /** 解析 intervene 动作对应的终态（分离自 intervene 方法以降低圈复杂度）。 */
    private static String resolveInterveneAction(String action, Map<String, Object> changes,
            Map<String, Object> detail) {
        return switch (action) {
            case "CONFIRM" -> STATUS_CONFIRMED;
            case "REJECT" -> STATUS_FAILED;
            case "MODIFY" -> {
                String removeId = changes == null ? null : asString(changes.get("removeAttraction"));
                if (removeId != null && !containsAttractionId(detail, removeId)) {
                    throw new InvalidRequestException("MODIFY 引用的 attractionId 不存在: " + removeId);
                }
                yield removeId == null ? STATUS_REVIEW : STATUS_PLANNING;
            }
            default -> throw new InvalidRequestException("action 必须是 CONFIRM/MODIFY/REJECT 之一: " + action);
        };
    }

    /** 提交用户反馈，写入 Redis（AC-6）。 */
    public Map<String, Object> submitFeedback(String planId, Map<String, Object> feedback, long userId) {
        checkOwnership(planId, userId);
        try {
            planCacheService.storeFeedback(planId, objectMapper.writeValueAsString(feedback));
        } catch (JsonProcessingException ex) {
            throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, "反馈序列化失败", ex);
        }
        return Map.of("message", "反馈已记录");
    }

    /** C-015 AC-10：清除用户记忆（调用 ReMe API 删除 workspace，失败返回错误信息）。 */
    public Map<String, Object> clearMemory(long userId) {
        if (memoryClearService == null) {
            return Map.of("success", false, "message", "记忆清除服务不可用");
        }
        String workspaceId = String.valueOf(userId);
        boolean cleared = memoryClearService.clearWorkspace(workspaceId);
        if (cleared) {
            LOG.log(System.Logger.Level.INFO, "用户记忆已清除: userId={0}", userId);
            return Map.of("success", true, "message", "记忆已清除");
        }
        LOG.log(System.Logger.Level.WARNING, "用户记忆清除失败: userId={0}", userId);
        return Map.of("success", false, "message", "记忆清除失败，请稍后重试");
    }

    private void checkOwnership(String planId, long userId) {
        if (!planCacheService.exists(planId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "规划不存在: " + planId);
        }
        String ownerId = planCacheService.getUserId(planId).orElse("");
        if (!String.valueOf(userId).equals(ownerId)) {
            throw new BaseException(ErrorCode.FORBIDDEN, "无权访问此规划");
        }
    }

    private Map<String, Object> loadDetail(String planId) {
        return planCacheService.getPlan(planId)
                .map(this::parsePlanJson)
                .map(TripPlanDetailMapper::normalizeLegacyDetail)
                .map(detail -> {
                    planCacheService.getStatus(planId).ifPresent(liveStatus -> {
                        if (!liveStatus.equals(detail.get("status"))) {
                            detail.put("status", liveStatus);
                        }
                    });
                    enrichRequestFromSummary(planId, detail);
                    return detail;
                })
                .orElseGet(() -> {
                    String status = planCacheService.getStatus(planId).orElse(STATUS_ANALYZING);
                    Map<String, Object> result = new HashMap<>();
                    result.put("planId", planId);
                    result.put("status", status);
                    result.put("requiresHumanIntervention", false);
                    enrichRequestFromSummary(planId, result);
                    return result;
                });
    }

    private void enrichRequestFromSummary(String planId, Map<String, Object> detail) {
        if (detail.containsKey("request")) {
            return;
        }
        planCacheService.getPlanSummary(planId).ifPresent(json -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> summary = objectMapper.readValue(json, Map.class);
                detail.put("request", TripPlanDetailMapper.summaryToRequestMap(summary));
            } catch (Exception ignored) { }
        });
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static boolean containsAttractionId(Object node, String attractionId) {
        if (node instanceof Map<?, ?> map) {
            if (attractionId.equals(map.get("attractionId")) || attractionId.equals(map.get("id"))) {
                return true;
            }
            for (Object value : map.values()) {
                if (containsAttractionId(value, attractionId)) {
                    return true;
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object value : list) {
                if (containsAttractionId(value, attractionId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void executeOrchestration(String planId, TripPlanRequest request,
                                      String traceId, String fingerprint, String userId) {
        LOG.log(System.Logger.Level.INFO, "编排开始: planId={0}, destination={1}, userId={2}",
                planId, request != null ? request.destination() : "null", userId);
        try {
            Map<String, Object> context = new HashMap<>();
            context.put(SupervisorAgent.CTX_REQUEST, request);
            context.put(SupervisorAgent.CTX_PLAN_ID, planId);
            context.put(SupervisorAgent.CTX_USER_ID, userId);
            Map<String, Object> payload = new HashMap<>();
            payload.put(Msg.KEY_TASK_TYPE, SupervisorAgent.TASK_TYPE);
            payload.put(SupervisorAgent.KEY_CONTEXT, context);

            Msg assign = Msg.taskAssign(
                    FACADE_URI, supervisorAgent.agentUri(), traceId, payload);
            Msg result = supervisorAgent.receive(assign);

            if (result.type() == MessageType.TASK_RESULT) {
                Object resultObj = result.payload().get(Msg.KEY_RESULT);
                // plan_review 暂停，不走后续编排
                if (planModeHelper.handlePlanPause(planId, resultObj, traceId, userId, request)) return;
                boolean requiresHITL = resultObj instanceof TripPlanResult tpr && tpr.requiresHITL();
                String status = requiresHITL ? STATUS_REVIEW : STATUS_CONFIRMED;
                LOG.log(System.Logger.Level.INFO,
                        "编排完成: planId={0}, status={1}, requiresHITL={2}",
                        planId, status, requiresHITL);
                planCacheService.setStatus(planId, status);
                Map<String, Object> detail = buildCompletedDetail(planId, status, resultObj, requiresHITL);
                storePlanJson(planId, detail);
                summaryUpdater.updateOnCompletion(planId, status, resultObj);
                if (fingerprint != null) {
                    planCacheService.storeRequestFingerprint(fingerprint, planId);
                }
                // C-017 AC-1/DECISION-5：仅到达终态（CONFIRMED）时持久化到 MySQL；
                // REVIEW 待 HITL 确认/驳回，不在此处落库。
                if (STATUS_CONFIRMED.equals(status) && resultObj instanceof TripPlanResult tpr2) {
                    persistPlanAsync(tpr2.plan(), userId, STATUS_CONFIRMED);
                }
            } else {
                LOG.log(System.Logger.Level.WARNING,
                        "编排返回非 TASK_RESULT: planId={0}, type={1}", planId, result.type());
                setFailedStatusSafely(planId, request, userId);
            }
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.ERROR, "编排异常: planId={0}, error={1}",
                    planId, ex.getMessage());
            setFailedStatusSafely(planId, request, userId);
        }
    }

    private static Map<String, Object> buildCompletedDetail(String planId, String status,
                                                            Object resultObj, boolean requiresHITL) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("planId", planId);
        detail.put("status", status);
        detail.put("requiresHumanIntervention", requiresHITL);
        if (resultObj instanceof TripPlanResult tpr) {
            detail.put("plan", TripPlanDetailMapper.toFrontendPlanMap(tpr.plan()));
            detail.put("request", TripPlanDetailMapper.toFrontendRequestMap(tpr.plan().request()));
            if (!tpr.hitlReasons().isEmpty()) {
                detail.put("interventionReason", tpr.hitlReasons().get(0));
            }
            if (tpr.memoryFallback()) {
                detail.put("memoryFallback", true);
            }
        } else {
            detail.put("result", resultObj);
        }
        return detail;
    }


    /**
     * 安全设 failed 态：catch 块中的二级异常不会吞没主异常（C-013-C）。
     *
     * <p>C-017 AC-1/B-1：到达 FAILED 终态也需持久化——仅写入规划主表（含原始请求），
     * 不写日期/景点/餐饮/路线子表（days 为空），费用表写零值。</p>
     */
    private void setFailedStatusSafely(String planId, TripPlanRequest request, String userId) {
        try {
            planCacheService.setStatus(planId, STATUS_FAILED);
            Map<String, Object> failDetail = new HashMap<>();
            failDetail.put("planId", planId);
            failDetail.put("status", STATUS_FAILED);
            failDetail.put("requiresHumanIntervention", false);
            storePlanJson(planId, failDetail);
            summaryUpdater.updateOnCompletion(planId, STATUS_FAILED, null);
            persistPlanAsync(buildFailedPlan(planId, request), userId, STATUS_FAILED);
        } catch (Exception innerEx) {
            LOG.log(System.Logger.Level.ERROR,
                    "设定 failed 态也失败: planId={0}, error={1}", planId, innerEx.getMessage());
        }
    }

    /**
     * 构造 FAILED 态的最小化持久化对象（B-1：空 days + 零值费用）。
     */
    private static TripPlan buildFailedPlan(String planId, TripPlanRequest request) {
        BudgetBreakdown zeroBreakdown = new BudgetBreakdown(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        Budget zeroBudget = new Budget(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, zeroBreakdown);
        return new TripPlan(planId, request, List.of(), BigDecimal.ZERO, zeroBudget,
                PlanStatus.FAILED, false);
    }

    /**
     * 历史规划异步持久化（C-017 DECISION-5）。null 服务或 null plan 时静默跳过（降级）；
     * 调用本身不应抛异常（{@link TripPlanPersistenceService#save} 契约即为 fire-and-forget），
     * 此处再包一层 try/catch 是防御性编码，确保调用点异常不影响主链路。
     */
    private void persistPlanAsync(TripPlan plan, String userId, String planStatus) {
        if (tripPlanPersistenceService == null || plan == null) {
            return;
        }
        try {
            tripPlanPersistenceService.save(plan, userId, planStatus.toLowerCase(Locale.ROOT));
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "历史规划持久化调用失败: planId={0}, error={1}", plan.planId(), ex.getMessage());
        }
    }

    private void storePlanJson(String planId, Map<String, Object> detail) {
        try {
            planCacheService.storePlan(planId, objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException ex) {
            throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, "规划序列化失败", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePlanJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException ex) {
            throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, "规划反序列化失败", ex);
        }
    }

    static String computeFingerprint(TripPlanRequest request) {
        String json = JsonUtils.toJson(request);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ========== 历史方案（读写委托 PlanSummaryUpdater，控制本文件行数） ==========

    /**
     * 查询用户历史方案列表（C-017 T-6：Redis-first → MySQL-fallback + 筛选扩展 DECISION-1/2）。
     *
     * <p>策略：先尝试 Redis（内存筛选），若摘要有缺失（部分过期）且 MySQL 可用则降级 MySQL；
     * 若 MySQL 亦不可用，返回 Redis 能解析的部分结果（优雅降级）。</p>
     *
     * @param userId      当前登录用户 ID
     * @param page        页码（1-based）
     * @param pageSize    每页条数
     * @param keyword     目的地关键词（可选）
     * @param travelStyle 旅行风格筛选（可选）
     * @param planStatus  终态筛选（confirmed/failed，可选）
     * @param startDate   起始日期下限（含，可选）
     * @param endDate     起始日期上限（含，可选）
     * @return 分页历史列表
     */
    public Map<String, Object> getUserPlanHistory(long userId, int page, int pageSize,
                                                  String keyword, TravelStyle travelStyle,
                                                  String planStatus, LocalDate startDate,
                                                  LocalDate endDate) {
        PlanHistoryQuery query = new PlanHistoryQuery(
                String.valueOf(userId), page, pageSize, keyword,
                travelStyle, planStatus, startDate, endDate);
        return summaryUpdater.queryHistory(query, tripPlanPersistenceService);
    }
}
