package com.smart.trip.skills.pdf;

import com.smart.trip.common.enums.PlanStatus;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.util.Matrix;

/**
 * PDF 渲染上下文：页面内容流 + 坐标 + 文本/形状原语（C-014）。
 *
 * <p>包私有，供 {@link PdfRenderService} 使用。所有坐标 y 为基线（左下角），自上而下递减。</p>
 */
final class RenderState {

    private static final float PW = PDRectangle.A4.getWidth();
    private static final float PH = PDRectangle.A4.getHeight();
    private static final float MARGIN = 50f;
    private static final float BODY = 10f;
    private static final float SMALL = 8f;
    private static final float LG = 0.78f;
    private static final float GRAY = 0.533f;

    private final PDDocument doc;
    private final PDFont font;
    private final PdfExportContext ctx;
    final boolean cjk;
    private PDPageContentStream cs;
    float y;

    RenderState(PDDocument doc, PDFont font, PdfExportContext ctx, boolean cjk) {
        this.doc = doc;
        this.font = font;
        this.ctx = ctx;
        this.cjk = cjk;
    }

    void newPage() throws IOException {
        if (cs != null) { renderFooter(); cs.close(); }
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true);
        cs.setFont(font, BODY);
        y = PH - MARGIN;
        if (ctx.status() == PlanStatus.REVIEW) renderWatermark();
    }

    // Shapes

    void drawFill(float x, float yb, float w, float h, float r, float g, float b) throws IOException {
        cs.setNonStrokingColor(r, g, b);
        cs.addRect(x, yb, w, h);
        cs.fill();
    }

    void drawStroke(float x, float yb, float w, float h, float r, float g, float b) throws IOException {
        cs.setStrokingColor(r, g, b);
        cs.setLineWidth(0.5f);
        cs.addRect(x, yb, w, h);
        cs.stroke();
    }

    void drawLine(float x, float yy, float w, float r, float g, float b) throws IOException {
        cs.setStrokingColor(r, g, b);
        cs.setLineWidth(0.5f);
        cs.moveTo(x, yy);
        cs.lineTo(x + w, yy);
        cs.stroke();
    }

    void drawDash(float x, float yy, float w, float r, float g, float b) throws IOException {
        cs.setStrokingColor(r, g, b);
        cs.setLineWidth(0.5f);
        cs.setLineDashPattern(new float[]{3f, 3f}, 0f);
        cs.moveTo(x, yy);
        cs.lineTo(x + w, yy);
        cs.stroke();
        cs.setLineDashPattern(new float[]{1f}, 0f);
    }

    // Text

    void drawText(float x, float yy, String text, float fs, float r, float g, float b) throws IOException {
        String sf = sanitize(text);
        if (sf.isEmpty()) return;
        cs.setNonStrokingColor(r, g, b);
        cs.setFont(font, fs);
        cs.beginText();
        cs.newLineAtOffset(x, yy);
        cs.showText(sf);
        cs.endText();
    }

    void drawCentered(String text, float fs, float r, float g, float b) throws IOException {
        String sf = sanitize(text);
        if (sf.isEmpty()) return;
        drawText((PW - tw(sf, fs)) / 2f, y, sf, fs, r, g, b);
    }

    float tw(String text, float fs) {
        try { return font.getStringWidth(sanitize(text)) / 1000f * fs; }
        catch (Exception e) { return fs * text.length() * 0.5f; }
    }

    void ensureSpace(float needed) throws IOException {
        if (cs == null || y - needed < MARGIN + 30f) newPage();
    }

    void skip(float pts) { y -= pts; }

    // Footer & watermark

    void close() throws IOException {
        if (cs != null) { renderFooter(); cs.close(); cs = null; }
    }

    private void renderFooter() throws IOException {
        String f = sanitize(PdfRenderService.maskTraceId(ctx.traceId())
                + " | " + ctx.generatedAt() + " | " + ctx.planId());
        cs.setFont(font, SMALL);
        cs.setNonStrokingColor(GRAY, GRAY, GRAY);
        cs.beginText();
        cs.newLineAtOffset(MARGIN, MARGIN - 15);
        cs.showText(f);
        cs.endText();
    }

    private void renderWatermark() throws IOException {
        cs.saveGraphicsState();
        cs.setNonStrokingColor(LG, LG, LG);
        String wm = "DRAFT - " + PdfRenderService.lbl(cjk, "未确认", "NOT CONFIRMED");
        float ws = 48f;
        cs.setFont(font, ws);
        float tw = font.getStringWidth(sanitize(wm)) / 1000f * ws;
        cs.beginText();
        Matrix m = Matrix.getTranslateInstance(PW / 2f, PH / 2f);
        m.rotate(Math.toRadians(-45));
        cs.setTextMatrix(m);
        cs.newLineAtOffset(-tw / 2f, 0);
        cs.showText(sanitize(wm));
        cs.endText();
        cs.restoreGraphicsState();
    }

    private String sanitize(String text) {
        if (text == null || text.isEmpty()) return "";
        try { font.encode(text); return text; }
        catch (Exception e) {
            StringBuilder sb = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                try { font.encode(ch); sb.append(ch); }
                catch (Exception ignored) { sb.append('?'); }
            }
            return sb.toString();
        }
    }
}
