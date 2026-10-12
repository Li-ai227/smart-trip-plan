package com.smart.trip.agent.supervisor.trace;

import com.smart.trip.skills.trace.SensitiveDataMasker;
import com.smart.trip.skills.trace.TraceDelegationRecord;
import com.smart.trip.skills.trace.TraceLlmCallRecord;
import com.smart.trip.skills.trace.TraceOrchestrationSummary;
import com.smart.trip.skills.trace.TracePersistenceService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;

/**
 * MySQL 审计追踪持久化实现（C-010 trace 方案）。
 *
 * <p>经 {@link TracePersistenceService} 接口提供异步（fire-and-forget）审计日志写入。
 * 写入失败仅 WARN 日志 + metrics 计数，不阻塞、不抛异常到主链路。</p>
 *
 * <h3>线程模型</h3>
 * <p>所有写入方法提交到有界线程池执行（核心 2，最大 4，队列 1000，CallerRunsPolicy）。
 * 队列满时由调用线程直接执行，确保不丢数据；正常路径异步不阻塞主链路。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * DataSource ds = ...; // 由 server 模块注入
 * MySqlTracePersistenceService svc = new MySqlTracePersistenceService(ds);
 * svc.saveDelegation(record); // fire-and-forget
 * }</pre>
 */
public final class MySqlTracePersistenceService implements TracePersistenceService, AutoCloseable {

    private static final System.Logger LOG =
            System.getLogger(MySqlTracePersistenceService.class.getName());

    // ===== SQL 语句 =====

    private static final String INSERT_DELEGATION =
            "INSERT INTO trip_agent_delegation_log "
            + "(trace_id, plan_id, parent_agent_id, sub_agent_id, task_type, "
            + "task_summary, response_summary, duration_ms, fallback, error_message, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_LLM_CALL =
            "INSERT INTO trip_llm_call_log "
            + "(trace_id, plan_id, model_id, prompt_summary, output_summary, "
            + "duration_ms, token_estimate, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SUMMARY =
            "INSERT INTO trip_orchestration_summary "
            + "(trace_id, plan_id, orchestration_path, total_duration_ms, fallback_count, "
            + "hitl_triggered, hitl_reasons, final_plan_status, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    // ===== 字段 =====

    private final DataSource dataSource;
    private final ExecutorService executor;

    /** 写入失败计数（可观测）。 */
    private final AtomicLong writeFailureCount = new AtomicLong(0);

    /**
     * @param dataSource MySQL 数据源，非 null
     */
    public MySqlTracePersistenceService(DataSource dataSource) {
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
    MySqlTracePersistenceService(DataSource dataSource, ExecutorService executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    // ===== TracePersistenceService 实现 =====

    @Override
    public void saveDelegation(TraceDelegationRecord record) {
        if (record == null) {
            return;
        }
        submitAsync(() -> insertDelegation(record));
    }

    @Override
    public void saveLlmCall(TraceLlmCallRecord record) {
        if (record == null) {
            return;
        }
        submitAsync(() -> insertLlmCall(record));
    }

    @Override
    public void saveOrchestrationSummary(TraceOrchestrationSummary summary) {
        if (summary == null) {
            return;
        }
        submitAsync(() -> insertSummary(summary));
    }

    // ===== 异步提交 =====

    private void submitAsync(Runnable task) {
        try {
            executor.submit(() -> {
                try {
                    task.run();
                } catch (Exception ex) {
                    writeFailureCount.incrementAndGet();
                    LOG.log(System.Logger.Level.WARNING,
                            "审计追踪异步写入失败（任务级）: {0}", ex.getMessage());
                }
            });
        } catch (RejectedExecutionException ex) {
            // 线程池已关闭时降级为同步执行
            try {
                task.run();
            } catch (Exception inner) {
                writeFailureCount.incrementAndGet();
                LOG.log(System.Logger.Level.WARNING,
                        "审计追踪同步写入失败（线程池已关闭）: {0}", inner.getMessage());
            }
        }
    }

    // ===== JDBC 写入 =====

    private void insertDelegation(TraceDelegationRecord r) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_DELEGATION)) {
            ps.setString(1, r.traceId());
            ps.setString(2, nullToEmpty(r.planId()));
            ps.setString(3, r.parentAgentId());
            ps.setString(4, r.subAgentId());
            ps.setString(5, r.taskType());
            ps.setString(6, SensitiveDataMasker.maskAndTruncate(
                    r.taskSummary(), TraceDelegationRecord.MAX_TEXT_LENGTH));
            ps.setString(7, truncate(r.responseSummary(), TraceDelegationRecord.MAX_TEXT_LENGTH));
            ps.setLong(8, r.durationMs());
            ps.setBoolean(9, r.fallback());
            ps.setString(10, truncate(r.errorMessage(), TraceDelegationRecord.MAX_TEXT_LENGTH));
            ps.setTimestamp(11, Timestamp.from(r.createdAt()));
            ps.executeUpdate();
        } catch (SQLException ex) {
            writeFailureCount.incrementAndGet();
            LOG.log(System.Logger.Level.WARNING,
                    "审计追踪写入 trip_agent_delegation_log 失败: traceId={0}, error={1}",
                    r.traceId(), ex.getMessage());
        }
    }

    private void insertLlmCall(TraceLlmCallRecord r) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_LLM_CALL)) {
            ps.setString(1, r.traceId());
            ps.setString(2, nullToEmpty(r.planId()));
            ps.setString(3, r.modelId());
            ps.setString(4, SensitiveDataMasker.maskAndTruncate(
                    r.promptSummary(), TraceLlmCallRecord.MAX_TEXT_LENGTH));
            ps.setString(5, truncate(r.outputSummary(), TraceLlmCallRecord.MAX_TEXT_LENGTH));
            ps.setLong(6, r.durationMs());
            ps.setInt(7, r.tokenEstimate());
            ps.setTimestamp(8, Timestamp.from(r.createdAt()));
            ps.executeUpdate();
        } catch (SQLException ex) {
            writeFailureCount.incrementAndGet();
            LOG.log(System.Logger.Level.WARNING,
                    "审计追踪写入 trip_llm_call_log 失败: traceId={0}, error={1}",
                    r.traceId(), ex.getMessage());
        }
    }

    private void insertSummary(TraceOrchestrationSummary s) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SUMMARY)) {
            ps.setString(1, s.traceId());
            ps.setString(2, nullToEmpty(s.planId()));
            ps.setString(3, s.orchestrationPath());
            ps.setLong(4, s.totalDurationMs());
            ps.setInt(5, s.fallbackCount());
            ps.setBoolean(6, s.hitlTriggered());
            ps.setString(7, truncate(s.hitlReasons(), TraceOrchestrationSummary.MAX_REASONS_LENGTH));
            ps.setString(8, s.finalPlanStatus());
            ps.setTimestamp(9, Timestamp.from(s.createdAt()));
            ps.executeUpdate();
        } catch (SQLException ex) {
            writeFailureCount.incrementAndGet();
            LOG.log(System.Logger.Level.WARNING,
                    "审计追踪写入 trip_orchestration_summary 失败: traceId={0}, error={1}",
                    s.traceId(), ex.getMessage());
        }
    }

    // ===== 工具方法 =====

    private static String truncate(String text, int maxLength) {
        return SensitiveDataMasker.truncate(text, maxLength);
    }

    /**
     * plan_id 三张 trace 表均为 {@code NOT NULL DEFAULT ''}；query.planId() 在测试/降级场景
     * 允许为 null（见 {@link com.smart.trip.skills.supervisor.TripOrchestrationQuery}），
     * 此处必须把 null 归一为空串，否则触发 "Column 'plan_id' cannot be null"。
     */
    private static String nullToEmpty(String s) {
        return s != null ? s : "";
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
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
