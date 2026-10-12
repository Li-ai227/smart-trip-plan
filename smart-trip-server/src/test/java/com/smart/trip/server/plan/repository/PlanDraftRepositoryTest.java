package com.smart.trip.server.plan.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PlanDraftRepository} 单元测试（C-018 T-13）。
 *
 * <p>Mock DataSource/Connection/PreparedStatement/ResultSet，覆盖正常 INSERT、SELECT、
 * 记录不存在、更新失败等场景。</p>
 */
class PlanDraftRepositoryTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement preparedStatement;
    private ResultSet resultSet;
    private PlanDraftRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        preparedStatement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        repository = new PlanDraftRepository(dataSource);
    }

    @Nested
    @DisplayName("insertDraft: 插入草案记录")
    class InsertDraftTest {

        @Test
        @DisplayName("正常插入返回影响行数 > 0")
        void insertDraftSuccess() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            int rows = repository.insertDraft("P-001", "user1", "# 草案内容");

            assertEquals(1, rows);
            verify(preparedStatement).setString(1, "P-001");
            verify(preparedStatement).setString(2, "user1");
            verify(preparedStatement).setString(3, "# 草案内容");
            verify(preparedStatement).executeUpdate();
        }

        @Test
        @DisplayName("草案为空时仍可插入（草案允许空 MEDIUMTEXT）")
        void insertDraftWithEmptyDraft() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            int rows = repository.insertDraft("P-002", "user2", "");

            assertEquals(1, rows);
            verify(preparedStatement).setString(3, "");
        }

        @Test
        @DisplayName("插入失败返回 0（如重复 planId）")
        void insertDraftDuplicate() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(0);

            int rows = repository.insertDraft("P-001", "user1", "# 草案");

            assertEquals(0, rows);
        }
    }

    @Nested
    @DisplayName("updateStatus: 更新草案状态")
    class UpdateStatusTest {

        @Test
        @DisplayName("正常更新状态为 confirmed")
        void updateStatusConfirmed() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            int rows = repository.updateStatus("P-001", PlanDraftRecord.STATUS_CONFIRMED, null);

            assertEquals(1, rows);
            verify(preparedStatement).setString(eq(1), eq(PlanDraftRecord.STATUS_CONFIRMED));
            verify(preparedStatement).setString(eq(2), eq(null));
            verify(preparedStatement).setTimestamp(eq(3), any(java.sql.Timestamp.class));
            verify(preparedStatement).setString(eq(4), eq("P-001"));
        }

        @Test
        @DisplayName("更新状态为 failed 时同时更新 failureReason")
        void updateStatusFailedWithReason() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            int rows = repository.updateStatus("P-001", PlanDraftRecord.STATUS_FAILED, "执行异常");

            assertEquals(1, rows);
            verify(preparedStatement).setString(eq(1), eq(PlanDraftRecord.STATUS_FAILED));
            verify(preparedStatement).setString(eq(2), eq("执行异常"));
            verify(preparedStatement).setTimestamp(eq(3), any(java.sql.Timestamp.class));
            verify(preparedStatement).setString(eq(4), eq("P-001"));
        }

        @Test
        @DisplayName("更新不存在的 planId 返回 0")
        void updateStatusNotFound() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeUpdate()).thenReturn(0);

            int rows = repository.updateStatus("P-NOT-EXIST", PlanDraftRecord.STATUS_FAILED, null);

            assertEquals(0, rows);
        }
    }

    @Nested
    @DisplayName("findByPlanId: 查询草案记录")
    class FindByPlanIdTest {

        @Test
        @DisplayName("查询存在的记录返回 PlanDraftRecord")
        void findByPlanIdFound() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong("id")).thenReturn(1L);
            when(resultSet.getString("plan_id")).thenReturn("P-001");
            when(resultSet.getString("user_id")).thenReturn("user1");
            when(resultSet.getInt("plan_mode")).thenReturn(1);
            when(resultSet.getString("draft_markdown")).thenReturn("# 草案内容");
            when(resultSet.getString("draft_status")).thenReturn("plan_review");
            when(resultSet.getInt("fallback")).thenReturn(0);
            when(resultSet.getString("failure_reason")).thenReturn(null);
            when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.now()));
            when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.now()));

            PlanDraftRecord record = repository.findByPlanId("P-001");

            assertNotNull(record);
            assertEquals("P-001", record.planId());
            assertEquals("user1", record.userId());
            assertEquals(1, record.planMode());
            assertEquals("# 草案内容", record.draftMarkdown());
            assertEquals("plan_review", record.draftStatus());
            assertEquals(0, record.fallback());
            assertNull(record.failureReason());
        }

        @Test
        @DisplayName("查询不存在的 planId 返回 null")
        void findByPlanIdNotFound() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            PlanDraftRecord record = repository.findByPlanId("P-NOT-EXIST");

            assertNull(record);
        }

        @Test
        @DisplayName("查询失败（SQL 异常）返回 null 不抛异常")
        void findByPlanIdSqlException() throws Exception {
            when(connection.prepareStatement(anyString())).thenThrow(new SQLException("DB down"));

            PlanDraftRecord record = repository.findByPlanId("P-001");

            assertNull(record);
        }
    }
}