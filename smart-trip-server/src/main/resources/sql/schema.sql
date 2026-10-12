-- =============================================================================
-- C-011 用户认证表 DDL（MySQL 8.0+）
-- 数据库：smart_trip_plan
-- =============================================================================

CREATE DATABASE IF NOT EXISTS smart_trip_plan
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_trip_plan;

-- -----------------------------------------------------------------------------
-- 用户表（AC-5）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)    NOT NULL COMMENT '用户名（唯一）',
    password_hash   VARCHAR(128)   NOT NULL COMMENT 'BCrypt 哈希',
    nickname        VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '昵称',
    role            VARCHAR(16)    NOT NULL DEFAULT 'USER' COMMENT '角色（USER/ADMIN）',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    UNIQUE INDEX uk_username (username),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表（C-011 AC-5）';
