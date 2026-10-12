package com.smart.trip.skills.pdf;

import com.smart.trip.common.enums.PlanStatus;
import java.time.Instant;

/**
 * PDF 导出请求上下文（C-014 AC-2/AC-3/AC-5）。
 *
 * <p>携带渲染所需的非业务元数据：方案 ID（文件命名）、状态（水印判定）、
 * traceId（页脚）、生成时间（页脚）。</p>
 *
 * @param planId      方案 ID
 * @param status      方案状态（review → 草案水印；confirmed → 正式版）
 * @param traceId     链路追踪 ID（页脚展示）
 * @param generatedAt 生成时间（ISO-8601，页脚展示）
 */
public record PdfExportContext(
        String planId,
        PlanStatus status,
        String traceId,
        Instant generatedAt) {
}
