package com.smart.trip.server.plan.repository;

import java.time.Instant;

/**
 * 计划模式草案持久化记录（C-018 AC-3 / V003）。
 *
 * <p>对应 MySQL 表 {@code trip_plan_draft_record}，记录 plan_review 态的草案 Markdown 全文，
 * 确保服务重启后不丢失。</p>
 *
 * @param id              主键 ID
 * @param planId          方案 ID（对应 trip_plan_record.plan_id）
 * @param userId          用户 ID
 * @param planMode        是否开启计划模式（1=开启）
 * @param draftMarkdown   计划草案 Markdown 全文
 * @param draftStatus     草案状态（plan_review / confirmed / failed）
 * @param fallback        是否降级（0=正常，1=降级）
 * @param failureReason   失败原因（draftStatus=failed 时有值）
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record PlanDraftRecord(
        Long id,
        String planId,
        String userId,
        int planMode,
        String draftMarkdown,
        String draftStatus,
        int fallback,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {

    /** 草案状态：plan_review，等待用户确认。 */
    public static final String STATUS_PLAN_REVIEW = "plan_review";
    /** 草案状态：confirmed，用户已确认。 */
    public static final String STATUS_CONFIRMED = "confirmed";
    /** 草案状态：failed，执行失败。 */
    public static final String STATUS_FAILED = "failed";
}