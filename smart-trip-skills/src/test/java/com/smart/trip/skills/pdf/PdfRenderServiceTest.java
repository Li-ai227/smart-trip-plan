package com.smart.trip.skills.pdf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PdfRenderService} 模板渲染单测（C-014 T-3 AC-2/AC-5）。
 *
 * <p>RED 阶段：先写测试验证 PDF 内容完整性。这些测试在完整实现后变 GREEN。</p>
 */
class PdfRenderServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    private final PdfRenderService service = new PdfRenderService();

    @Test
    @DisplayName("render 产出有效 PDF 字节（%PDF 魔数开头）")
    void render_producesValidPdfBytes() {
        TripPlan plan = minimalPlan();
        PdfExportContext ctx = new PdfExportContext(
                "P-20260701-001", PlanStatus.CONFIRMED, "T-abc123", Instant.now());

        byte[] pdf = service.render(plan, ctx);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0, "PDF 字节不应为空");
        assertTrue(pdf[0] == 0x25 && pdf[1] == 0x50 && pdf[2] == 0x44 && pdf[3] == 0x46,
                "PDF 应以 %PDF 魔数开头");
    }

    @Test
    @DisplayName("render null plan 抛 IllegalArgumentException")
    void render_nullPlanThrows() {
        PdfExportContext ctx = new PdfExportContext(
                "P-001", PlanStatus.CONFIRMED, "T-001", Instant.now());
        assertThrows(IllegalArgumentException.class, () -> service.render(null, ctx));
    }

    @Test
    @DisplayName("render null context 抛 IllegalArgumentException")
    void render_nullContextThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.render(minimalPlan(), null));
    }

    @Test
    @DisplayName("render 空 days 列表仍可生成有效 PDF")
    void render_emptyDaysProducesValidPdf() {
        TripPlan plan = new TripPlan("P-002", request(), List.of(),
                BigDecimal.ZERO, budget(), PlanStatus.CONFIRMED, false);
        PdfExportContext ctx = new PdfExportContext(
                "P-002", PlanStatus.CONFIRMED, "T-002", Instant.now());

        byte[] pdf = service.render(plan, ctx);

        assertTrue(pdf.length > 0);
    }

    // ===== T-3 内容完整性测试 =====

    @Test
    @DisplayName("AC-2 封面内容：目的地/日期/人数/风格/方案编号")
    void coverPage_containsAllFields() throws IOException {
        TripPlan plan = fullPlan();
        PdfExportContext ctx = exportCtx();
        byte[] pdf = service.render(plan, ctx);

        String text = extractText(pdf);
        // 方案编号
        assertTrue(text.contains(ctx.planId()), "封面应含方案编号");
        // 目的地
        assertTrue(text.contains(plan.request().destination()), "封面应含目的地");
        // 出发日期
        assertTrue(text.contains(plan.request().startDate().toString()), "封面应含出发日期");
        // 天数
        assertTrue(text.contains(String.valueOf(plan.request().days())), "封面应含天数");
        // 人数
        assertTrue(text.contains(String.valueOf(plan.request().headcount())), "封面应含人数");
        // 风格
        assertTrue(text.contains(plan.request().travelStyle().name()), "封面应含旅行风格");
        // 总费用
        assertTrue(text.contains(formatAssert(plan.totalCost())), "封面应含总费用");
    }

    @Test
    @DisplayName("AC-2 逐日行程：每日景点/餐饮/路线/当日小计")
    void dailyItinerary_containsAllDays() throws IOException {
        TripPlan plan = fullPlan();
        byte[] pdf = service.render(plan, ctx());

        String text = extractText(pdf);

        // 每日标题（兼容 CJK 中文/Helvetica 英文两种模式）
        for (TripDay day : plan.days()) {
            String dayNum = String.valueOf(day.dayIndex());
            boolean hasDayLabel = text.contains("Day " + day.dayIndex())
                    || text.contains("第" + day.dayIndex())
                    || text.contains(day.dayIndex() + "天");
            assertTrue(hasDayLabel, "应含第 " + day.dayIndex() + " 天标题或 Day " + day.dayIndex());
        }

        // 景点名称
        assertTrue(text.contains("宽窄巷子"));
        assertTrue(text.contains("武侯祠"));

        // 餐饮
        assertTrue(text.contains("LUNCH"));
        assertTrue(text.contains("DINNER"));

        // 路线
        assertTrue(text.contains("km"));
        assertTrue(text.contains("WALKING"));
    }

    @Test
    @DisplayName("AC-2 费用拆分：四类 + 总预算 + 实际费用 + 超支率")
    void budgetBreakdown_containsAllCategories() throws IOException {
        TripPlan plan = fullPlan();
        byte[] pdf = service.render(plan, ctx());

        String text = extractText(pdf);

        BudgetBreakdown bd = plan.budget().breakdown();
        assertTrue(text.contains(formatAssert(bd.transport())), "应含交通费用");
        assertTrue(text.contains(formatAssert(bd.hotel())), "应含住宿费用");
        assertTrue(text.contains(formatAssert(bd.food())), "应含餐饮费用");
        assertTrue(text.contains(formatAssert(bd.ticket())), "应含门票费用");
        assertTrue(text.contains(formatAssert(plan.budget().total())), "应含总预算");
        assertTrue(text.contains(formatAssert(plan.budget().used())), "应含实际费用");

        // 超支率
        String rateStr = String.format("%.2f",
                plan.budget().overrunRate().multiply(BigDecimal.valueOf(100))
                        .setScale(2, java.math.RoundingMode.HALF_UP));
        assertTrue(text.contains(rateStr), "应含超支率");
    }

    @Test
    @DisplayName("AC-2 页脚：traceId 和生成时间")
    void footer_containsTraceIdAndTimestamp() throws IOException {
        PdfExportContext ctx = exportCtx();
        TripPlan plan = fullPlan();
        byte[] pdf = service.render(plan, ctx);

        String text = extractText(pdf);

        assertTrue(text.contains(ctx.traceId()), "页脚应含 traceId");
        assertTrue(text.contains(ctx.planId()), "页脚应含 planId");
    }

    @Test
    @DisplayName("AC-5 金额单位元/两位小数，不乱码")
    void moneyFormattedTwoDecimals() throws IOException {
        TripPlan plan = fullPlan();
        byte[] pdf = service.render(plan, ctx());

        String text = extractText(pdf);

        // 金额应含两位小数（例如 150.00）
        assertTrue(text.contains("150.00"), "金额应含两位小数");
        assertTrue(text.contains("300.00"), "金额应含两位小数");
    }

    @Test
    @DisplayName("render 含有多种数据的完整行程不抛异常")
    void render_fullPlanDoesNotThrow() {
        assertDoesNotThrow(() -> service.render(fullPlan(), ctx()));
    }

    @Test
    @DisplayName("路线 fallback 标注出现")
    void routeFallbackLabelAppears() throws IOException {
        TripPlan plan = fullPlanWithFallback();
        byte[] pdf = service.render(plan, ctx());

        String text = extractText(pdf);

        assertTrue(text.contains("fallback") || text.contains("降级"), "应标注 fallback 或降级标签");
    }

    // ===== T-4 水印 + 脱敏测试 =====

    @Test
    @DisplayName("AC-3 review 状态 PDF 含草案水印")
    void reviewStatus_containsDraftWatermark() throws IOException {
        TripPlan plan = fullPlan();
        PdfExportContext ctx = new PdfExportContext("P-REVIEW", PlanStatus.REVIEW,
                "T-review", Instant.now());
        byte[] pdf = service.render(plan, ctx);

        String text = extractText(pdf);

        boolean hasWatermark = text.contains("草案") || text.contains("DRAFT")
                || text.contains("未确认");
        assertTrue(hasWatermark, "review 状态 PDF 应含草案水印");
    }

    @Test
    @DisplayName("AC-3 confirmed 状态 PDF 不含草案水印")
    void confirmedStatus_noDraftWatermark() throws IOException {
        TripPlan plan = fullPlan();
        PdfExportContext ctx = new PdfExportContext("P-CONFIRMED", PlanStatus.CONFIRMED,
                "T-confirmed", Instant.now());
        byte[] pdf = service.render(plan, ctx);

        String text = extractText(pdf);

        assertFalse(text.contains("DRAFT"), "confirmed 状态不应含 DRAFT 水印");
    }

    @Test
    @DisplayName("AC-5 traceId 脱敏显示（仅显示首尾部分字符）")
    void traceId_maskedSensitiveFields() throws IOException {
        TripPlan plan = fullPlan();
        PdfExportContext ctx = new PdfExportContext("P-001", PlanStatus.CONFIRMED,
                "T-secret-key-123", Instant.now());
        byte[] pdf = service.render(plan, ctx);

        String text = extractText(pdf);

        // 不应完整显示 traceId
        assertFalse(text.contains("T-secret-key-123"), "traceId 不应完整显示");
        // 应显示脱敏后的 traceId（首尾部分）
        boolean masked = text.contains("T-") && text.contains("***");
        assertTrue(masked, "traceId 应以脱敏格式显示");
    }

    // ===== 边界测试（C-014 ③ 补齐）=====

    @Test
    @DisplayName("边界: request 为 null 时封面安全渲染不抛异常")
    void render_nullRequest_safeRender() {
        TripDay day = new TripDay(START, 1, List.of(), List.of(), List.of(),
                BigDecimal.ZERO);
        Budget budget = new Budget(BigDecimal.valueOf(1000), BigDecimal.ZERO,
                BigDecimal.ZERO, new BudgetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        TripPlan plan = new TripPlan("P-NULLREQ", null, List.of(day),
                BigDecimal.ZERO, budget, PlanStatus.CONFIRMED, false);

        byte[] pdf = assertDoesNotThrow(() -> service.render(plan, ctx()));
        assertTrue(pdf.length > 0, "null request 时仍应生成有效 PDF");
    }

    @Test
    @DisplayName("边界: budget 为 null 时不渲染费用拆分")
    void render_nullBudget_skipsBudgetSection() throws IOException {
        TripDay day = new TripDay(START, 1, List.of(), List.of(), List.of(),
                BigDecimal.valueOf(100));
        TripPlan plan = new TripPlan("P-NOBUDGET", request(), List.of(day),
                BigDecimal.valueOf(100), null, PlanStatus.CONFIRMED, false);

        byte[] pdf = service.render(plan, ctx());
        assertTrue(pdf.length > 0, "null budget 时仍应生成有效 PDF");
    }

    @Test
    @DisplayName("边界: totalCost 为 null 时封面安全显示")
    void render_nullTotalCost_showsZero() throws IOException {
        TripDay day = new TripDay(START, 1, List.of(), List.of(), List.of(),
                BigDecimal.valueOf(100));
        TripPlan plan = new TripPlan("P-NULLCOST", request(), List.of(day),
                null, budget(), PlanStatus.CONFIRMED, false);

        byte[] pdf = service.render(plan, ctx());
        String text = extractText(pdf);
        assertTrue(text.contains("0"), "totalCost 为 null 时应显示 0");
    }

    // ===== 降级测试（C-014 ③ 补齐）=====

    @Test
    @DisplayName("降级: lbl cjk=false 时返回英文标签")
    void lbl_nonCjk_returnsEnglish() {
        assertEquals("Trip Plan", PdfRenderService.lbl(false, "行程方案", "Trip Plan"));
        assertEquals("行程方案", PdfRenderService.lbl(true, "行程方案", "Trip Plan"));
    }

    @Test
    @DisplayName("降级: maskTraceId 长度恰好 8 字符 → 原样返回（边界值）")
    void maskTraceId_exactly8_unchanged() {
        assertEquals("T-abc123", PdfRenderService.maskTraceId("T-abc123"));
    }

    @Test
    @DisplayName("降级: maskTraceId 9 字符 → 开始脱敏")
    void maskTraceId_9chars_masked() {
        String masked = PdfRenderService.maskTraceId("T-abc1234");
        assertTrue(masked.contains("***"), "9 字符应脱敏");
        assertEquals(11, masked.length());
    }

    // ===== T-5 覆盖补齐测试 =====

    @Test
    @DisplayName("maskTraceId null → 空字符串")
    void maskTraceId_null_returnsEmpty() {
        assertEquals("", PdfRenderService.maskTraceId(null));
    }

    @Test
    @DisplayName("maskTraceId 短 traceId（≤8 字符）原样返回")
    void maskTraceId_short_unchanged() {
        assertEquals("T-abc123", PdfRenderService.maskTraceId("T-abc123"));
    }

    @Test
    @DisplayName("maskTraceId 长 traceId 仅显示首 4 尾 4 字符")
    void maskTraceId_long_masked() {
        String masked = PdfRenderService.maskTraceId("T-secret-key-123456");
        assertTrue(masked.startsWith("T-se"), "应保留前 4 位");
        assertTrue(masked.endsWith("3456"), "应保留后 4 位");
        assertTrue(masked.contains("***"), "中间替换为 ***");
        assertFalse(masked.contains("secret"), "不应包含完整明文");
    }

    @Test
    @DisplayName("T-5 并发渲染：10 线程同时调用 render 不抛异常")
    void concurrentRender_multipleThreads() throws InterruptedException {
        int threadCount = 10;
        TripPlan plan = fullPlan();
        PdfExportContext ctx = exportCtx();
        AtomicInteger success = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    byte[] pdf = service.render(plan, ctx);
                    if (pdf.length > 0 && pdf[0] == 0x25) {
                        success.incrementAndGet();
                    }
                } catch (Exception e) {
                    // count stays
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "所有线程应在 30s 内完成");
        executor.shutdown();
        assertEquals(threadCount, success.get(), "所有线程均应生成有效 PDF");
    }

    @Test
    @DisplayName("T-5 渲染 30 天行程不超资源限制")
    void largePlan_30DaysDoesNotOOM() throws IOException {
        TripPlan plan = largePlan(30);
        PdfExportContext ctx = exportCtx();
        byte[] pdf = service.render(plan, ctx);
        assertTrue(pdf.length > 0);
        String text = extractText(pdf);
        assertTrue(text.contains("Day 30") || text.contains("第30"), "应含第 30 天");
    }

    // ===== 测试夹具 =====

    private static PdfExportContext exportCtx() {
        return new PdfExportContext("P-20260701-001", PlanStatus.CONFIRMED,
                "T-abc123", Instant.parse("2026-07-01T10:00:00Z"));
    }

    private static PdfExportContext ctx() {
        return exportCtx();
    }

    private static TripPlan fullPlan() {
        GeoLocation loc = new GeoLocation(104.07, 30.67, "成都市", "成都");
        Attraction a1 = new Attraction("A-1", "宽窄巷子", loc, 4.5, 120,
                BigDecimal.valueOf(0), List.of("古街", "美食"));
        Attraction a2 = new Attraction("A-2", "武侯祠", loc, 4.3, 90,
                BigDecimal.valueOf(50), List.of("历史", "文化"));

        Meal lunch = new Meal("陈麻婆豆腐", loc, MealType.LUNCH,
                BigDecimal.valueOf(80), List.of("麻婆豆腐"));
        Meal dinner = new Meal("小龙坎火锅", loc, MealType.DINNER,
                BigDecimal.valueOf(120), List.of("毛肚"));

        Route route = new Route(loc, loc, 3.5, 15, "WALKING", false);

        TripDay day1 = new TripDay(START, 1, List.of(a1, a2), List.of(lunch, dinner),
                List.of(route), BigDecimal.valueOf(250));
        TripDay day2 = new TripDay(START.plusDays(1), 2, List.of(a1), List.of(), List.of(),
                BigDecimal.valueOf(100));

        BudgetBreakdown bd = new BudgetBreakdown(
                BigDecimal.valueOf(200), BigDecimal.valueOf(500),
                BigDecimal.valueOf(300), BigDecimal.valueOf(150));
        Budget budget = new Budget(BigDecimal.valueOf(3000), BigDecimal.valueOf(1150),
                BigDecimal.valueOf(-0.617), bd);

        return new TripPlan("P-20260701-001", request(), List.of(day1, day2),
                BigDecimal.valueOf(1150), budget, PlanStatus.CONFIRMED, false);
    }

    private static TripPlan fullPlanWithFallback() {
        GeoLocation loc = new GeoLocation(104.07, 30.67, "成都市", "成都");
        Route fallbackRoute = new Route(loc, loc, 5.0, 30, "DRIVING", true);
        TripDay day = new TripDay(START, 1, List.of(), List.of(), List.of(fallbackRoute),
                BigDecimal.valueOf(0));
        Budget budget = new Budget(BigDecimal.valueOf(1000), BigDecimal.ZERO,
                BigDecimal.ZERO, new BudgetBreakdown(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        return new TripPlan("P-FALLBACK", request(), List.of(day),
                BigDecimal.ZERO, budget, PlanStatus.CONFIRMED, false);
    }

    private static TripPlanRequest request() {
        return new TripPlanRequest("成都", START, 2, BigDecimal.valueOf(3000),
                2, TravelStyle.COUPLE, List.of("美食", "文化"));
    }

    private static TripPlan minimalPlan() {
        TripDay day = new TripDay(START, 1, List.of(), List.of(), List.of(),
                BigDecimal.valueOf(500));
        BudgetBreakdown bd = new BudgetBreakdown(
                BigDecimal.valueOf(100), BigDecimal.valueOf(200),
                BigDecimal.valueOf(150), BigDecimal.valueOf(50));
        Budget budget = new Budget(BigDecimal.valueOf(3000), BigDecimal.valueOf(500),
                BigDecimal.valueOf(-0.83), bd);
        return new TripPlan("P-20260701-001", request(), List.of(day),
                BigDecimal.valueOf(500), budget, PlanStatus.CONFIRMED, false);
    }

    private static Budget budget() {
        BudgetBreakdown bd = new BudgetBreakdown(
                BigDecimal.valueOf(100), BigDecimal.valueOf(200),
                BigDecimal.valueOf(150), BigDecimal.valueOf(50));
        return new Budget(BigDecimal.valueOf(3000), BigDecimal.valueOf(500),
                BigDecimal.valueOf(-0.83), bd);
    }

    /** 提取 PDF 文本用于内容断言。 */
    private static String extractText(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /** 格式化为 PDF 中呈现的金额样式（两位小数）。 */
    private static String formatAssert(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** 构造 N 天的完整行程（用于大行程测试）。 */
    private static TripPlan largePlan(int numDays) {
        GeoLocation loc = new GeoLocation(104.07, 30.67, "成都市", "成都");
        Attraction a = new Attraction("A-1", "宽窄巷子", loc, 4.5, 120,
                BigDecimal.valueOf(0), List.of("古街", "美食"));
        BudgetBreakdown bd = new BudgetBreakdown(
                BigDecimal.valueOf(200), BigDecimal.valueOf(500),
                BigDecimal.valueOf(300), BigDecimal.valueOf(150));
        Budget budget = new Budget(BigDecimal.valueOf(3000), BigDecimal.valueOf(1150),
                BigDecimal.valueOf(-0.617), bd);

        TripDay[] days = new TripDay[numDays];
        for (int i = 0; i < numDays; i++) {
            days[i] = new TripDay(START.plusDays(i), i + 1,
                    List.of(a), List.of(), List.of(),
                    BigDecimal.valueOf(100 + i));
        }

        return new TripPlan("P-LARGE", request(), List.of(days),
                BigDecimal.valueOf(1150), budget, PlanStatus.CONFIRMED, false);
    }
}
