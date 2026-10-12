package com.smart.trip.skills.persistence;

import com.smart.trip.common.dto.PlanSummary;
import com.smart.trip.common.model.TripPlan;
import java.util.List;
import java.util.Optional;

/**
 * 历史规划持久化服务接口（C-017 AC-1 / ADR-012）。
 *
 * <p>接口定义在 {@code smart-trip-skills}，实现在 {@code smart-trip-agent-supervisor}
 * （与 {@link com.smart.trip.skills.trace.TracePersistenceService} 同模式）。</p>
 *
 * <p>写入方法（{@link #save}）的实现应异步执行，写失败不阻塞主流程（AC-7）。
 * 调用方通过 {@code ObjectProvider<TripPlanPersistenceService>} 注入，
 * 为 null 时跳过持久化（DECISION-5 优雅降级）。</p>
 */
public interface TripPlanPersistenceService {

    /**
     * 异步持久化完整规划方案到 MySQL（6 张业务表）。
     *
     * <p>实现应捕获所有异常并 WARN 日志 + 计数器，不向调用方抛出。</p>
     *
     * @param plan       完整行程方案
     * @param userId     用户 ID
     * @param planStatus 终态（confirmed / failed）
     */
    void save(TripPlan plan, String userId, String planStatus);

    /**
     * 分页查询用户历史规划摘要（MySQL）。
     *
     * @param query 查询参数（含分页 + 筛选条件）
     * @return 摘要列表（按创建时间降序），无结果返回空列表
     */
    List<PlanSummary> queryHistory(PlanHistoryQuery query);

    /**
     * 统计符合条件的历史规划数量。
     *
     * @param query 查询参数（筛选条件，分页参数忽略）
     * @return 总数
     */
    long countHistory(PlanHistoryQuery query);

    /**
     * 从 MySQL 查询完整规划详情（Redis 缓存未命中时的兜底）。
     *
     * @param planId 方案 ID
     * @return 完整行程方案，不存在或已软删除返回 empty
     */
    Optional<TripPlan> queryDetail(String planId);

    /**
     * 软删除规划（设置 deleted=1）。
     *
     * @param planId 方案 ID
     * @param userId 用户 ID（鉴权：仅方案拥有者可删除）
     * @return true 删除成功，false 方案不存在或非本人
     */
    boolean softDelete(String planId, String userId);
}
