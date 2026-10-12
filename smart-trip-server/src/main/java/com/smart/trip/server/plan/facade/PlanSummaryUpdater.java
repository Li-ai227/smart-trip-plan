package com.smart.trip.server.plan.facade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.dto.PlanSummary;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.skills.persistence.PlanHistoryQuery;
import com.smart.trip.skills.persistence.TripPlanPersistenceService;
import com.smart.trip.skills.supervisor.TripPlanResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 历史方案摘要（Redis {@code PlanSummary}）读写辅助（C-011 AC-6 抽出，控制
 * {@link TripPlanFacade} 单文件行数）。
 *
 * <p>无状态，持有 {@link PlanCacheService} + {@link ObjectMapper} 引用，供
 * {@link TripPlanFacade} 委托调用。</p>
 */
final class PlanSummaryUpdater {

    private static final System.Logger LOG = System.getLogger(PlanSummaryUpdater.class.getName());

    private final PlanCacheService planCacheService;
    private final ObjectMapper objectMapper;

    PlanSummaryUpdater(PlanCacheService planCacheService, ObjectMapper objectMapper) {
        this.planCacheService = planCacheService;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交时写入初始摘要 + 用户历史索引。
     *
     * @param planId    方案 ID
     * @param request   原始请求
     * @param userId    用户 ID
     * @param createdAt 创建时间戳（epoch millis）
     * @param status    初始状态
     */
    void storeOnSubmit(String planId, com.smart.trip.common.dto.TripPlanRequest request,
                       String userId, long createdAt, String status) {
        try {
            PlanSummary summary = new PlanSummary(
                    planId, request.destination(), request.startDate(),
                    request.days(), request.budget(), null,
                    request.headcount(),
                    request.travelStyle() != null ? request.travelStyle().name() : null,
                    status, createdAt);
            String json = objectMapper.writeValueAsString(summary);
            planCacheService.storePlanSummary(planId, json);
            planCacheService.addToUserPlanIndex(userId, planId, createdAt);
        } catch (JsonProcessingException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "方案摘要序列化失败: planId={0}", planId);
        }
    }

    /**
     * 编排完成 / 失败时更新摘要终态与实际花费。
     *
     * @param planId    方案 ID
     * @param status    新状态
     * @param resultObj 编排结果（可能为 {@link TripPlanResult} 或 null）
     */
    void updateOnCompletion(String planId, String status, Object resultObj) {
        try {
            planCacheService.getPlanSummary(planId).ifPresent(json -> updateStoredSummary(planId, status,
                    resultObj, json));
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "更新方案摘要异常: planId={0}, error={1}", planId, ex.getMessage());
        }
    }

    private void updateStoredSummary(String planId, String status, Object resultObj, String json) {
        try {
            PlanSummary old = objectMapper.readValue(json, PlanSummary.class);
            BigDecimal totalCost = old.totalCost();
            if (resultObj instanceof TripPlanResult tpr && tpr.plan() != null) {
                totalCost = tpr.plan().totalCost();
            }
            PlanSummary updated = new PlanSummary(
                    old.planId(), old.destination(), old.startDate(),
                    old.days(), old.budget(), totalCost,
                    old.headcount(), old.travelStyle(),
                    status, old.createdAt());
            planCacheService.storePlanSummary(planId, objectMapper.writeValueAsString(updated));
        } catch (JsonProcessingException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "更新方案摘要失败: planId={0}", planId);
        }
    }

    /**
     * 查询用户历史方案列表（C-017 T-6：Redis-first → MySQL-fallback + 多维筛选）。
     *
     * <p>策略：
     * <ol>
     *   <li>从 Redis ZSet 加载全部 planId，逐个获取摘要 JSON</li>
     *   <li>若有摘要解析失败（已过期），标记为 incomplete</li>
     *   <li>若 incomplete 且 MySQL 可用，降级到 MySQL 获取完整结果</li>
     *   <li>若 MySQL 不可用，返回 Redis 能解析的部分（优雅降级）</li>
     * </ol>
     * </p>
     *
     * @param query              查询参数（含分页 + 筛选条件）
     * @param persistenceService MySQL 持久化服务，可为 null（降级时跳过）
     * @return 分页历史列表
     */
    Map<String, Object> queryHistory(PlanHistoryQuery query,
                                     TripPlanPersistenceService persistenceService) {
        String uid = query.userId();
        long zsetCount = planCacheService.getUserPlanCount(uid);
        List<String> allPlanIds = planCacheService.getUserPlanIds(uid, 0, zsetCount);

        List<PlanSummary> summaries = new ArrayList<>();
        int missCount = 0;
        for (String planId : allPlanIds) {
            Optional<PlanSummary> parsed = planCacheService.getPlanSummary(planId)
                    .flatMap(this::parseSummary);
            if (parsed.isPresent()) {
                summaries.add(parsed.get());
            } else {
                missCount++;
            }
        }

        // DECISION-1：Redis 摘要不完整（有过期 key）且 MySQL 可用 → 降级
        if (missCount > 0 && persistenceService != null) {
            return queryFromMySql(query, persistenceService);
        }

        // Redis 路径：内存筛选（keyword + travelStyle + planStatus + startDate + endDate）
        applyFilters(summaries, query);

        long total = summaries.size();
        int fromIndex = Math.min(query.offset(), summaries.size());
        int toIndex = Math.min(fromIndex + query.pageSize(), summaries.size());
        List<PlanSummary> pageItems = summaries.subList(fromIndex, toIndex);

        return buildPageResult(pageItems, total, query.page(), query.pageSize());
    }

    /**
     * MySQL 完整查询路径（DECISION-1 降级）。
     */
    private static Map<String, Object> queryFromMySql(PlanHistoryQuery query,
                                                      TripPlanPersistenceService svc) {
        try {
            List<PlanSummary> items = svc.queryHistory(query);
            long total = svc.countHistory(query);
            return buildPageResult(items, total, query.page(), query.pageSize());
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "MySQL 查询历史降级失败: userId={0}, error={1}", query.userId(), ex.getMessage());
            return buildPageResult(List.of(), 0L, query.page(), query.pageSize());
        }
    }

    /**
     * 对 Redis 内存中的摘要列表应用多维筛选（单次 removeIf，减小 CC）。
     */
    private static void applyFilters(List<PlanSummary> summaries, PlanHistoryQuery query) {
        summaries.removeIf(s -> !matchesKeyword(s, query.keyword())
                || !matchesTravelStyle(s, query.travelStyle())
                || !matchesPlanStatus(s, query.planStatus())
                || !matchesDateRange(s, query.startDate(), query.endDate()));
    }

    private static boolean matchesKeyword(PlanSummary s, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return s.destination() != null
                && s.destination().toLowerCase(Locale.ROOT).contains(keyword.strip().toLowerCase(Locale.ROOT));
    }

    private static boolean matchesTravelStyle(PlanSummary s, com.smart.trip.common.enums.TravelStyle style) {
        return style == null || style.name().equals(s.travelStyle());
    }

    private static boolean matchesPlanStatus(PlanSummary s, String planStatus) {
        if (planStatus == null || planStatus.isBlank()) {
            return true;
        }
        return s.status() != null
                && s.status().toLowerCase(Locale.ROOT).equals(planStatus.strip().toLowerCase(Locale.ROOT));
    }

    private static boolean matchesDateRange(PlanSummary s, LocalDate from, LocalDate to) {
        if (from != null && (s.startDate() == null || s.startDate().isBefore(from))) {
            return false;
        }
        return to == null || (s.startDate() != null && !s.startDate().isAfter(to));
    }

    private static Map<String, Object> buildPageResult(List<PlanSummary> items, long total,
                                                       int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    private Optional<PlanSummary> parseSummary(String json) {
        try {
            return Optional.of(objectMapper.readValue(json, PlanSummary.class));
        } catch (JsonProcessingException ex) {
            LOG.log(System.Logger.Level.WARNING, "方案摘要反序列化失败: {0}", ex.getMessage());
            return Optional.empty();
        }
    }
}
