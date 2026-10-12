/**
 * PDF 导出能力（C-014）。
 *
 * <p>纯渲染层：消费 {@code TripPlan}/{@code TripDay}/{@code Budget} 等已有领域模型，
 * 不引入任何业务规则。临时文件严格限定沙箱目录，请求级生命周期管理。</p>
 *
 * @see com.smart.trip.skills.pdf.PdfRenderService
 * @see com.smart.trip.skills.pdf.SandboxFileManager
 */
package com.smart.trip.skills.pdf;
