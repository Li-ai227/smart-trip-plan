-- =============================================================================
-- V003: C-018 计划模式草案持久化表（Docker 初始化脚本）
-- 描述：存储 plan_review 态的草案 Markdown 全文，确保服务重启后不丢失
-- 源：smart-trip-server/src/main/resources/sql/plan-draft-schema.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `trip_plan_draft_record` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `plan_id`         VARCHAR(64)     NOT NULL                 COMMENT '方案 ID（对应 trip_plan_record.plan_id）',
    `user_id`         VARCHAR(64)     NOT NULL                 COMMENT '用户 ID',
    `plan_mode`       TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否开启计划模式（1=开启）',
    `draft_markdown`  MEDIUMTEXT      NULL                     COMMENT '计划草案 Markdown 全文',
    `draft_status`    VARCHAR(32)     NOT NULL DEFAULT 'plan_review' COMMENT '草案状态（plan_review/confirmed/rejected/failed）',
    `fallback`        TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否降级（LLM 未产出草案时=1）',
    `failure_reason`  VARCHAR(2000)   NULL                     COMMENT '失败原因',
    `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_plan_id` (`plan_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_draft_status` (`draft_status`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='计划模式草案持久化表（C-018）：存储 plan_review 态草案，重启后自动恢复';