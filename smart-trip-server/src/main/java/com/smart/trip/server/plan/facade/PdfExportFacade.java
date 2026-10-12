package com.smart.trip.server.plan.facade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.PlanStatus;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.Budget;
import com.smart.trip.common.model.BudgetBreakdown;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.skills.pdf.PdfExportContext;
import com.smart.trip.skills.pdf.PdfRenderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * PDF 导出门面（C-014 T-2 AC-1/AC-3/AC-6）。
 *
 * <p>职责：所有权校验 → 可导出状态门禁 → 加载行程数据 → 委托渲染。
 * 不含任何业务规则（不判定 budget/route），仅做导出层门面。</p>
 */
public class PdfExportFacade {

    private static final Logger LOG = Logger.getLogger(PdfExportFacade.class.getName());

    private static final Set<String> EXPORTABLE_STATUSES = Set.of("review", "confirmed");

    /** PDF 文件大小上限 10MB（AC-4）。 */
    static final long MAX_PDF_SIZE = 10L * 1024 * 1024;

    private final PlanCacheService planCacheService;
    private final PdfRenderService pdfRenderService;
    private final ObjectMapper objectMapper;

    /**
     * 构造 PDF 导出门面。
     *
     * @param planCacheService Redis 缓存服务
     * @param pdfRenderService PDF 渲染服务
     */
    public PdfExportFacade(PlanCacheService planCacheService, PdfRenderService pdfRenderService) {
        this(planCacheService, pdfRenderService, new ObjectMapper());
    }

    /**
     * 构造 PDF 导出门面（完整参数）。
     *
     * @param planCacheService Redis 缓存服务
     * @param pdfRenderService PDF 渲染服务
     * @param objectMapper     JSON 序列化/反序列化
     */
    public PdfExportFacade(PlanCacheService planCacheService,
                           PdfRenderService pdfRenderService,
                           ObjectMapper objectMapper) {
        this.planCacheService = planCacheService;
        this.pdfRenderService = pdfRenderService;
        this.objectMapper = objectMapper;
    }

    /**
     * 导出行程方案 PDF。
     *
     * @param planId 方案 ID
     * @param userId 当前用户 ID（所有权校验）
     * @return PDF 字节数组
     * @throws BaseException NOT_FOUND / FORBIDDEN / PLAN_NOT_EXPORTABLE
     */
    public byte[] exportPdf(String planId, long userId) {
        String status = checkExistence(planId);
        checkOwnership(planId, userId);
        checkExportable(status, planId);

        PlanStatus planStatus = PlanStatus.valueOf(status.toUpperCase(Locale.ROOT));
        TripPlan plan = loadTripPlan(planId, planStatus);
        plan = enrichFromSummary(plan, planId);
        String traceId = "T-" + UUID.randomUUID().toString().substring(0, 12);
        PdfExportContext ctx = new PdfExportContext(planId, planStatus, traceId, Instant.now());

        byte[] pdf = pdfRenderService.render(plan, ctx);
        checkFileSize(pdf, traceId);
        return pdf;
    }

    /**
     * 校验生成文件大小，超限时记录告警日志（AC-4 / T-5）。
     */
    private static void checkFileSize(byte[] pdf, String traceId) {
        if (pdf.length > MAX_PDF_SIZE) {
            LOG.warning(String.format(
                    "PDF 文件超限: size=%d, max=%d, traceId=%s",
                    pdf.length, MAX_PDF_SIZE, traceId));
        }
    }

    private String checkExistence(String planId) {
        return planCacheService.getStatus(planId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                        "规划不存在: " + planId));
    }

    private void checkOwnership(String planId, long userId) {
        String ownerId = planCacheService.getUserId(planId).orElse("");
        if (!String.valueOf(userId).equals(ownerId)) {
            throw new BaseException(ErrorCode.FORBIDDEN, "无权访问此规划");
        }
    }

    private static void checkExportable(String status, String planId) {
        if (!EXPORTABLE_STATUSES.contains(status)) {
            throw new BaseException(ErrorCode.PLAN_NOT_EXPORTABLE,
                    "当前状态不可导出: " + status + ", planId=" + planId);
        }
    }

    /**
     * 当 plan.request 字段为空时，从 summary key 补全（plan JSON 可能不含 request）。
     */
    private TripPlan enrichFromSummary(TripPlan plan, String planId) {
        TripPlanRequest req = plan.request();
        if (req != null && req.destination() != null && !req.destination().isBlank()) {
            return plan;
        }
        return planCacheService.getPlanSummary(planId)
                .map(json -> buildRequestFromSummary(json, plan))
                .orElse(plan);
    }

    private TripPlan buildRequestFromSummary(String summaryJson, TripPlan plan) {
        try {
            JsonNode s = objectMapper.readTree(summaryJson);
            TripPlanRequest req = new TripPlanRequest(
                    s.path("destination").asText(""),
                    parseDateNode(s.path("startDate")),
                    s.path("days").asInt(1),
                    toBigDecimal(s.path("budget")),
                    s.path("headcount").asInt(1),
                    parseTravelStyle(s.path("travelStyle").asText(null)),
                    List.of());
            return new TripPlan(plan.planId(), req, plan.days(),
                    plan.totalCost(), plan.budget(), plan.status(), plan.requiresHITL());
        } catch (Exception e) {
            return plan;
        }
    }

    /** 解析日期：兼容 "2026-07-01" 字符串和 [2026,7,1] 数组两种 Jackson 序列化格式。 */
    private static LocalDate parseDateNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray() && node.size() >= 3) {
            return LocalDate.of(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
        }
        return parseLocalDate(node.asText(null));
    }

    private TripPlan loadTripPlan(String planId, PlanStatus status) {
        String json = planCacheService.getPlan(planId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND,
                        "行程数据不存在: " + planId));
        try {
            return parseTripPlan(json, planId, status);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException(ErrorCode.NOT_FOUND,
                    "行程数据解析失败: " + planId);
        }
    }

    // ===== JSON 解析 =====

    private TripPlan parseTripPlan(String json, String planId, PlanStatus status)
            throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode requestNode = root.path("request");
        JsonNode planNode = root.path("plan");

        TripPlanRequest request = buildRequest(requestNode);
        List<TripDay> days = List.of();
        BigDecimal totalCost = BigDecimal.ZERO;
        Budget budget = new Budget(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BudgetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));

        if (!planNode.isMissingNode()) {
            totalCost = toBigDecimal(planNode.path("totalCost"));
            budget = buildBudget(planNode.path("budget"));
            days = buildDays(planNode.path("days"));
        }

        return new TripPlan(planId, request, days, totalCost, budget, status, false);
    }

    // ===== Request 解析 =====

    private static TripPlanRequest buildRequest(JsonNode node) {
        if (node.isMissingNode()) {
            return new TripPlanRequest("", null, 1, BigDecimal.ZERO, 1, null, List.of());
        }
        String dest = node.path("destination").asText("");
        LocalDate startDate = parseLocalDate(node.path("startDate").asText(null));
        int days = node.path("days").asInt(1);
        BigDecimal budget = toBigDecimal(node.path("budget"));
        int headcount = node.path("headcount").asInt(1);
        TravelStyle style = parseTravelStyle(node.path("travelStyle").asText(null));
        return new TripPlanRequest(dest, startDate, days, budget, headcount, style, List.of());
    }

    private static LocalDate parseLocalDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static TravelStyle parseTravelStyle(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return TravelStyle.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ===== Days 解析 =====

    private static List<TripDay> buildDays(JsonNode daysNode) {
        if (daysNode.isMissingNode() || !daysNode.isArray() || daysNode.isEmpty()) {
            return List.of();
        }
        List<TripDay> result = new ArrayList<>();
        for (JsonNode dayNode : daysNode) {
            result.add(buildDay(dayNode));
        }
        return Collections.unmodifiableList(result);
    }

    private static TripDay buildDay(JsonNode node) {
        LocalDate date = parseLocalDate(node.path("date").asText(null));
        int dayIndex = node.path("dayIndex").asInt(node.path("index").asInt(1));
        BigDecimal dailyCost = toBigDecimal(node.path("dailyCost"));

        List<Attraction> attractions = buildAttractions(node.path("attractions"));
        List<Meal> meals = buildMeals(node.path("meals"));
        List<Route> routes = buildRoutes(node.path("routes"));

        return new TripDay(date, dayIndex, attractions, meals, routes, dailyCost);
    }

    // ===== Attractions 解析 =====

    private static List<Attraction> buildAttractions(JsonNode node) {
        if (node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<Attraction> list = new ArrayList<>();
        for (JsonNode n : node) {
            list.add(buildAttraction(n));
        }
        return Collections.unmodifiableList(list);
    }

    private static Attraction buildAttraction(JsonNode node) {
        String id = node.path("attractionId").asText(
                node.path("id").asText(""));
        String name = node.path("name").asText("");
        GeoLocation loc = buildLocation(node.path("location"));
        double rating = node.path("rating").asDouble(0);
        int durationMin = node.path("durationMin").asInt(
                node.path("durationMinutes").asInt(0));
        BigDecimal ticket = toBigDecimal(node.path("ticketPrice"));
        if (ticket.signum() == 0) {
            ticket = toBigDecimal(node.path("ticket"));
        }
        return new Attraction(id, name, loc, rating, durationMin, ticket, List.of());
    }

    // ===== Meals 解析 =====

    private static List<Meal> buildMeals(JsonNode node) {
        if (node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<Meal> list = new ArrayList<>();
        for (JsonNode n : node) {
            list.add(buildMeal(n));
        }
        return Collections.unmodifiableList(list);
    }

    private static Meal buildMeal(JsonNode node) {
        String name = node.path("name").asText("");
        GeoLocation loc = buildLocation(node.path("location"));
        MealType type = parseMealType(node.path("mealType").asText(null));
        BigDecimal price = toBigDecimal(node.path("avgPrice"));
        if (price.signum() == 0) {
            price = toBigDecimal(node.path("perCapita"));
        }
        return new Meal(name, loc, type, price, List.of());
    }

    private static MealType parseMealType(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MealType.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ===== Routes 解析 =====

    private static List<Route> buildRoutes(JsonNode node) {
        if (node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<Route> list = new ArrayList<>();
        for (JsonNode n : node) {
            list.add(buildRoute(n));
        }
        return Collections.unmodifiableList(list);
    }

    private static Route buildRoute(JsonNode node) {
        GeoLocation from = buildLocation(node.path("from"));
        GeoLocation to = buildLocation(node.path("to"));
        double distance = node.path("distanceKm").asDouble(0);
        int duration = node.path("durationMin").asInt(0);
        String mode = node.path("transportMode").asText(
                node.path("mode").asText(""));
        boolean fallback = node.path("fallback").asBoolean(false);
        return new Route(from, to, distance, duration, mode, fallback);
    }

    // ===== Location 解析 =====

    private static GeoLocation buildLocation(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        // 纯字符串 → {address: ...}
        if (node.isTextual()) {
            return new GeoLocation(0, 0, node.asText(), "");
        }
        double lng = node.path("longitude").asDouble(
                node.path("lng").asDouble(0));
        double lat = node.path("latitude").asDouble(
                node.path("lat").asDouble(0));
        String address = node.path("address").asText("");
        String city = node.path("city").asText("");
        return new GeoLocation(lng, lat, address, city);
    }

    // ===== Budget 解析 =====

    private static Budget buildBudget(JsonNode node) {
        if (node.isMissingNode()) {
            return new Budget(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BudgetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO));
        }
        JsonNode bd = node.path("breakdown");
        BudgetBreakdown breakdown = new BudgetBreakdown(
                toBigDecimal(bd.path("transport")),
                toBigDecimal(bd.path("hotel")),
                toBigDecimal(bd.path("food")),
                toBigDecimal(bd.path("ticket")));
        return new Budget(
                toBigDecimal(node.path("total")),
                toBigDecimal(node.path("used")),
                toBigDecimal(node.path("overrunRate")),
                breakdown);
    }

    // ===== 数值解析 + API 兼容 =====

    private static BigDecimal toBigDecimal(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText("");
        if (text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
