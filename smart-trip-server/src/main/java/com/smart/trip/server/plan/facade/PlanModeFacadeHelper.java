package com.smart.trip.server.plan.facade;

import com.smart.trip.common.model.TripPlan;
import com.smart.trip.agent.supervisor.SupervisorAgent;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.PlanStatus;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.persistence.TripPlanPersistenceService;
import com.smart.trip.skills.supervisor.PlanDraftPause;
import com.smart.trip.skills.supervisor.TripOrchestrationQuery;
import com.smart.trip.skills.supervisor.TripPlanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * C-018 计划模式门面辅助：提取 {@link TripPlanFacade} 中计划模式暂停/恢复/HITL 相关的
 * 独立方法，降低 TripPlanFacade 的圈复杂度与文件长度，使检查规则门禁（FileLength ≤500、
 * CyclomaticComplexity ≤10、MethodLength ≤50）保持绿色。
 */
final class PlanModeFacadeHelper {

    private static final System.Logger LOG = System.getLogger(PlanModeFacadeHelper.class.getName());
    private static final String STATUS_PLAN_REVIEW = PlanStatus.PLAN_REVIEW.name().toLowerCase(Locale.ROOT);
    private static final String STATUS_FAILED = PlanStatus.FAILED.name().toLowerCase(Locale.ROOT);
    private static final String STATUS_REVIEW = PlanStatus.REVIEW.name().toLowerCase(Locale.ROOT);
    private static final String STATUS_CONFIRMED = PlanStatus.CONFIRMED.name().toLowerCase(Locale.ROOT);
    private static final String STATUS_PROCESSING = "processing";

    private final PlanCacheService planCacheService;
    private final SupervisorAgent supervisorAgent;
    private final ObjectMapper objectMapper;
    private final AgentMetrics agentMetrics;
    private final TripPlanPersistenceService tripPlanPersistenceService;
    private final PlanSummaryUpdater summaryUpdater;
    private final Executor asyncExecutor;

    PlanModeFacadeHelper(PlanCacheService planCacheService, SupervisorAgent supervisorAgent,
                          ObjectMapper objectMapper, AgentMetrics agentMetrics,
                          TripPlanPersistenceService tripPlanPersistenceService,
                          PlanSummaryUpdater summaryUpdater, Executor asyncExecutor) {
        this.planCacheService = planCacheService;
        this.supervisorAgent = supervisorAgent;
        this.objectMapper = objectMapper;
        this.agentMetrics = agentMetrics;
        this.tripPlanPersistenceService = tripPlanPersistenceService;
        this.summaryUpdater = summaryUpdater;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 处理 plan_review 态的 intervene（AC-4）：CONFIRM 恢复执行，REJECT 置失败，MODIFY 拒绝。
     *
     * @param planId 方案 ID
     * @param action 介入动作（CONFIRM/REJECT/MODIFY）
     * @param userId 当前用户 ID
     * @return 更新后的方案详情（status/planDraft/failureReason）
     * @throws InvalidRequestException 当 action 为 MODIFY 或非法值时
     */
    Map<String, Object> intervenePlanReview(String planId, String action, long userId) {
        return switch (action) {
            case "CONFIRM" -> handlePlanReviewConfirm(planId, userId);
            case "REJECT" -> handlePlanReviewReject(planId);
            case "MODIFY" -> throw new InvalidRequestException("plan_review 态不支持 MODIFY");
            default -> throw new InvalidRequestException("plan_review 态 action 必须是 CONFIRM/REJECT: " + action);
        };
    }

    /**
     * 计划草案生成完毕→PLAN_REVIEW 暂停处理：落库草案到 Redis。
     *
     * <p>T-14 重写：不再存储 pending-confirm（programmatic API 无需 ToolUseBlock 重放）。</p>
     *
     * @param planId   方案 ID
     * @param resultObj Supervisor 编排返回的原始结果对象
     * @param traceId  分布式追踪 ID
     * @return true 表示成功暂停到 plan_review；false 表示非暂停型结果（继续原编排流程）
     */
    boolean handlePlanPause(String planId, Object resultObj, String traceId, String userId, TripPlanRequest request) {
        if (!(resultObj instanceof TripPlanResult tpr && tpr.planPause() != null)) return false;
        PlanDraftPause pause = tpr.planPause();
        // ① Redis 状态标记 — 前端轮询进入审核展示
        planCacheService.setStatus(planId, STATUS_PLAN_REVIEW);
        // ② 草案存储 Redis & MySQL
        planCacheService.storePlanDraft(planId, userId, pause.draftMarkdown());
        Map<String, Object> pauseDetail = new HashMap<>();
        pauseDetail.put("planId", planId);
        pauseDetail.put("status", STATUS_PLAN_REVIEW);
        pauseDetail.put("requiresHumanIntervention", false);
        pauseDetail.put("planDraft", pause.draftMarkdown());
        pauseDetail.put("_resumeTraceId", traceId);
        if (request != null) pauseDetail.put("request", request);
        try {
            // ③ 存储 pauseDetail 到 Redis
            planCacheService.storePlan(planId, objectMapper.writeValueAsString(pauseDetail));
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.ERROR, "计划状态序列化失败: planId={0}", planId);
        }
        // ④ 编排完成 / 失败时更新摘要终态与实际花费。
        summaryUpdater.updateOnCompletion(planId, STATUS_PLAN_REVIEW, null);
        LOG.log(System.Logger.Level.INFO, "计划模式暂停: planId={0}", planId);
        return true;
    }

    /**
     * CONFIRM 恢复执行：从 Redis 恢复请求 → exitPlanMode → 四步编排。
     *
     * @param planId 方案 ID
     * @param userId 当前用户 ID
     * @return 恢复执行后的方案详情
     */
    private Map<String, Object> handlePlanReviewConfirm(String planId, long userId) {
        // 立即返回 processing 状态，异步执行恢复编排（C-018 关键约束：不可同步阻塞前端）
        planCacheService.setStatus(planId, STATUS_PROCESSING);
        Map<String, Object> result = new HashMap<>();
        result.put("planId", planId);
        result.put("status", STATUS_PROCESSING);

        CompletableFuture.runAsync(() -> {
            try {
                TripPlanResult resumed = doResume(planId, userId);
                boolean requiresHITL = resumed.requiresHITL();
                String status = requiresHITL ? STATUS_REVIEW : STATUS_CONFIRMED;
                planCacheService.setStatus(planId, status);
                planCacheService.updatePlanDraftStatus(planId, "confirmed", null);
                Map<String, Object> detail = buildCompletedDetail(planId, status, resumed, requiresHITL);
                try { planCacheService.storePlan(planId, objectMapper.writeValueAsString(detail));
                } catch (Exception ex) { LOG.log(System.Logger.Level.WARNING, "序列化存储失败: planId={0}", planId, ex); }
                summaryUpdater.updateOnCompletion(planId, status, resumed);
                agentMetrics.incrementHumanIntervention();
                if (STATUS_CONFIRMED.equals(status) && resumed.plan() != null) {
                    persistAsync(resumed.plan(), String.valueOf(userId));
                }
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.ERROR, "恢复执行失败: planId={0}, error={1}", planId, ex.getMessage());
                planCacheService.setStatus(planId, STATUS_FAILED);
                planCacheService.updatePlanDraftStatus(planId, "failed", "恢复执行失败: " + ex.getMessage());
                Map<String, Object> detail = Map.of("planId", planId, "status", STATUS_FAILED,
                        "failureReason", "恢复执行失败: " + ex.getMessage());
                try { planCacheService.storePlan(planId, objectMapper.writeValueAsString(detail));
                } catch (Exception e) { LOG.log(System.Logger.Level.WARNING, "失败态序列化存储失败: planId={0}", planId, e); }
            }
        }, asyncExecutor);

        return result;
    }

    /**
     * REJECT 拒绝执行计划：置 failed 态并记录拒绝原因。
     *
     * @param planId 方案 ID
     * @return failed 态方案详情
     */
    private Map<String, Object> handlePlanReviewReject(String planId) {
        planCacheService.setStatus(planId, STATUS_FAILED);
        planCacheService.updatePlanDraftStatus(planId, "rejected", "用户拒绝执行计划");
        agentMetrics.incrementHumanIntervention();
        Map<String, Object> detail = Map.of("planId", planId, "status", STATUS_FAILED,
                "failureReason", "用户拒绝执行计划");
        try { planCacheService.storePlan(planId, objectMapper.writeValueAsString(detail));
        } catch (Exception ex) { LOG.log(System.Logger.Level.WARNING, "拒绝态存储失败: planId={0}", planId, ex); }
        return detail;
    }

    /**
     * 从 Redis 恢复原始请求并重建 Supervisor 编排（T-14 programmatic API）。
     *
     * <p>T-14：不再需要 pendingConfirmationJson — resume() 用
     * {@code agent.exitPlanMode(ctx)} 程序化退出 Plan Mode。</p>
     *
     * @param planId 方案 ID
     * @param userId 当前用户 ID
     * @return 恢复后的 TripPlanResult
     * @throws InvalidRequestException 当 Redis 中无有效请求可恢复时
     * @throws Exception              当 Supervisor 执行失败时
     */
    private TripPlanResult doResume(String planId, long userId) throws Exception {
        Optional<String> planJson = planCacheService.getPlan(planId);
        String traceId = "";
        TripPlanRequest request = null;
        if (planJson.isPresent()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> detail = objectMapper.readValue(planJson.get(), Map.class);
                traceId = (String) detail.getOrDefault("_resumeTraceId", "");
                @SuppressWarnings("unchecked")
                Map<String, Object> reqMap = (Map<String, Object>) detail.get("request");
                if (reqMap != null) request = objectMapper.convertValue(reqMap, TripPlanRequest.class);
            } catch (Exception ex) { LOG.log(System.Logger.Level.WARNING, "恢复请求反序列化失败: planId={0}", planId, ex); }
        }
        if (request == null) throw new InvalidRequestException("无法恢复规划请求: " + planId);
        String tId = traceId == null || traceId.isBlank() ? "" : traceId;
        PlanDraftPause pause = new PlanDraftPause("", "");
        TripOrchestrationQuery query = new TripOrchestrationQuery(request, planId, String.valueOf(userId), null, tId);
        TripPlanResult resumed = supervisorAgent.resumeFromPlanPause(query, pause);
        if (resumed == null) throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, "恢复返回空: " + planId);
        return resumed;
    }

    /**
     * 从 TripPlanResult 构建前端展示用的方案详情 Map。
     *
     * @param planId        方案 ID
     * @param status        当前状态
     * @param result        编排结果
     * @param requiresHITL  是否需要人工介入
     * @return 前端可渲染的方案详情 Map
     */
    private static Map<String, Object> buildCompletedDetail(String planId, String status,
            TripPlanResult result, boolean requiresHITL) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("planId", planId);
        detail.put("status", status);
        detail.put("requiresHumanIntervention", requiresHITL);
        detail.put("plan", TripPlanDetailMapper.toFrontendPlanMap(result.plan()));
        detail.put("request", TripPlanDetailMapper.toFrontendRequestMap(result.plan().request()));
        if (!result.hitlReasons().isEmpty()) detail.put("interventionReason", result.hitlReasons().get(0));
        if (result.memoryFallback()) detail.put("memoryFallback", true);
        return detail;
    }

    /**
     * 异步持久化 TripPlan 到 MySQL（C-017 DECISION-5：仅终态落库）。
     *
     * @param plan   规划方案
     * @param userId 用户 ID
     */
    private void persistAsync(TripPlan plan, String userId) {
        if (tripPlanPersistenceService == null || plan == null) return;
        try { tripPlanPersistenceService.save(plan, userId, STATUS_CONFIRMED); }
        catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING, "持久化失败: planId={0}", plan.planId(), ex);
        }
    }
}
