package com.smart.trip.agent.supervisor.persistence;

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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史规划查询辅助（C-017 T-4）：负责历史列表筛选 SQL 拼装 + 详情跨表查回映射。
 *
 * <p>从 {@link MySqlTripPlanPersistenceService} 拆出，控制单文件行数 / 单方法圈复杂度，
 * 无状态，仅接收调用方传入的 {@link Connection}。</p>
 */
final class TripPlanReader {

    private static final String SELECT_DAYS_BY_PLAN =
            "SELECT * FROM trip_day_record WHERE plan_id = ? ORDER BY day_index ASC";

    private static final String SELECT_ATTRACTIONS_BY_DAY =
            "SELECT * FROM trip_attraction_record WHERE day_id = ? ORDER BY id ASC";

    private static final String SELECT_MEALS_BY_DAY =
            "SELECT * FROM trip_meal_record WHERE day_id = ? ORDER BY id ASC";

    private static final String SELECT_ROUTES_BY_DAY =
            "SELECT * FROM trip_route_record WHERE day_id = ? ORDER BY id ASC";

    private static final String SELECT_BUDGET_BY_PLAN =
            "SELECT * FROM trip_budget_record WHERE plan_id = ? ORDER BY id DESC LIMIT 1";

    private TripPlanReader() {
    }

    /**
     * 拼装历史查询 WHERE 筛选片段（不含分页），追加到调用方已构造的 SQL 与参数列表。
     *
     * @param sql    调用方 SQL StringBuilder（已含 base WHERE user_id/deleted）
     * @param params 调用方参数列表（已含 userId）
     * @param query  查询条件
     */
    static void appendFilters(StringBuilder sql, List<Object> params, PlanHistoryQuery query) {
        if (query.keyword() != null && !query.keyword().isBlank()) {
            sql.append(" AND destination LIKE ?");
            params.add("%" + query.keyword() + "%");
        }
        if (query.travelStyle() != null) {
            sql.append(" AND travel_style = ?");
            params.add(query.travelStyle().name());
        }
        if (query.planStatus() != null && !query.planStatus().isBlank()) {
            sql.append(" AND plan_status = ?");
            params.add(query.planStatus());
        }
        if (query.startDate() != null) {
            sql.append(" AND start_date >= ?");
            params.add(java.sql.Date.valueOf(query.startDate()));
        }
        if (query.endDate() != null) {
            sql.append(" AND start_date <= ?");
            params.add(java.sql.Date.valueOf(query.endDate()));
        }
    }

    /**
     * 绑定参数列表到 PreparedStatement（按顺序 setObject）。
     *
     * @param ps     语句
     * @param params 参数列表
     * @throws SQLException 绑定失败
     */
    static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /**
     * 从历史列表行映射为 {@link PlanSummary}。
     *
     * @param rs 结果集（游标已定位到当前行）
     * @return 摘要
     * @throws SQLException 读取失败
     */
    static PlanSummary mapPlanSummary(ResultSet rs) throws SQLException {
        LocalDate startDate = toLocalDate(rs.getDate("start_date"));
        return new PlanSummary(
                rs.getString("plan_id"),
                rs.getString("destination"),
                startDate,
                rs.getInt("days"),
                rs.getBigDecimal("budget"),
                rs.getBigDecimal("total_cost"),
                rs.getInt("headcount"),
                rs.getString("travel_style"),
                rs.getString("plan_status"),
                rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").getTime() : 0L);
    }

    /**
     * 从主表行 + 跨表查询重建完整 {@link TripPlan}（详情查回，Redis 未命中兜底）。
     *
     * @param conn JDBC 连接
     * @param rs   trip_plan_record 结果集（游标已定位到当前行）
     * @return 完整方案
     * @throws SQLException 查询失败
     */
    static TripPlan mapPlanRecord(Connection conn, ResultSet rs) throws SQLException {
        String planId = rs.getString("plan_id");
        TravelStyle travelStyle = parseTravelStyle(rs.getString("travel_style"));

        TripPlanRequest request = new TripPlanRequest(
                rs.getString("destination"), toLocalDate(rs.getDate("start_date")),
                rs.getInt("days"), rs.getBigDecimal("budget"), rs.getInt("headcount"),
                travelStyle, List.of());

        List<TripDay> days = queryDays(conn, planId);
        Budget budget = queryBudget(conn, planId);
        PlanStatus status = parsePlanStatus(rs.getString("plan_status"));

        return new TripPlan(planId, request, days, rs.getBigDecimal("total_cost"),
                budget, status, false);
    }

    private static List<TripDay> queryDays(Connection conn, String planId) throws SQLException {
        List<TripDay> days = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_DAYS_BY_PLAN)) {
            ps.setString(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long dayId = rs.getLong("id");
                    days.add(new TripDay(
                            toLocalDate(rs.getDate("trip_date")), rs.getInt("day_index"),
                            queryAttractions(conn, dayId), queryMeals(conn, dayId),
                            queryRoutes(conn, dayId), rs.getBigDecimal("daily_cost")));
                }
            }
        }
        return days;
    }

    private static List<Attraction> queryAttractions(Connection conn, long dayId) throws SQLException {
        List<Attraction> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_ATTRACTIONS_BY_DAY)) {
            ps.setLong(1, dayId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapAttraction(rs));
                }
            }
        }
        return list;
    }

    private static Attraction mapAttraction(ResultSet rs) throws SQLException {
        Double lon = (Double) rs.getObject("longitude");
        Double lat = (Double) rs.getObject("latitude");
        GeoLocation loc = (lon != null && lat != null) ? new GeoLocation(lon, lat, "", "") : null;
        return new Attraction(
                "", rs.getString("attraction_name"), loc,
                rs.getBigDecimal("rating").doubleValue(), rs.getInt("duration_min"),
                rs.getBigDecimal("ticket_price"), List.of());
    }

    private static List<Meal> queryMeals(Connection conn, long dayId) throws SQLException {
        List<Meal> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_MEALS_BY_DAY)) {
            ps.setLong(1, dayId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Meal(
                            rs.getString("restaurant_name"), null,
                            parseMealType(rs.getString("meal_type")),
                            rs.getBigDecimal("avg_price"), List.of()));
                }
            }
        }
        return list;
    }

    private static List<Route> queryRoutes(Connection conn, long dayId) throws SQLException {
        List<Route> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_ROUTES_BY_DAY)) {
            ps.setLong(1, dayId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GeoLocation from = new GeoLocation(0, 0, rs.getString("from_name"), "");
                    GeoLocation to = new GeoLocation(0, 0, rs.getString("to_name"), "");
                    list.add(new Route(
                            from, to, rs.getBigDecimal("distance_km").doubleValue(),
                            rs.getInt("duration_min"), rs.getString("transport_mode"),
                            rs.getBoolean("fallback")));
                }
            }
        }
        return list;
    }

    private static Budget queryBudget(Connection conn, String planId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BUDGET_BY_PLAN)) {
            ps.setString(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                BudgetBreakdown breakdown = new BudgetBreakdown(
                        rs.getBigDecimal("transport_cost"), rs.getBigDecimal("hotel_cost"),
                        rs.getBigDecimal("food_cost"), rs.getBigDecimal("ticket_cost"));
                return new Budget(
                        rs.getBigDecimal("total_budget"), rs.getBigDecimal("total_cost"),
                        rs.getBigDecimal("overrun_rate"), breakdown);
            }
        }
    }

    // ===== 解析工具 =====

    private static LocalDate toLocalDate(java.sql.Date date) {
        return date != null ? date.toLocalDate() : null;
    }

    private static TravelStyle parseTravelStyle(String value) {
        return value != null && !value.isBlank() ? TravelStyle.valueOf(value) : null;
    }

    private static MealType parseMealType(String value) {
        return value != null && !value.isBlank() ? MealType.valueOf(value) : null;
    }

    private static PlanStatus parsePlanStatus(String value) {
        if ("confirmed".equalsIgnoreCase(value)) {
            return PlanStatus.CONFIRMED;
        }
        if ("failed".equalsIgnoreCase(value)) {
            return PlanStatus.FAILED;
        }
        return PlanStatus.CONFIRMED;
    }
}
