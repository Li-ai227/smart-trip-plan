package com.smart.trip.skills.pdf;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.BudgetBreakdown;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * PDF 渲染服务 — 纯文本流式布局，安全 CJK 字符，无重叠（C-014）。
 *
 * <p>所有 emoji 替换为 CJK 安全文本。无卡片边框，流式文字 + 虚线分隔，
 * 避免字体重叠和问号乱码。时间轴 layout：左侧红色时间 + 右侧内容。</p>
 */
public final class PdfRenderService {

    private static final Logger LOG = Logger.getLogger(PdfRenderService.class.getName());

    private static final float PW = PDRectangle.A4.getWidth();
    private static final float PH = PDRectangle.A4.getHeight();
    private static final float M = 50f;
    private static final float CW = PW - 2 * M;

    private static final float R = 1.0f, G = 0.141f, B = 0.259f;
    private static final float DR = 0.169f, DG = 0.169f, DB = 0.169f;
    private static final float GR = 0.533f, GG = 0.533f, GB = 0.533f;
    private static final float WH = 1f;
    private static final float LG = 0.9f;
    private static final float SPOT_C = 0.086f;
    private static final float MEAL_C = 0.541f;

    private static final float TS = 24f, SS = 11f;
    private static final float BS = 10f, SML = 8f;
    private static final float TMS = 13f;

    private static final List<String> CJK = List.of(
            "C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simsun.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/System/Library/Fonts/PingFang.ttc");

    /**
     * 将行程方案渲染为 PDF 字节数组。
     *
     * @param plan 行程方案
     * @param ctx  导出上下文
     * @return PDF 字节
     */
    public byte[] render(TripPlan plan, PdfExportContext ctx) {
        if (plan == null) throw new IllegalArgumentException("TripPlan 不可为 null");
        if (ctx == null) throw new IllegalArgumentException("PdfExportContext 不可为 null");
        Closeable fr = null;
        try (PDDocument doc = new PDDocument()) {
            FontResult ft = loadFont(doc);
            fr = ft.resource;
            RenderState s = new RenderState(doc, ft.font, ctx, ft.cjk);
            renderCover(s, plan, ctx);
            if (!plan.days().isEmpty()) s.newPage();
            for (TripDay d : plan.days()) renderDay(s, d);
            renderBudget(s, plan);
            s.close();
            return toBytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("PDF 渲染失败: " + ctx.planId(), e);
        } finally {
            closeQuietly(fr);
        }
    }

    // ===== Cover =====

    private static void renderCover(RenderState s, TripPlan plan, PdfExportContext ctx) throws IOException {
        s.newPage();
        TripPlanRequest req = plan.request();
        float hh = 110f;
        s.drawFill(0, PH - M - hh, PW, hh, R, G, B);
        s.y = PH - M - 30f;
        s.drawCentered(lbl(s.cjk, "行程方案", "Trip Plan"), TS, WH, WH, WH);
        s.y = PH - M - 60f;
        String sub = safe(reqDestination(req)) + " | " + reqDaysVal(req) + lbl(s.cjk, "天", "d");
        if (!sub.isBlank()) s.drawCentered(sub, SS, WH, WH, WH);
        s.y = PH - M - hh - 16f;
        drawStatusBadge(s, ctx);
        s.drawFill(M, s.y - 150f, CW, 150f, 1f, 1f, 1f);
        s.drawStroke(M, s.y - 150f, CW, 150f, LG, LG, LG);
        s.y -= 14f;
        String[][] rows = {
            {lbl(s.cjk, "方案编号", "Plan ID"), plan.planId()},
            {lbl(s.cjk, "目的地", "Destination"), safe(reqDestination(req))},
            {lbl(s.cjk, "出行日期", "Date"), reqStartDate(req)},
            {lbl(s.cjk, "行程天数", "Days"), String.valueOf(reqDaysVal(req))},
            {lbl(s.cjk, "人数", "Travelers"), String.valueOf(reqHeadcountVal(req))},
            {lbl(s.cjk, "风格", "Style"), reqStyle(req)},
        };
        float c1 = M + 16f, c2 = M + CW / 2f + 12f, rh = BS + 4f;
        for (int i = 0; i < rows.length; i++) {
            int col = i % 2, ri = i / 2;
            float x = col == 0 ? c1 : c2, ry = s.y - ri * rh * 2;
            s.drawText(x, ry, rows[i][0], BS, GR, GG, GB);
            s.drawText(x + s.tw(rows[i][0], BS) + 4, ry, rows[i][1], BS, DR, DG, DB);
        }
        s.y = s.y - 2 * rh * 3 - 20f;
        s.drawLine(M, s.y, CW, LG, LG, LG);
        s.skip(14);
        float price = plan.totalCost() != null ? plan.totalCost().floatValue() : 0;
        s.drawCentered(moneyLabel(String.format("%,.2f", price), s.cjk), 28f, R, G, B);
        s.skip(36);
        if (plan.budget() != null && plan.budget().total() != null) {
            s.drawCentered(lbl(s.cjk, "预算", "Budget") + ": "
                    + moneyLabel(String.format("%,.2f", plan.budget().total().floatValue()), s.cjk), SML, GR, GG, GB);
        }
    }

    private static void drawStatusBadge(RenderState s, PdfExportContext ctx) throws IOException {
        boolean c = ctx.status() != null && "CONFIRMED".equalsIgnoreCase(ctx.status().name());
        float bw = 90f, bh = 22f, bx = PW - M - bw - 4f;
        if (c) s.drawFill(bx, s.y - bh, bw, bh, 0.91f, 0.98f, 0.945f);
        else s.drawFill(bx, s.y - bh, bw, bh, 1f, 0.914f, 0.937f);
        String bt = c ? lbl(s.cjk, "已确认", "Confirmed") : lbl(s.cjk, "草案", "Draft");
        float tw2 = s.tw(bt, SML);
        s.drawText(bx + (bw - tw2) / 2f, s.y - bh + 6, bt, SML, c ? SPOT_C : R, c ? MEAL_C : G, c ? 0.518f : B);
        s.y -= bh + 20f;
    }

    // ===== Day =====

    private static void renderDay(RenderState s, TripDay day) throws IOException {
        s.ensureSpace(80);
        String ds = day.date() != null ? day.date().toString() : "";
        String dh = "Day " + day.dayIndex() + " - " + ds + "  |  "
                + moneyLabel(String.format("%,.2f", day.dailyCost().floatValue()), s.cjk);
        float bh = 28f;
        s.drawFill(M, s.y - bh, CW, bh, R, G, B);
        s.drawText(M + 14, s.y - bh + 7, dh, BS + 1, WH, WH, WH);
        s.y -= bh + 12;
        renderSpots(s, day);
        renderMeals(s, day);
        renderRoutes(s, day);
    }

    private static void renderSpots(RenderState s, TripDay day) throws IOException {
        List<Attraction> list = day.attractions();
        if (list.isEmpty()) return;
        String[] times = {"09:00", "10:30", "14:00", "15:30", "16:30"};
        for (int i = 0; i < list.size(); i++) {
            Attraction a = list.get(i);
            String time = i < times.length ? times[i] : "--:--";
            s.ensureSpace(70);
            drawTime(s, time);
            float cx = M + 60f;
            // Tag on same line as time
            drawTag(s, cx, s.y, lbl(s.cjk, "[景点]", "[Spot]"), SPOT_C, 0.78f, 0.518f);
            // Name right after tag
            float tx = cx + 8 + s.tw(lbl(s.cjk, "[景点]", "[Spot]"), SML) + 12;
            s.drawText(tx, s.y - 3, safe(a.name()), BS + 1, DR, DG, DB);
            s.y -= BS + 10;
            // Detail tags
            String tags = star(a.rating()) + "  " + a.durationMin() + lbl(s.cjk, "分钟", "min")
                    + "  " + moneyLabel(formatMoney(a.ticketPrice()), s.cjk);
            s.drawText(cx + 8, s.y, tags, SML, GR, GG, GB);
            s.y -= SML + 6;
            // Route transit
            if (i < day.routes().size()) {
                Route r = day.routes().get(i);
                drawTransit(s, r, cx);
            }
            s.skip(6);
        }
    }

    private static void renderMeals(RenderState s, TripDay day) throws IOException {
        List<Meal> list = day.meals();
        if (list.isEmpty()) return;
        String[] mtimes = {"08:00", "12:00", "19:00"};
        for (int i = 0; i < list.size(); i++) {
            Meal m = list.get(i);
            String time = i < mtimes.length ? mtimes[i] : "--:--";
            s.ensureSpace(60);
            drawTime(s, time);
            float cx = M + 60f;
            drawTag(s, cx, s.y, lbl(s.cjk, "[美食]", "[Food]"), MEAL_C, 0.541f, 0f);
            String mt = m.mealType() != null ? "[" + m.mealType().name() + "] " : "";
            String mn = mt + safe(m.name());
            float tx2 = cx + 8 + s.tw(lbl(s.cjk, "[美食]", "[Food]"), SML) + 12;
            s.drawText(tx2, s.y - 3, mn, BS + 1, DR, DG, DB);
            s.y -= BS + 10;
            String info = moneyLabel(formatMoney(m.avgPrice()), s.cjk) + lbl(s.cjk, "/人", "/person");
            s.drawText(cx + 8, s.y, info, SML, GR, GG, GB);
            s.y -= SML + 8;
        }
    }

    private static void renderRoutes(RenderState s, TripDay day) throws IOException {
        List<Route> list = day.routes();
        List<Attraction> spots = day.attractions();
        if (list.isEmpty() || list.size() <= spots.size()) return;
        s.ensureSpace(40);
        for (int i = spots.size(); i < list.size(); i++) {
            drawTransit(s, list.get(i), M + 60f);
            s.skip(4);
        }
    }

    // ===== Entry helpers =====

    private static void drawTime(RenderState s, String time) throws IOException {
        s.drawText(M + 4, s.y - 4, time, TMS, R, G, B);
    }

    private static void drawTag(RenderState s, float x, float yy, String text,
                                 float tr, float tg, float tb) throws IOException {
        float tw2 = s.tw(text, SML);
        float pad = 4f;
        s.drawFill(x, yy - 14, tw2 + pad * 2, 14, tr, tg, tb);
        s.drawText(x + pad, yy - 12, text, SML, WH, WH, WH);
    }

    private static void drawTransit(RenderState s, Route r, float cx) throws IOException {
        if (r == null) return;
        s.ensureSpace(30);
        float ly = s.y;
        s.drawDash(cx + 8, ly, CW - 68, LG, LG, LG);
        s.y = ly - 4;
        String fb = r.fallback() ? "  " + lbl(s.cjk, "[降级估算]", "[fallback]") : "";
        String line = r.durationMin() + lbl(s.cjk, "分钟", "min") + " · " + r.distanceKm()
                + "km  " + safe(r.transportMode()) + fb;
        s.drawText(cx + 12, s.y, line, SML, GR, GG, GB);
        if (r.fallback()) {
            String fallbackTag = lbl(s.cjk, "[降级估算]", "[fallback]");
            float ftw = s.tw(fallbackTag, SML);
            s.drawFill(cx + 12 + s.tw(line, SML) - ftw - 4, s.y - 2, ftw + 8, 12, 1f, 0.965f, 0.914f);
        }
        s.y -= SML + 4;
    }

    private static String star(double rating) {
        int full = Math.min(Math.max((int) Math.round(rating / 1.0), 0), 5);
        return "*".repeat(full) + ".".repeat(5 - full) + " " + String.format("%.1f", rating);
    }

    // ===== Budget =====

    private static void renderBudget(RenderState s, TripPlan plan) throws IOException {
        if (plan.budget() == null) return;
        s.ensureSpace(180);
        float bh = 28f;
        s.drawFill(M, s.y - bh, CW, bh, R, G, B);
        s.drawText(M + 14, s.y - bh + 7, lbl(s.cjk, "费用拆分", "Budget Breakdown"), BS + 1, WH, WH, WH);
        s.y -= bh + 14;
        s.drawFill(M, s.y - 140f, CW, 140f, 1f, 1f, 1f);
        s.drawStroke(M, s.y - 140f, CW, 140f, LG, LG, LG);
        s.y -= 12;
        float c1 = M + 20f, c2 = PW - M - 20f;
        s.drawText(c1, s.y, lbl(s.cjk, "类别", "Category"), BS, GR, GG, GB);
        s.drawText(c2 - s.tw(lbl(s.cjk, "金额", "Amount"), BS), s.y, lbl(s.cjk, "金额", "Amount"), BS, GR, GG, GB);
        s.y -= BS + 4;
        s.drawLine(M + 20, s.y, CW - 40, LG, LG, LG);
        s.skip(8);
        BudgetBreakdown bd = plan.budget().breakdown();
        if (bd != null) {
            bRow(s, lbl(s.cjk, "  交通", "Transport"), bd.transport(), c1, c2);
            bRow(s, lbl(s.cjk, "  住宿", "Hotel"), bd.hotel(), c1, c2);
            bRow(s, lbl(s.cjk, "  餐饮", "Food"), bd.food(), c1, c2);
            bRow(s, lbl(s.cjk, "  门票", "Ticket"), bd.ticket(), c1, c2);
        }
        s.skip(6);
        s.drawLine(M + 20, s.y, CW - 40, DR, DG, DB);
        s.skip(16);
        bRow(s, lbl(s.cjk, "总预算", "Total Budget"), plan.budget().total(), c1, c2);
        bRow(s, lbl(s.cjk, "已用费用", "Used"), plan.budget().used(), c1, c2);
        s.drawText(c1, s.y, lbl(s.cjk, "超支率", "Overrun") + ": " + formatPercent(plan.budget().overrunRate()), BS, GR, GG, GB);
        s.y -= BS + 6;
        BigDecimal tot = plan.budget().total(), used = plan.budget().used();
        if (tot != null && tot.compareTo(BigDecimal.ZERO) > 0 && used != null) {
            drawPct(s, used.divide(tot, 4, RoundingMode.HALF_UP).floatValue());
        }
    }

    private static void bRow(RenderState s, String lbl, BigDecimal a, float cx1, float cx2) throws IOException {
        s.ensureSpace(BS + 4);
        s.drawText(cx1, s.y, lbl, BS, DR, DG, DB);
        String amt = moneyLabel(formatMoney(a), s.cjk);
        s.drawText(cx2 - s.tw(amt, BS), s.y, amt, BS, DR, DG, DB);
        s.y -= BS + 6;
    }

    private static void drawPct(RenderState s, float ratio) throws IOException {
        float barW = CW - 40, barH = 16f, bx = M + 20;
        float clamp = Math.min(Math.max(ratio, 0f), 1.2f);
        float fillW = barW * Math.min(clamp, 1f);
        float pr, pg, pb;
        if (ratio > 1f) { pr = R; pg = G; pb = B; }
        else if (ratio > 0.9f) { pr = 1f; pg = 0.8f; pb = 0f; }
        else { pr = 0.2f; pg = 0.7f; pb = 0.3f; }
        s.drawFill(bx, s.y - barH, barW, barH, LG, LG, LG);
        if (fillW > 0) s.drawFill(bx, s.y - barH, fillW, barH, pr, pg, pb);
        String pct = String.format("%.0f%%", ratio * 100);
        s.drawText(bx + (barW - s.tw(pct, SML)) / 2f, s.y - 13, pct, SML, WH, WH, WH);
        s.y -= barH + 6;
    }

    // ===== Font =====

    private static FontResult loadFont(PDDocument doc) {
        for (String path : CJK) {
            File f = new File(path);
            if (!f.exists()) continue;
            try {
                if (path.endsWith(".ttc")) return loadTtc(doc, f);
                try (InputStream is = Files.newInputStream(f.toPath())) {
                    return new FontResult(PDType0Font.load(doc, is), true, null);
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Font load failed: " + path, e);
            }
        }
        LOG.warning("No CJK font found, using Helvetica");
        return new FontResult(new PDType1Font(Standard14Fonts.FontName.HELVETICA), false, null);
    }

    private static FontResult loadTtc(PDDocument doc, File f) throws IOException {
        TrueTypeCollection ttc = new TrueTypeCollection(f);
        try {
            TrueTypeFont[] h = {null};
            ttc.processAllFonts(t -> { if (h[0] == null) h[0] = t; });
            if (h[0] == null) throw new IOException("No fonts in TTC: " + f);
            return new FontResult(PDType0Font.load(doc, h[0], true), true, ttc);
        } catch (IOException e) {
            ttc.close(); throw e;
        }
    }

    private record FontResult(PDFont font, boolean cjk, Closeable resource) { }

    // ===== Static helpers =====

    static String lbl(boolean cjk, String zh, String en) { return cjk ? zh : en; }
    /** 金额本地化：CJK → "xxx 元"，非 CJK → "$xxx"。 */
    private static String moneyLabel(String num, boolean cjk) {
        return cjk ? num + " 元" : "$" + num;
    }
    private static String formatMoney(BigDecimal a) {
        return a != null ? a.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
    }
    private static String formatPercent(BigDecimal r) {
        return r != null ? r.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%" : "0.00%";
    }
    private static String safe(String t) { return t != null ? t : ""; }
    private static String reqDestination(TripPlanRequest r) { return r != null ? safe(r.destination()) : "-"; }
    private static String reqStartDate(TripPlanRequest r) { return r != null && r.startDate() != null ? r.startDate().toString() : "-"; }
    private static String reqStyle(TripPlanRequest r) { return r != null && r.travelStyle() != null ? r.travelStyle().name() : "-"; }
    private static int reqDaysVal(TripPlanRequest r) { return r != null ? r.days() : 0; }
    private static int reqHeadcountVal(TripPlanRequest r) { return r != null ? r.headcount() : 0; }
    static String maskTraceId(String id) {
        if (id == null || id.length() <= 8) return id != null ? id : "";
        return id.substring(0, 4) + "***" + id.substring(id.length() - 4);
    }
    private static byte[] toBytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        return baos.toByteArray();
    }
    private static void closeQuietly(Closeable c) {
        if (c != null) try { c.close(); } catch (IOException ignored) { }
    }
}
