-- =============================================================================
-- V002: C-017 历史规划持久化业务表（Docker 初始化脚本）
-- 源：smart-trip-agent-supervisor/src/main/resources/sql/plan-persistence-schema.sql
-- =============================================================================

USE smart_trip_plan_trace;

-- -----------------------------------------------------------------------------
-- 规划主表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_plan_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id         VARCHAR(64)    NOT NULL COMMENT '方案 ID（P-yyyyMMdd-NNN）',
    user_id         VARCHAR(64)    NOT NULL COMMENT '用户 ID',
    destination     VARCHAR(256)   NOT NULL DEFAULT '' COMMENT '目的地',
    start_date      DATE           NULL COMMENT '出发日期',
    days            INT            NOT NULL DEFAULT 0 COMMENT '天数',
    budget          DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '总预算',
    total_cost      DECIMAL(12,2)  NULL COMMENT '实际总费用',
    headcount       INT            NOT NULL DEFAULT 1 COMMENT '人数',
    travel_style    VARCHAR(32)    NOT NULL DEFAULT '' COMMENT '旅行风格（FAMILY/COUPLE/SOLO/TEAM）',
    preferences     VARCHAR(2000)  NOT NULL DEFAULT '' COMMENT '偏好标签（已脱敏）',
    plan_status     VARCHAR(32)    NOT NULL DEFAULT '' COMMENT '终态（confirmed/failed）',
    deleted         TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '软删除标记',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    INDEX idx_plan_id (plan_id),
    INDEX idx_user_id (user_id),
    INDEX idx_destination (destination(64)),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史规划主表（ADR-012）';

-- -----------------------------------------------------------------------------
-- 每日行程表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_day_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id         VARCHAR(64)    NOT NULL COMMENT '方案 ID',
    day_index       INT            NOT NULL COMMENT '第几天（1-based）',
    trip_date       DATE           NULL COMMENT '当日日期',
    daily_cost      DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '当日小计',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX idx_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='每日行程表';

-- -----------------------------------------------------------------------------
-- 景点记录表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_attraction_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    day_id          BIGINT         NOT NULL COMMENT 'trip_day_record.id',
    plan_id         VARCHAR(64)    NOT NULL COMMENT '方案 ID',
    attraction_name VARCHAR(256)   NOT NULL DEFAULT '' COMMENT '景点名称',
    rating          DECIMAL(3,1)   NOT NULL DEFAULT 0 COMMENT '评分',
    ticket_price    DECIMAL(10,2)  NOT NULL DEFAULT 0 COMMENT '门票价格',
    duration_min    INT            NOT NULL DEFAULT 0 COMMENT '游玩时长（分钟）',
    longitude       DOUBLE         NULL COMMENT '经度',
    latitude        DOUBLE         NULL COMMENT '纬度',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX idx_day_id (day_id),
    INDEX idx_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='景点记录表';

-- -----------------------------------------------------------------------------
-- 餐饮记录表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_meal_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    day_id          BIGINT         NOT NULL COMMENT 'trip_day_record.id',
    plan_id         VARCHAR(64)    NOT NULL COMMENT '方案 ID',
    meal_type       VARCHAR(32)    NOT NULL DEFAULT '' COMMENT '餐类型（BREAKFAST/LUNCH/DINNER）',
    restaurant_name VARCHAR(256)   NOT NULL DEFAULT '' COMMENT '餐厅名称',
    avg_price       DECIMAL(10,2)  NOT NULL DEFAULT 0 COMMENT '人均价格',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX idx_day_id (day_id),
    INDEX idx_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='餐饮记录表';

-- -----------------------------------------------------------------------------
-- 路线记录表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_route_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    day_id          BIGINT         NOT NULL COMMENT 'trip_day_record.id',
    plan_id         VARCHAR(64)    NOT NULL COMMENT '方案 ID',
    from_name       VARCHAR(256)   NOT NULL DEFAULT '' COMMENT '起点名称',
    to_name         VARCHAR(256)   NOT NULL DEFAULT '' COMMENT '终点名称',
    distance_km     DECIMAL(10,2)  NOT NULL DEFAULT 0 COMMENT '距离（km）',
    duration_min    INT            NOT NULL DEFAULT 0 COMMENT '耗时（分钟）',
    transport_mode  VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '交通方式',
    fallback        TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否降级路线',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX idx_day_id (day_id),
    INDEX idx_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='路线记录表';

-- -----------------------------------------------------------------------------
-- 费用记录表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_budget_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id         VARCHAR(64)    NOT NULL COMMENT '方案 ID',
    total_budget    DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '总预算',
    total_cost      DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '实际总费用',
    overrun_rate    DECIMAL(5,4)   NOT NULL DEFAULT 0 COMMENT '超支率',
    transport_cost  DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '交通费',
    hotel_cost      DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '住宿费',
    food_cost       DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '餐饮费',
    ticket_cost     DECIMAL(12,2)  NOT NULL DEFAULT 0 COMMENT '门票费',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX idx_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='费用记录表（ADR-012）';
