package com.smart.trip.agent.supervisor.persistence;

import com.smart.trip.agent.supervisor.trace.TraceThreadPoolConfig;
import com.smart.trip.common.dto.PlanSummary;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.skills.persistence.PlanHistoryQuery;
import com.smart.trip.skills.persistence.TripPlanPersistenceService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;

/**
 * MySQL 历史规划持久化实现（C-017 T-4 / ADR-012）。
 *
 * <p>经 {@link TripPlanPersistenceService} 接口提供异步（fire-and-forget）方案写入，
 * 以及历史查询 / 详情查回 / 软删除的同步查询能力。写入失败仅 WARN 日志 + metrics 计数，
 * 不阻塞、不抛异常到主链路（AC-7）。JDBC 细节委托 {@link TripPlanWriter} / {@link TripPlanReader}，
 * 以控制单文件行数 / 单方法圈复杂度。</p>
 *
 * <h3>线程模型</h3>
 * <p>写入方法复用 {@link TraceThreadPoolConfig#createTraceExecutor()}（核心 2，最大 4，
 * 队列 1000，CallerRunsPolicy），与审计追踪共用同一线程池规格（DECISION-4）。</p>
 *
 * <p>未做容量验证，仅完成功能实现；生产级容量 / 压测结论需在 deploy-verify 阶段单独确认。</p>
 */
public final class MySqlTripPlanPersistenceService
        implements TripPlanPersistenceService, AutoCloseable {

    private static final System.Logger LOG =
            System.getLogger(MySqlTripPlanPersistenceService.class.getName());

    private static final String SELECT_PLAN_BY_ID =
            "SELECT * FROM trip_plan_record WHERE plan_id = ? AND deleted = 0";

    private static final String UPDATE_SOFT_DELETE =
            "UPDATE trip_plan_record SET deleted = 1 "
            + "WHERE plan_id = ? AND user_id = ? AND deleted = 0";

    private static final String HISTORY_BASE_SELECT =
            "SELECT plan_id, destination, start_date, days, budget, total_cost, "
            + "headcount, travel_style, plan_status, created_at "
            + "FROM trip_plan_record WHERE user_id = ? AND deleted = 0";

    private static final String COUNT_BASE_SELECT =
            "SELECT COUNT(*) AS cnt FROM trip_plan_record WHERE user_id = ? AND deleted = 0";

    private final DataSource dataSource;
    private final ExecutorService executor;

    /** 写入失败计数（可观测）。 */
    private final AtomicLong writeFailureCount = new AtomicLong(0);

    /**
     * @param dataSource MySQL 数据源，非 null
     */
    public MySqlTripPlanPersistenceService(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不可为 null");
        }
        this.dataSource = dataSource;
        this.executor = TraceThreadPoolConfig.createTraceExecutor();
    }

    /**
     * 构造并注入自定义 Executor（测试用）。
     *
     * @param dataSource MySQL 数据源
     * @param executor   自定义线程池
     */
    MySqlTripPlanPersistenceService(DataSource dataSource, ExecutorService executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    // ===== TripPlanPersistenceService 实现 =====

    @Override
    public void save(TripPlan plan, String userId, String planStatus) {
        if (plan == null) {
            return;
        }
        submitAsync(() -> insertPlanTransactionally(plan, userId, planStatus));
    }

    @Override
    public List<PlanSummary> queryHistory(PlanHistoryQuery query) {
        if (query == null) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(HISTORY_BASE_SELECT);
        List<Object> params = new ArrayList<>();
        params.add(query.userId());
        TripPlanReader.appendFilters(sql, params, query);
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(query.pageSize());
        params.add(query.offset());

        List<PlanSummary> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            TripPlanReader.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(TripPlanReader.mapPlanSummary(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "历史规划查询失败: userId={0}, error={1}", query.userId(), ex.getMessage());
        }
        return result;
    }

    @Override
    public long countHistory(PlanHistoryQuery query) {
        if (query == null) {
            return 0L;
        }
        StringBuilder sql = new StringBuilder(COUNT_BASE_SELECT);
        List<Object> params = new ArrayList<>();
        params.add(query.userId());
        TripPlanReader.appendFilters(sql, params, query);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            TripPlanReader.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("cnt") : 0L;
            }
        } catch (SQLException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "历史规划统计失败: userId={0}, error={1}", query.userId(), ex.getMessage());
        }
        return 0L;
    }

    @Override
    public Optional<TripPlan> queryDetail(String planId) {
        if (planId == null || planId.isBlank()) {
            return Optional.empty();
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_PLAN_BY_ID)) {
            ps.setString(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(TripPlanReader.mapPlanRecord(conn, rs));
            }
        } catch (SQLException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "规划详情查询失败: planId={0}, error={1}", planId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean softDelete(String planId, String userId) {
        if (planId == null || planId.isBlank() || userId == null || userId.isBlank()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SOFT_DELETE)) {
            ps.setString(1, planId);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "规划软删除失败: planId={0}, userId={1}, error={2}",
                    planId, userId, ex.getMessage());
            return false;
        }
    }

    // ===== 异步提交 + 事务包裹 =====

    private void submitAsync(Runnable task) {
        try {
            executor.submit(() -> {
                try {
                    task.run();
                } catch (Exception ex) {
                    writeFailureCount.incrementAndGet();
                    LOG.log(System.Logger.Level.WARNING,
                            "历史规划异步写入失败（任务级）: {0}", ex.getMessage());
                }
            });
        } catch (RejectedExecutionException ex) {
            try {
                task.run();
            } catch (Exception inner) {
                writeFailureCount.incrementAndGet();
                LOG.log(System.Logger.Level.WARNING,
                        "历史规划同步写入失败（线程池已关闭）: {0}", inner.getMessage());
            }
        }
    }

    private void insertPlanTransactionally(TripPlan plan, String userId, String planStatus) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                TripPlanWriter.writePlan(conn, plan, userId, planStatus);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            writeFailureCount.incrementAndGet();
            LOG.log(System.Logger.Level.WARNING,
                    "历史规划写入失败: planId={0}, error={1}", plan.planId(), ex.getMessage());
        }
    }

    /**
     * 返回累计写入失败次数（可观测）。
     *
     * @return 写入失败计数
     */
    public long writeFailureCount() {
        return writeFailureCount.get();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
