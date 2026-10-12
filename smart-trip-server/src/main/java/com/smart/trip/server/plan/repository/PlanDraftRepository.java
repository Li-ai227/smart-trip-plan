package com.smart.trip.server.plan.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;

/**
 * 计划模式草案 MySQL 持久化仓库（C-018 AC-3 / T-13）。
 *
 * <p>使用 plain JDBC（不引入 JPA），操作 {@code trip_plan_draft_record} 表，
 * 提供草案的插入、状态更新、按 planId 查询能力。</p>
 *
 * <p>写入失败仅 WARN 日志，不抛异常到主链路（MySQL 写入失败不阻塞 Redis 主链路，见 AC-3 边界-8）。</p>
 */
public class PlanDraftRepository {

    private static final System.Logger LOG = System.getLogger(PlanDraftRepository.class.getName());

    private static final String INSERT_DRAFT =
            "INSERT INTO trip_plan_draft_record "
            + "(plan_id, user_id, plan_mode, draft_markdown, draft_status, fallback, failure_reason, created_at, updated_at) "
            + "VALUES (?, ?, 1, ?, 'plan_review', 0, NULL, ?, ?) "
            + "ON DUPLICATE KEY UPDATE draft_markdown = VALUES(draft_markdown), "
            + "updated_at = VALUES(updated_at)";

    private static final String UPDATE_STATUS =
            "UPDATE trip_plan_draft_record SET draft_status = ?, failure_reason = ?, updated_at = ? "
            + "WHERE plan_id = ?";

    private static final String FIND_BY_PLAN_ID =
            "SELECT id, plan_id, user_id, plan_mode, draft_markdown, draft_status, fallback, failure_reason, "
            + "created_at, updated_at FROM trip_plan_draft_record WHERE plan_id = ?";

    private final DataSource dataSource;

    /**
     * @param dataSource MySQL 数据源，非 null
     */
    public PlanDraftRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不可为 null");
        }
        this.dataSource = dataSource;
    }

    /**
     * 插入一条草案记录。MySQL 写入失败不阻塞主链路（边界-8）。
     *
     * @param planId        方案 ID
     * @param userId        用户 ID
     * @param draftMarkdown 草案 Markdown 全文
     * @return 影响行数（1=成功，0=失败）
     */
    public int insertDraft(String planId, String userId, String draftMarkdown) {
        Instant now = Instant.now();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_DRAFT)) {
            ps.setString(1, planId);
            ps.setString(2, userId);
            ps.setString(3, draftMarkdown);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            return ps.executeUpdate();
        } catch (SQLException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "草案插入失败: planId={0}, error={1}", planId, ex.getMessage());
            return 0;
        }
    }

    /**
     * 更新草案状态。用于 CONFIRM（→ confirmed）或 REJECT（→ failed）场景。
     *
     * @param planId        方案 ID
     * @param draftStatus   目标状态（confirmed / failed）
     * @param failureReason 失败原因（draftStatus=failed 时填写，可 null）
     * @return 影响行数
     */
    public int updateStatus(String planId, String draftStatus, String failureReason) {
        Instant now = Instant.now();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, draftStatus);
            ps.setString(2, failureReason);
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setString(4, planId);
            return ps.executeUpdate();
        } catch (SQLException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "草案状态更新失败: planId={0}, status={1}, error={2}",
                    planId, draftStatus, ex.getMessage());
            return 0;
        }
    }

    /**
     * 按 planId 查询草案记录。用于服务重启后恢复 plan_review 态（边界-2）。
     *
     * @param planId 方案 ID
     * @return PlanDraftRecord，不存在或查询失败时返回 null
     */
    public PlanDraftRecord findByPlanId(String planId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_PLAN_ID)) {
            ps.setString(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapRow(rs);
            }
        } catch (SQLException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "草案查询失败: planId={0}, error={1}", planId, ex.getMessage());
            return null;
        }
    }

    private static PlanDraftRecord mapRow(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String planId = rs.getString("plan_id");
        String userId = rs.getString("user_id");
        int planMode = rs.getInt("plan_mode");
        String draftMarkdown = rs.getString("draft_markdown");
        String draftStatus = rs.getString("draft_status");
        int fallback = rs.getInt("fallback");
        String failureReason = rs.getString("failure_reason");
        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        Instant createdAt = createdAtTs != null ? createdAtTs.toInstant() : null;
        Instant updatedAt = updatedAtTs != null ? updatedAtTs.toInstant() : null;
        return new PlanDraftRecord(id, planId, userId, planMode, draftMarkdown,
                draftStatus, fallback, failureReason, createdAt, updatedAt);
    }
}