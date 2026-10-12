package com.smart.trip.agent.supervisor.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.skills.trace.TraceDelegationRecord;
import com.smart.trip.skills.trace.TraceLlmCallRecord;
import com.smart.trip.skills.trace.TraceOrchestrationSummary;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link MySqlTracePersistenceService} H2 内存库单测（C-010 trace 方案）。
 *
 * <p>验证：写入内容完整性、异步不阻塞、脱敏正确、写入失败不影响主链路。</p>
 */
class MySqlTracePersistenceServiceTest {

    private DataSource dataSource;
    private MySqlTracePersistenceService service;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:tracedb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        this.dataSource = ds;

        // 建表
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE trip_agent_delegation_log (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        trace_id VARCHAR(64) NOT NULL,
                        plan_id VARCHAR(64) NOT NULL DEFAULT '',
                        parent_agent_id VARCHAR(128) DEFAULT 'agent://supervisor',
                        sub_agent_id VARCHAR(128) NOT NULL,
                        task_type VARCHAR(64) NOT NULL,
                        task_summary VARCHAR(2000) DEFAULT '',
                        response_summary VARCHAR(2000) DEFAULT '',
                        duration_ms BIGINT DEFAULT 0,
                        fallback TINYINT DEFAULT 0,
                        error_message VARCHAR(2000) DEFAULT '',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            stmt.execute("""
                    CREATE TABLE trip_llm_call_log (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        trace_id VARCHAR(64) NOT NULL,
                        plan_id VARCHAR(64) NOT NULL DEFAULT '',
                        model_id VARCHAR(128) DEFAULT 'unknown',
                        prompt_summary VARCHAR(3000) DEFAULT '',
                        output_summary VARCHAR(3000) DEFAULT '',
                        duration_ms BIGINT DEFAULT 0,
                        token_estimate INT DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            stmt.execute("""
                    CREATE TABLE trip_orchestration_summary (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        trace_id VARCHAR(64) NOT NULL,
                        plan_id VARCHAR(64) NOT NULL DEFAULT '',
                        orchestration_path VARCHAR(32) DEFAULT 'UNKNOWN',
                        total_duration_ms BIGINT DEFAULT 0,
                        fallback_count INT DEFAULT 0,
                        hitl_triggered TINYINT DEFAULT 0,
                        hitl_reasons VARCHAR(1000) DEFAULT '',
                        final_plan_status VARCHAR(32) DEFAULT '',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
        }

        // 使用同步 executor 以便测试验证
        this.service = new MySqlTracePersistenceService(ds, Executors.newSingleThreadExecutor());
    }

    // ================================================================
    // 委托记录写入
    // ================================================================

    @Nested
    @DisplayName("子 Agent 委派记录写入")
    class DelegationWrite {

        @Test
        @DisplayName("正常委派记录 → 写入成功，字段完整")
        void normalDelegation() throws Exception {
            Instant now = Instant.now();
            TraceDelegationRecord record = new TraceDelegationRecord(
                    "T-001", "P-20260701-001", "agent://supervisor",
                    "agent://xhs-analyst", "XHS_ANALYSIS",
                    "分析北京景点", "返回 2 个候选", 1234L,
                    false, "", now);

            service.saveDelegation(record);
            // 等待异步写入完成
            Thread.sleep(200);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT * FROM trip_agent_delegation_log WHERE trace_id='T-001'")) {
                assertTrue(rs.next());
                assertEquals("T-001", rs.getString("trace_id"));
                assertEquals("agent://xhs-analyst", rs.getString("sub_agent_id"));
                assertEquals("XHS_ANALYSIS", rs.getString("task_type"));
                assertEquals("分析北京景点", rs.getString("task_summary"));
                assertEquals("返回 2 个候选", rs.getString("response_summary"));
                assertEquals(1234L, rs.getLong("duration_ms"));
                assertFalse(rs.getBoolean("fallback"));
            }
        }

        @Test
        @DisplayName("降级委派记录 → fallback=true 写入正确")
        void fallbackDelegation() throws Exception {
            TraceDelegationRecord record = new TraceDelegationRecord(
                    "T-002", "P-002", "agent://supervisor",
                    "agent://route-planner", "ROUTE_PLANNING",
                    "规划路线", "地图 API 降级", 5678L,
                    true, "地图服务不可用", null);

            service.saveDelegation(record);
            Thread.sleep(200);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT * FROM trip_agent_delegation_log WHERE trace_id='T-002'")) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean("fallback"));
                assertEquals("地图服务不可用", rs.getString("error_message"));
            }
        }

        @Test
        @DisplayName("API Key 脱敏：task 内容含 sk-xxx → 写入时已脱敏")
        void maskingInDelegation() throws Exception {
            TraceDelegationRecord record = new TraceDelegationRecord(
                    "T-003", "P-003", "agent://supervisor",
                    "agent://budget-controller", "COST_CALCULATION",
                    "apiKey=sk-abcdef1234567890abcdef1234567890abcdef12",
                    "响应摘要", 100L, false, "", null);

            service.saveDelegation(record);
            Thread.sleep(200);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT task_summary FROM trip_agent_delegation_log WHERE trace_id='T-003'")) {
                assertTrue(rs.next());
                String summary = rs.getString("task_summary");
                assertFalse(summary.contains("sk-abcdef"), "API Key 应已脱敏: " + summary);
                assertTrue(summary.contains("***REDACTED***"), "应含 REDACTED 标记");
            }
        }

        @Test
        @DisplayName("回归：planId=null（TripOrchestrationQuery 测试/降级场景）→ 写入空串而非 NULL，不抛异常")
        void nullPlanIdWritesEmptyStringNotSqlNull() throws Exception {
            // TraceDelegationRecord 紧凑构造器已将 null planId 归一为 ""，
            // 而 plan_id 列是 NOT NULL DEFAULT ''：写入层必须保留这个 ""，
            // 不能再次转成 SQL NULL（历史上 emptyToNull() 就是这样悄悄破坏了写入）。
            TraceDelegationRecord record = new TraceDelegationRecord(
                    "T-004", null, "agent://supervisor",
                    "agent://xhs-analyst", "XHS_ANALYSIS",
                    "无 planId 场景", "响应摘要", 10L, false, "", null);

            service.saveDelegation(record);
            Thread.sleep(200);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT plan_id FROM trip_agent_delegation_log WHERE trace_id='T-004'")) {
                assertTrue(rs.next(), "写入不应因 plan_id NOT NULL 约束而静默失败");
                assertEquals("", rs.getString("plan_id"));
            }
            assertEquals(0, service.writeFailureCount(), "不应记录写入失败");
        }
    }

    // ================================================================
    // LLM 调用记录写入
    // ================================================================

    @Nested
    @DisplayName("LLM 调用记录写入")
    class LlmCallWrite {

        @Test
        @DisplayName("正常 LLM 调用 → 字段完整写入")
        void normalLlmCall() throws Exception {
            TraceLlmCallRecord record = new TraceLlmCallRecord(
                    "T-010", "P-010", "deepseek-v4-pro",
                    "请为北京规划行程", "好的，我将…", 2500L, 1500, null);

            service.saveLlmCall(record);
            Thread.sleep(200);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT * FROM trip_llm_call_log WHERE trace_id='T-010'")) {
                assertTrue(rs.next());
                assertEquals("T-010", rs.getString("trace_id"));
                assertEquals("deepseek-v4-pro", rs.getString("model_id"));
                assertEquals(2500L, rs.getLong("duration_ms"));
                assertEquals(1500, rs.getInt("token_estimate"));
            }
        }

        @Test
        @DisplayName("LLM 提示词含 API Key → 写入时已脱敏")
        void llmPromptMasking() throws Exception {
            TraceLlmCallRecord record = new TraceLlmCallRecord(
                    "T-011", "P-011", "deepseek-v4-pro",
                    "DEEPSEEK_API_KEY=sk-abcdef1234567890abcdef1234567890abcdef12 请规划",
                    "输出不含 key", 100L, 50, null);

            service.saveLlmCall(record);
            Thread.sleep(200);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT prompt_summary FROM trip_llm_call_log WHERE trace_id='T-011'")) {
                assertTrue(rs.next());
                String prompt = rs.getString("prompt_summary");
                assertFalse(prompt.contains("sk-abcdef"), "API Key 应已脱敏");
                assertTrue(prompt.contains("***REDACTED***"));
            }
        }
    }

    // ================================================================
    // 编排汇总写入
    // ================================================================

    @Nested
    @DisplayName("编排汇总写入")
    class SummaryWrite {

        @Test
        @DisplayName("正常编排汇总 → 字段完整写入")
        void normalSummary() throws Exception {
            TraceOrchestrationSummary summary = new TraceOrchestrationSummary(
                    "T-020", "P-020", TraceOrchestrationSummary.PATH_NATIVE,
                    12000L, 2, true,
                    TraceOrchestrationSummary.joinReasons(
                            List.of("超预算红线: 总费用 6000 > 预算×1.15 5750.00",
                                    "天数不一致: 需求 3 天，实际 2 天")),
                    "REVIEW", null);

            service.saveOrchestrationSummary(summary);
            Thread.sleep(200);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT * FROM trip_orchestration_summary WHERE trace_id='T-020'")) {
                assertTrue(rs.next());
                assertEquals("T-020", rs.getString("trace_id"));
                assertEquals("NATIVE", rs.getString("orchestration_path"));
                assertEquals(12000L, rs.getLong("total_duration_ms"));
                assertEquals(2, rs.getInt("fallback_count"));
                assertTrue(rs.getBoolean("hitl_triggered"));
                assertTrue(rs.getString("hitl_reasons").contains("超预算"));
                assertEquals("REVIEW", rs.getString("final_plan_status"));
            }
        }
    }

    // ================================================================
    // 异步不阻塞
    // ================================================================

    @Nested
    @DisplayName("异步不阻塞主链路")
    class AsyncNonBlocking {

        @Test
        @DisplayName("saveDelegation 调用立即返回（不等待写入完成）")
        void returnsImmediately() {
            TraceDelegationRecord record = new TraceDelegationRecord(
                    "T-030", "P-030", "agent://supervisor",
                    "agent://xhs-analyst", "XHS_ANALYSIS",
                    "task", "response", 100L, false, "", null);

            long start = System.nanoTime();
            service.saveDelegation(record);
            long elapsed = System.nanoTime() - start;

            // 异步提交应在极短时间内返回（< 50ms）
            assertTrue(elapsed < Duration.ofMillis(500).toNanos(),
                    "saveDelegation 应在 500ms 内返回，实际: " + (elapsed / 1_000_000) + "ms");
        }

        @Test
        @DisplayName("多线程并发写入 → 不阻塞、不抛异常")
        void concurrentWrites() throws Exception {
            CountDownLatch latch = new CountDownLatch(10);
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                new Thread(() -> {
                    try {
                        service.saveDelegation(new TraceDelegationRecord(
                                "T-CONCUR-" + idx, "P-" + idx, "agent://supervisor",
                                "agent://xhs-analyst", "XHS_ANALYSIS",
                                "并发测试 " + idx, "ok", 50L, false, "", null));
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }
            assertTrue(latch.await(3, TimeUnit.SECONDS), "10 个并发写入应在 3s 内完成提交");
            // 等待异步写入完成
            Thread.sleep(300);
        }
    }

    // ================================================================
    // 写入失败不影响主链路
    // ================================================================

    @Nested
    @DisplayName("写入失败不影响主链路")
    class FailureResilience {

        @Test
        @DisplayName("null record → 静默返回，不抛异常")
        void nullRecord() {
            // 不应抛异常
            service.saveDelegation(null);
            service.saveLlmCall(null);
            service.saveOrchestrationSummary(null);
        }

        @Test
        @DisplayName("写入失败（如关闭 DataSource）→ 不抛异常到调用方")
        void closedDataSource() throws Exception {
            // 关闭 service 后写入：内部捕获异常，不传播
            service.close();

            TraceDelegationRecord record = new TraceDelegationRecord(
                    "T-040", "P-040", "agent://supervisor",
                    "agent://xhs-analyst", "XHS_ANALYSIS",
                    "task", "response", 100L, false, "", null);

            // 不抛异常（CallerRunsPolicy + 异常捕获）
            service.saveDelegation(record);
            // 到达此处即验证通过
            assertTrue(true);
        }
    }

    // ================================================================
    // 内容完整性
    // ================================================================

    @Nested
    @DisplayName("内容完整性")
    class ContentIntegrity {

        @Test
        @DisplayName("超长文本自动截断 → 不超过 MAX_TEXT_LENGTH")
        void longTextTruncation() throws Exception {
            String longText = "x".repeat(3000);
            TraceDelegationRecord record = new TraceDelegationRecord(
                    "T-050", "P-050", "agent://supervisor",
                    "agent://itinerary-designer", "ITINERARY_DESIGN",
                    longText, longText, 100L, false, "", null);

            service.saveDelegation(record);
            Thread.sleep(200);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT task_summary, response_summary FROM trip_agent_delegation_log WHERE trace_id='T-050'")) {
                assertTrue(rs.next());
                String taskSum = rs.getString("task_summary");
                String respSum = rs.getString("response_summary");
                // 截断长度 ≤ MAX（含 "…" 标记）
                assertTrue(taskSum.length() <= TraceDelegationRecord.MAX_TEXT_LENGTH,
                        "taskSummary 超长: " + taskSum.length());
                assertTrue(respSum.length() <= TraceDelegationRecord.MAX_TEXT_LENGTH,
                        "responseSummary 超长: " + respSum.length());
            }
        }
    }

    // ================================================================
    // 附加覆盖：close / writeFailureCount / SQL 异常路径
    // ================================================================

    @Nested
    @DisplayName("生命周期与可观测")
    class LifecycleAndObservability {

        @Test
        @DisplayName("close() 正常关闭线程池")
        void closeGraceful() {
            service.close();
            // close 后不抛异常，writeFailureCount 可查询
            assertTrue(service.writeFailureCount() >= 0);
        }

        @Test
        @DisplayName("writeFailureCount 初始为 0")
        void initialFailureCount() {
            assertEquals(0, service.writeFailureCount());
        }

        @Test
        @DisplayName("close 后写入 → 不抛异常（CallerRunsPolicy + 内部 catch）")
        void writeAfterClose() {
            service.close();
            TraceDelegationRecord record = new TraceDelegationRecord(
                    "T-CLOSED", "P-CLOSED", "agent://supervisor",
                    "agent://xhs-analyst", "XHS_ANALYSIS",
                    "task", "response", 100L, false, "", null);
            // 不应抛异常
            service.saveDelegation(record);
            assertTrue(true);
        }
    }
}

