package com.smart.trip.server.plan.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.skills.pdf.PdfExportContext;
import com.smart.trip.skills.pdf.PdfRenderService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PdfExportFacade} 单测（C-014 T-2：状态门禁 + 所有权校验）。
 */
@ExtendWith(MockitoExtension.class)
class PdfExportFacadeTest {

    private static final String PLAN_ID = "P-20260701-001";
    private static final long USER_ID = 10L;
    private static final byte[] FAKE_PDF = {0x25, 0x50, 0x44, 0x46};

    @Mock PlanCacheService planCacheService;
    @Mock PdfRenderService pdfRenderService;

    PdfExportFacade facade;

    @BeforeEach
    void setUp() {
        facade = new PdfExportFacade(planCacheService, pdfRenderService);
    }

    @Test
    @DisplayName("planId 不存在 → NOT_FOUND")
    void exportPdf_planNotFound_throwsNotFound() {
        when(planCacheService.getStatus(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.exportPdf(PLAN_ID, USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(pdfRenderService, never()).render(any(), any());
    }

    @ParameterizedTest(name = "status={0} → PLAN_NOT_EXPORTABLE")
    @ValueSource(strings = {"analyzing", "planning", "assembling", "budgeting", "failed"})
    @DisplayName("不可导出状态 → PLAN_NOT_EXPORTABLE")
    void exportPdf_nonExportableStatus_throws(String status) {
        when(planCacheService.getStatus(PLAN_ID)).thenReturn(Optional.of(status));
        when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of(String.valueOf(USER_ID)));

        assertThatThrownBy(() -> facade.exportPdf(PLAN_ID, USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLAN_NOT_EXPORTABLE);

        verify(pdfRenderService, never()).render(any(), any());
    }

    @Test
    @DisplayName("他人 plan → FORBIDDEN")
    void exportPdf_wrongOwner_throwsForbidden() {
        when(planCacheService.getStatus(PLAN_ID)).thenReturn(Optional.of("confirmed"));
        when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of("999"));

        assertThatThrownBy(() -> facade.exportPdf(PLAN_ID, USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("confirmed plan → 返回 PDF 字节")
    void exportPdf_confirmed_returnsPdf() {
        stubExportable("confirmed");
        when(pdfRenderService.render(any(), any())).thenReturn(FAKE_PDF);

        byte[] result = facade.exportPdf(PLAN_ID, USER_ID);

        assertThat(result).isEqualTo(FAKE_PDF);
        verify(pdfRenderService).render(any(), any(PdfExportContext.class));
    }

    @Test
    @DisplayName("review plan → 返回 PDF 字节（水印由 T-4 处理）")
    void exportPdf_review_returnsPdf() {
        stubExportable("review");
        when(pdfRenderService.render(any(), any())).thenReturn(FAKE_PDF);

        byte[] result = facade.exportPdf(PLAN_ID, USER_ID);

        assertThat(result).isEqualTo(FAKE_PDF);
    }

    @Test
    @DisplayName("缓存无 plan 数据但状态可导出 → NOT_FOUND")
    void exportPdf_noPlanData_throwsNotFound() {
        when(planCacheService.getStatus(PLAN_ID)).thenReturn(Optional.of("confirmed"));
        when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of(String.valueOf(USER_ID)));
        when(planCacheService.getPlan(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.exportPdf(PLAN_ID, USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("T-5 PDF 大小上限常量 = 10MB")
    void maxPdfSizeConstant_10MB() {
        assertThat(PdfExportFacade.MAX_PDF_SIZE).isEqualTo(10L * 1024 * 1024);
    }

    @Test
    @DisplayName("T-5 生成文件超过 10MB 不抛出异常（仅记录日志）")
    void exportPdf_largeFile_doesNotThrow() {
        stubExportable("confirmed");
        byte[] largePdf = new byte[11 * 1024 * 1024]; // > 10MB
        when(pdfRenderService.render(any(), any())).thenReturn(largePdf);

        byte[] result = facade.exportPdf(PLAN_ID, USER_ID);

        assertThat(result).isSameAs(largePdf);
    }

    // ===== 边界测试 =====

    @Test
    @DisplayName("边界: plan JSON 中 plan 节点缺失 → 渲染空行程不抛异常")
    void exportPdf_missingPlanNode_rendersEmptyDays() {
        when(planCacheService.getStatus(PLAN_ID)).thenReturn(Optional.of("confirmed"));
        when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of(String.valueOf(USER_ID)));
        when(planCacheService.getPlan(PLAN_ID)).thenReturn(Optional.of("""
                {"request":{"destination":"成都","days":1,"budget":3000,"headcount":2}}
                """));
        when(pdfRenderService.render(any(), any())).thenReturn(FAKE_PDF);

        byte[] result = facade.exportPdf(PLAN_ID, USER_ID);
        assertThat(result).isEqualTo(FAKE_PDF);
    }

    @Test
    @DisplayName("边界: userId 字符串不存在（ownerId 空）→ FORBIDDEN")
    void exportPdf_noOwnerId_throwsForbidden() {
        when(planCacheService.getStatus(PLAN_ID)).thenReturn(Optional.of("confirmed"));
        when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.exportPdf(PLAN_ID, USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ===== 降级测试 =====

    @Test
    @DisplayName("降级: 渲染异常 → 传播 UncheckedIOException（不吞异常）")
    void exportPdf_renderException_propagates() {
        stubExportable("confirmed");
        when(pdfRenderService.render(any(), any()))
                .thenThrow(new UncheckedIOException("渲染失败",
                        new IOException("font error")));

        assertThatThrownBy(() -> facade.exportPdf(PLAN_ID, USER_ID))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("降级: plan JSON 格式损坏 → NOT_FOUND")
    void exportPdf_malformedJson_throwsNotFound() {
        when(planCacheService.getStatus(PLAN_ID)).thenReturn(Optional.of("confirmed"));
        when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of(String.valueOf(USER_ID)));
        when(planCacheService.getPlan(PLAN_ID)).thenReturn(Optional.of("{bad-json!!!!}"));

        assertThatThrownBy(() -> facade.exportPdf(PLAN_ID, USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ===== 回归测试 =====

    @Test
    @DisplayName("回归: confirmed 导出调用 render 时传入正确 PlanStatus")
    void exportPdf_confirmed_passesCorrectStatus() {
        stubExportable("confirmed");
        when(pdfRenderService.render(any(), any())).thenReturn(FAKE_PDF);

        facade.exportPdf(PLAN_ID, USER_ID);

        verify(pdfRenderService).render(any(), any(PdfExportContext.class));
    }

    private void stubExportable(String status) {
        when(planCacheService.getStatus(PLAN_ID)).thenReturn(Optional.of(status));
        when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of(String.valueOf(USER_ID)));
        when(planCacheService.getPlan(PLAN_ID)).thenReturn(Optional.of(samplePlanJson()));
    }

    private static String samplePlanJson() {
        return """
                {
                  "planId": "P-20260701-001",
                  "status": "confirmed",
                  "request": {
                    "destination": "成都",
                    "startDate": "2026-07-01",
                    "days": 1,
                    "budget": 3000,
                    "headcount": 2,
                    "travelStyle": "COUPLE",
                    "preferences": []
                  },
                  "plan": {
                    "days": [],
                    "totalCost": 500,
                    "budget": {
                      "total": 3000, "used": 500, "overrunRate": -0.83,
                      "breakdown": {"transport": 100, "hotel": 200, "food": 150, "ticket": 50}
                    }
                  }
                }
                """;
    }
}
