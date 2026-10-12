package com.smart.trip.agent.supervisor.persistence;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.Budget;
import com.smart.trip.common.model.BudgetBreakdown;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.skills.trace.SensitiveDataMasker;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 历史规划写入辅助（C-017 T-4）：负责单个事务内 6 张业务表的 JDBC 写入。
 *
 * <p>从 {@link MySqlTripPlanPersistenceService} 拆出，控制单文件行数 / 单方法圈复杂度，
 * 不持有状态，仅接收调用方传入的 {@link Connection}（事务边界由调用方控制）。</p>
 */
final class TripPlanWriter {

    private static final int PREFERENCES_MAX_LENGTH = 2000;
    private static final int NAME_MAX_LENGTH = 256;

    private static final String INSERT_PLAN =
            "INSERT INTO trip_plan_record "
            + "(plan_id, user_id, destination, start_date, days, budget, total_cost, "
            + "headcount, travel_style, preferences, plan_status, deleted, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)";

    private static final String INSERT_DAY =
            "INSERT INTO trip_day_record (plan_id, day_index, trip_date, daily_cost, created_at) "
            + "VALUES (?, ?, ?, ?, ?)";

    private static final String INSERT_ATTRACTION =
            "INSERT INTO trip_attraction_record "
            + "(day_id, plan_id, attraction_name, rating, ticket_price, duration_min, "
            + "longitude, latitude, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_MEAL =
            "INSERT INTO trip_meal_record "
            + "(day_id, plan_id, meal_type, restaurant_name, avg_price, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String INSERT_ROUTE =
            "INSERT INTO trip_route_record "
            + "(day_id, plan_id, from_name, to_name, distance_km, duration_min, "
            + "transport_mode, fallback, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_BUDGET =
            "INSERT INTO trip_budget_record "
            + "(plan_id, total_budget, total_cost, overrun_rate, transport_cost, "
            + "hotel_cost, food_cost, ticket_cost, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private TripPlanWriter() {
    }

    /**
     * 在同一事务中写入完整方案（主表 + 每日 + 景点/餐饮/路线 + 费用）。
     *
     * @param conn       JDBC 连接（调用方管理事务提交/回滚）
     * @param plan       完整方案
     * @param userId     用户 ID
     * @param planStatus 终态
     * @throws SQLException 任一写入失败
     */
    static void writePlan(Connection conn, TripPlan plan, String userId, String planStatus)
            throws SQLException {
        insertPlan(conn, plan, userId, planStatus);
        for (TripDay day : plan.days()) {
            long dayId = insertDay(conn, plan.planId(), day);
            for (Attraction attraction : day.attractions()) {
                insertAttraction(conn, dayId, plan.planId(), attraction);
            }
            for (Meal meal : day.meals()) {
                insertMeal(conn, dayId, plan.planId(), meal);
            }
            for (Route route : day.routes()) {
                insertRoute(conn, dayId, plan.planId(), route);
            }
        }
        if (plan.budget() != null) {
            insertBudget(conn, plan.planId(), plan.budget());
        }
    }

    private static void insertPlan(Connection conn, TripPlan plan, String userId, String planStatus)
            throws SQLException {
        NormalizedRequest req = NormalizedRequest.of(plan.request());
        try (PreparedStatement ps = conn.prepareStatement(INSERT_PLAN, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, plan.planId());
            ps.setString(2, userId);
            ps.setString(3, truncate(req.destination()));
            setNullableDate(ps, 4, req.startDate());
            ps.setInt(5, req.days());
            ps.setBigDecimal(6, req.budget());
            setNullableDecimal(ps, 7, plan.totalCost());
            ps.setInt(8, req.headcount());
            ps.setString(9, req.travelStyle());
            ps.setString(10, SensitiveDataMasker.maskAndTruncate(req.preferences(), PREFERENCES_MAX_LENGTH));
            ps.setString(11, planStatus == null ? "" : planStatus);
            ps.setTimestamp(12, now());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    keys.getLong(1);
                }
            }
        }
    }

    private static long insertDay(Connection conn, String planId, TripDay day) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_DAY, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, planId);
            ps.setInt(2, day.dayIndex());
            setNullableDate(ps, 3, day.date());
            ps.setBigDecimal(4, orZero(day.dailyCost()));
            ps.setTimestamp(5, now());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    private static void insertAttraction(Connection conn, long dayId, String planId, Attraction a)
            throws SQLException {
        GeoLocation loc = a.location();
        try (PreparedStatement ps = conn.prepareStatement(INSERT_ATTRACTION)) {
            ps.setLong(1, dayId);
            ps.setString(2, planId);
            ps.setString(3, truncate(a.name()));
            ps.setBigDecimal(4, BigDecimal.valueOf(a.rating()));
            ps.setBigDecimal(5, orZero(a.ticketPrice()));
            ps.setInt(6, a.durationMin());
            setNullableDouble(ps, 7, loc == null ? null : loc.longitude());
            setNullableDouble(ps, 8, loc == null ? null : loc.latitude());
            ps.setTimestamp(9, now());
            ps.executeUpdate();
        }
    }

    private static void insertMeal(Connection conn, long dayId, String planId, Meal meal)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_MEAL)) {
            ps.setLong(1, dayId);
            ps.setString(2, planId);
            ps.setString(3, meal.mealType() == null ? "" : meal.mealType().name());
            ps.setString(4, truncate(meal.name()));
            ps.setBigDecimal(5, orZero(meal.avgPrice()));
            ps.setTimestamp(6, now());
            ps.executeUpdate();
        }
    }

    private static void insertRoute(Connection conn, long dayId, String planId, Route route)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_ROUTE)) {
            ps.setLong(1, dayId);
            ps.setString(2, planId);
            ps.setString(3, addressOf(route.from()));
            ps.setString(4, addressOf(route.to()));
            ps.setBigDecimal(5, BigDecimal.valueOf(route.distanceKm()));
            ps.setInt(6, route.durationMin());
            ps.setString(7, route.transportMode() == null ? "" : route.transportMode());
            ps.setBoolean(8, route.fallback());
            ps.setTimestamp(9, now());
            ps.executeUpdate();
        }
    }

    private static void insertBudget(Connection conn, String planId, Budget budget) throws SQLException {
        NormalizedBreakdown b = NormalizedBreakdown.of(budget.breakdown());
        try (PreparedStatement ps = conn.prepareStatement(INSERT_BUDGET)) {
            ps.setString(1, planId);
            ps.setBigDecimal(2, orZero(budget.total()));
            ps.setBigDecimal(3, orZero(budget.used()));
            ps.setBigDecimal(4, orZero(budget.overrunRate()));
            ps.setBigDecimal(5, b.transport());
            ps.setBigDecimal(6, b.hotel());
            ps.setBigDecimal(7, b.food());
            ps.setBigDecimal(8, b.ticket());
            ps.setTimestamp(9, now());
            ps.executeUpdate();
        }
    }

    // ===== 工具方法 =====

    private static String truncate(String text) {
        return SensitiveDataMasker.truncate(text, NAME_MAX_LENGTH);
    }

    private static String addressOf(GeoLocation location) {
        return location == null ? "" : truncate(location.address());
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    private static void setNullableDate(PreparedStatement ps, int index, java.time.LocalDate date)
            throws SQLException {
        if (date != null) {
            ps.setDate(index, java.sql.Date.valueOf(date));
        } else {
            ps.setNull(index, java.sql.Types.DATE);
        }
    }

    private static void setNullableDecimal(PreparedStatement ps, int index, BigDecimal value)
            throws SQLException {
        if (value != null) {
            ps.setBigDecimal(index, value);
        } else {
            ps.setNull(index, java.sql.Types.DECIMAL);
        }
    }

    private static void setNullableDouble(PreparedStatement ps, int index, Double value)
            throws SQLException {
        if (value != null) {
            ps.setDouble(index, value);
        } else {
            ps.setNull(index, java.sql.Types.DOUBLE);
        }
    }

    /**
     * 请求字段归一（null 安全默认值），消除写入方法中的分支判断。
     */
    private record NormalizedRequest(
            String destination, java.time.LocalDate startDate, int days, BigDecimal budget,
            int headcount, String travelStyle, String preferences) {

        static NormalizedRequest of(TripPlanRequest request) {
            if (request == null) {
                return new NormalizedRequest("", null, 0, BigDecimal.ZERO, 1, "", "");
            }
            String style = request.travelStyle() != null ? request.travelStyle().name() : "";
            String prefs = request.preferences() == null || request.preferences().isEmpty()
                    ? "" : String.join(",", request.preferences());
            return new NormalizedRequest(
                    request.destination(), request.startDate(), request.days(),
                    request.budget(), request.headcount(), style, prefs);
        }
    }

    /**
     * 费用拆分归一（null 安全默认值）。
     */
    private record NormalizedBreakdown(BigDecimal transport, BigDecimal hotel,
            BigDecimal food, BigDecimal ticket) {

        static NormalizedBreakdown of(BudgetBreakdown breakdown) {
            if (breakdown == null) {
                return new NormalizedBreakdown(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            return new NormalizedBreakdown(
                    orZero(breakdown.transport()), orZero(breakdown.hotel()),
                    orZero(breakdown.food()), orZero(breakdown.ticket()));
        }
    }
}
