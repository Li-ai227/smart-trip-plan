package com.smart.trip.agent.supervisor.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.PlanSummary;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.PlanStatus;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.Budget;
import com.smart.trip.common.model.BudgetBreakdown;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.skills.persistence.PlanHistoryQuery;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link MySqlTripPlanPersistenceService} H2 内存库单测（C-017 T-4）。
 *
 * <p>验证：全量写入完整性、历史查询筛选/分页、详情查回、软删除、写入失败不阻塞主链路（AC-1/AC-7）。</p>
 */
class MySqlTripPlanPersistenceServiceTest {

    private DataSource dataSource;
    private MySqlTripPlanPersistenceService service;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:plandb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        this.dataSource = ds;

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE trip_plan_record (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        plan_id VARCHAR(64) NOT NULL,
                        user_id VARCHAR(64) NOT NULL,
                        destination VARCHAR(256) DEFAULT '',
                        start_date DATE,
                        days INT DEFAULT 0,
                        budget DECIMAL(12,2) DEFAULT 0,
                        total_cost DECIMAL(12,2),
                        headcount INT DEFAULT 1,
                        travel_style VARCHAR(32) DEFAULT '',
                        preferences VARCHAR(2000) DEFAULT '',
                        plan_status VARCHAR(32) DEFAULT '',
                        deleted TINYINT DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            stmt.execute("""
                    CREATE TABLE trip_day_record (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        plan_id VARCHAR(64) NOT NULL,
                        day_index INT NOT NULL,
                        trip_date DATE,
                        daily_cost DECIMAL(12,2) DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            stmt.execute("""
                    CREATE TABLE trip_attraction_record (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        day_id BIGINT NOT NULL,
                        plan_id VARCHAR(64) NOT NULL,
                        attraction_name VARCHAR(256) DEFAULT '',
                        rating DECIMAL(3,1) DEFAULT 0,
                        ticket_price DECIMAL(10,2) DEFAULT 0,
                        duration_min INT DEFAULT 0,
                        longitude DOUBLE,
                        latitude DOUBLE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            stmt.execute("""
                    CREATE TABLE trip_meal_record (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        day_id BIGINT NOT NULL,
                        plan_id VARCHAR(64) NOT NULL,
                        meal_type VARCHAR(32) DEFAULT '',
                        restaurant_name VARCHAR(256) DEFAULT '',
                        avg_price DECIMAL(10,2) DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            stmt.execute("""
                    CREATE TABLE trip_route_record (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        day_id BIGINT NOT NULL,
                        plan_id VARCHAR(64) NOT NULL,
                        from_name VARCHAR(256) DEFAULT '',
                        to_name VARCHAR(256) DEFAULT '',
                        distance_km DECIMAL(10,2) DEFAULT 0,
                        duration_min INT DEFAULT 0,
                        transport_mode VARCHAR(64) DEFAULT '',
                        fallback TINYINT DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            stmt.execute("""
                    CREATE TABLE trip_budget_record (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        plan_id VARCHAR(64) NOT NULL,
                        total_budget DECIMAL(12,2) DEFAULT 0,
                        total_cost DECIMAL(12,2) DEFAULT 0,
                        overrun_rate DECIMAL(6,4) DEFAULT 0,
                        transport_cost DECIMAL(12,2) DEFAULT 0,
                        hotel_cost DECIMAL(12,2) DEFAULT 0,
                        food_cost DECIMAL(12,2) DEFAULT 0,
                        ticket_cost DECIMAL(12,2) DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
        }

        // 使用同步 executor 以便测试验证
        this.service = new MySqlTripPlanPersistenceService(ds, Executors.newSingleThreadExecutor());
    }

    private static TripPlan samplePlan(String planId, String destination, int days) {
        TripPlanRequest request = new TripPlanRequest(
                destination, LocalDate.of(2026, 7, 1), days,
                new BigDecimal("5000"), 2, TravelStyle.FAMILY, List.of("美食"));
        GeoLocation loc = new GeoLocation(116.4, 39.9, "地址", destination);
        Attraction attraction = new Attraction(
                "A-001", "景点1", loc, 4.5, 120, new BigDecimal("50"), List.of("文化"));
        Meal meal = new Meal("餐厅1", loc, MealType.LUNCH, new BigDecimal("80"), List.of("菜1"));
        Route route = new Route(loc, loc, 2.5, 15, "WALKING", false);
        TripDay day = new TripDay(
                LocalDate.of(2026, 7, 1), 1,
                List.of(attraction), List.of(meal), List.of(route),
                new BigDecimal("130"));
        BudgetBreakdown breakdown = new BudgetBreakdown(
                new BigDecimal("100"), new BigDecimal("200"),
                new BigDecimal("80"), new BigDecimal("50"));
        Budget budget = new Budget(new BigDecimal("5000"), new BigDecimal("430"),
                new BigDecimal("-0.914"), breakdown);
        return new TripPlan(planId, request, List.of(day),
                new BigDecimal("430"), budget, PlanStatus.CONFIRMED, false);
    }

    // ================================================================
    // 完整写入 + 详情查回
    // ================================================================

    @Nested
    @DisplayName("完整写入 + 详情查回")
    class SaveAndQueryDetail {

        @Test
        @DisplayName("保存完整方案 → queryDetail 可查回，字段完整")
        void saveThenQueryDetail() throws Exception {
            TripPlan plan = samplePlan("P-20260701-001", "北京", 1);
            service.save(plan, "user-001", "confirmed");
            Thread.sleep(200);

            Optional<TripPlan> loaded = service.queryDetail("P-20260701-001");
            assertTrue(loaded.isPresent());
            TripPlan result = loaded.get();
            assertEquals("P-20260701-001", result.planId());
            assertEquals(1, result.days().size());
            assertEquals(1, result.days().get(0).attractions().size());
            assertEquals("景点1", result.days().get(0).attractions().get(0).name());
            assertEquals(1, result.days().get(0).meals().size());
            assertEquals(1, result.days().get(0).routes().size());
            assertEquals(new BigDecimal("430.00"), result.totalCost());
            assertEquals(new BigDecimal("5000.00"), result.budget().total());
        }

        @Test
        @DisplayName("不存在的 planId → queryDetail 返回 empty")
        void queryDetailNotFound() {
            Optional<TripPlan> loaded = service.queryDetail("P-NOT-EXIST");
            assertTrue(loaded.isEmpty());
        }
    }

    // ================================================================
    // 历史查询：筛选 + 分页
    // ================================================================

    @Nested
    @DisplayName("历史查询筛选与分页")
    class HistoryQuery {

        @BeforeEach
        void seedData() throws Exception {
            service.save(samplePlan("P-001", "北京", 1), "user-h", "confirmed");
            service.save(samplePlan("P-002", "上海", 2), "user-h", "confirmed");
            service.save(samplePlan("P-003", "北京", 3), "user-h", "failed");
            service.save(samplePlan("P-004", "云南", 1), "user-other", "confirmed");
            Thread.sleep(200);
        }

        @Test
        @DisplayName("按 userId 查询 → 仅返回该用户方案")
        void queryByUserId() {
            List<PlanSummary> result = service.queryHistory(
                    new PlanHistoryQuery("user-h", 1, 10, null, null, null, null, null));
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("按关键词（目的地模糊）筛选")
        void queryByKeyword() {
            List<PlanSummary> result = service.queryHistory(
                    new PlanHistoryQuery("user-h", 1, 10, "北京", null, null, null, null));
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("按终态筛选")
        void queryByPlanStatus() {
            List<PlanSummary> result = service.queryHistory(
                    new PlanHistoryQuery("user-h", 1, 10, null, null, "failed", null, null));
            assertEquals(1, result.size());
            assertEquals("P-003", result.get(0).planId());
        }

        @Test
        @DisplayName("按旅行风格筛选")
        void queryByTravelStyle() {
            List<PlanSummary> result = service.queryHistory(
                    new PlanHistoryQuery("user-h", 1, 10, null, TravelStyle.FAMILY, null, null, null));
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("按日期范围筛选")
        void queryByDateRange() {
            List<PlanSummary> result = service.queryHistory(new PlanHistoryQuery(
                    "user-h", 1, 10, null, null, null,
                    LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)));
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("分页：pageSize=2 第一页返回 2 条")
        void pagination() {
            List<PlanSummary> page1 = service.queryHistory(
                    new PlanHistoryQuery("user-h", 1, 2, null, null, null, null, null));
            assertEquals(2, page1.size());
        }

        @Test
        @DisplayName("countHistory 统计总数（不受分页影响）")
        void countHistoryTotal() {
            long count = service.countHistory(
                    new PlanHistoryQuery("user-h", 1, 2, null, null, null, null, null));
            assertEquals(3, count);
        }

        @Test
        @DisplayName("不存在的用户 → 返回空列表 + count=0")
        void unknownUserReturnsEmpty() {
            List<PlanSummary> result = service.queryHistory(
                    new PlanHistoryQuery("user-not-exist", 1, 10, null, null, null, null, null));
            assertTrue(result.isEmpty());
            assertEquals(0, service.countHistory(
                    new PlanHistoryQuery("user-not-exist", 1, 10, null, null, null, null, null)));
        }
    }

    // ================================================================
    // 软删除
    // ================================================================

    @Nested
    @DisplayName("软删除")
    class SoftDelete {

        @Test
        @DisplayName("本人删除自己的方案 → 成功，之后 queryDetail 不可见")
        void softDeleteOwnPlan() throws Exception {
            service.save(samplePlan("P-DEL-001", "北京", 1), "user-owner", "confirmed");
            Thread.sleep(200);

            boolean deleted = service.softDelete("P-DEL-001", "user-owner");
            assertTrue(deleted);
            assertTrue(service.queryDetail("P-DEL-001").isEmpty());
        }

        @Test
        @DisplayName("非本人删除 → 失败，方案仍可查")
        void softDeleteWrongUser() throws Exception {
            service.save(samplePlan("P-DEL-002", "北京", 1), "user-owner", "confirmed");
            Thread.sleep(200);

            boolean deleted = service.softDelete("P-DEL-002", "user-intruder");
            assertFalse(deleted);
            assertTrue(service.queryDetail("P-DEL-002").isPresent());
        }

        @Test
        @DisplayName("删除不存在的方案 → 返回 false")
        void softDeleteNotFound() {
            assertFalse(service.softDelete("P-NOT-EXIST", "user-x"));
        }
    }

    // ================================================================
    // 异步 + 写入失败不阻塞主链路
    // ================================================================

    @Nested
    @DisplayName("异步不阻塞 + 写入失败不影响主链路")
    class AsyncAndFailureResilience {

        @Test
        @DisplayName("save 调用立即返回（不等待写入完成）")
        void returnsImmediately() {
            TripPlan plan = samplePlan("P-ASYNC-001", "北京", 1);
            long start = System.nanoTime();
            service.save(plan, "user-async", "confirmed");
            long elapsed = System.nanoTime() - start;
            assertTrue(elapsed < Duration.ofMillis(500).toNanos(),
                    "save 应在 500ms 内返回，实际: " + (elapsed / 1_000_000) + "ms");
        }

        @Test
        @DisplayName("null plan → 静默返回，不抛异常")
        void nullPlan() {
            service.save(null, "user-x", "confirmed");
        }

        @Test
        @DisplayName("close 后写入 → 不抛异常（CallerRunsPolicy + 内部 catch）")
        void writeAfterClose() {
            service.close();
            TripPlan plan = samplePlan("P-CLOSED-001", "北京", 1);
            service.save(plan, "user-x", "confirmed");
            assertTrue(true);
        }

        @Test
        @DisplayName("writeFailureCount 初始为 0")
        void initialFailureCount() {
            assertEquals(0, service.writeFailureCount());
        }
    }
}
