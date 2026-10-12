-- =============================================================================
-- V001: C-010 审计追踪表（Docker 初始化脚本）
-- 源：smart-trip-agent-supervisor/src/main/resources/sql/schema.sql
-- =============================================================================

CREATE DATABASE IF NOT EXISTS smart_trip_plan_trace
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_trip_plan_trace;

-- -----------------------------------------------------------------------------
-- 子 Agent 委派记录
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_agent_delegation_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)    NOT NULL COMMENT '全链路追踪 ID',
    plan_id         VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '行程方案 ID',
    parent_agent_id VARCHAR(128)   NOT NULL DEFAULT 'agent://supervisor' COMMENT '委派方 Agent',
    sub_agent_id    VARCHAR(128)   NOT NULL COMMENT '被委派子 Agent（如 agent://xhs-analyst）',
    task_type       VARCHAR(64)    NOT NULL COMMENT '任务类型（XHS_ANALYSIS/ROUTE_PLANNING/ITINERARY_DESIGN/COST_CALCULATION）',
    task_summary    VARCHAR(2000)  NOT NULL DEFAULT '' COMMENT '委派任务内容摘要（已脱敏+截断）',
    response_summary VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '响应摘要（已截断）',
    duration_ms     BIGINT         NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    fallback        TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否降级',
    error_message   VARCHAR(2000)  NOT NULL DEFAULT '' COMMENT '错误信息',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    INDEX idx_trace_id (trace_id),
    INDEX idx_plan_id (plan_id),
    INDEX idx_sub_agent (sub_agent_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='子 Agent 委派审计记录';

-- -----------------------------------------------------------------------------
-- LLM 调用记录
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_llm_call_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)    NOT NULL COMMENT '全链路追踪 ID',
    plan_id         VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '行程方案 ID',
    model_id        VARCHAR(128)   NOT NULL DEFAULT 'unknown' COMMENT '模型标识（deepseek-v4-pro/qwen3-max）',
    prompt_summary  VARCHAR(3000)  NOT NULL DEFAULT '' COMMENT '提示词摘要（已脱敏+截断）',
    output_summary  VARCHAR(3000)  NOT NULL DEFAULT '' COMMENT '输出摘要（已截断）',
    duration_ms     BIGINT         NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    token_estimate  INT            NOT NULL DEFAULT 0 COMMENT 'Token 用量估算（输入+输出）',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    INDEX idx_trace_id (trace_id),
    INDEX idx_plan_id (plan_id),
    INDEX idx_model_id (model_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='LLM 调用审计记录';

-- -----------------------------------------------------------------------------
-- 编排汇总记录
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_orchestration_summary (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id            VARCHAR(64)    NOT NULL COMMENT '全链路追踪 ID',
    plan_id             VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '行程方案 ID',
    orchestration_path  VARCHAR(32)    NOT NULL DEFAULT 'UNKNOWN' COMMENT '编排路径（NATIVE/DETERMINISTIC）',
    total_duration_ms   BIGINT         NOT NULL DEFAULT 0 COMMENT '总耗时毫秒',
    fallback_count      INT            NOT NULL DEFAULT 0 COMMENT '子 Agent 降级计数',
    hitl_triggered      TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否触发 HITL',
    hitl_reasons        VARCHAR(1000)  NOT NULL DEFAULT '' COMMENT 'HITL 触发原因（逗号分隔）',
    final_plan_status   VARCHAR(32)    NOT NULL DEFAULT '' COMMENT '最终 PlanStatus',
    created_at          DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    UNIQUE INDEX uk_trace_id (trace_id),
    INDEX idx_plan_id (plan_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='编排汇总审计记录';
