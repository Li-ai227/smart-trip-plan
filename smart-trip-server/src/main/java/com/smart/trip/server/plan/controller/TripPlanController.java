package com.smart.trip.server.plan.controller;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.server.auth.model.AuthPrincipal;
import com.smart.trip.server.plan.facade.PdfExportFacade;
import com.smart.trip.server.plan.facade.TripPlanFacade;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



/**
 * 规划 API 端点（C-011 AC-6/AC-7/AC-8）。
 *
 * <p>薄 Controller：仅做 HTTP 语义映射，全部业务编排委托给 {@link TripPlanFacade}。
 * 不直接依赖 Service、Repository 或 Agent（ArchUnit R3/R6 守护）。</p>
 */
@RestController
@RequestMapping("/api/v1/trip-plan")
public class TripPlanController {

    private final TripPlanFacade tripPlanFacade;
    private final PdfExportFacade pdfExportFacade;

    /**
     * 构造规划控制器，注入 Facade。
     *
     * @param tripPlanFacade  规划门面
     * @param pdfExportFacade PDF 导出门面（C-014）
     */
    public TripPlanController(TripPlanFacade tripPlanFacade, PdfExportFacade pdfExportFacade) {
        this.tripPlanFacade = tripPlanFacade;
        this.pdfExportFacade = pdfExportFacade;
    }

    /**
     * 提交规划请求（AC-6/Case-6）。
     *
     * @param request 规划请求体
     * @return 202 + planId + status=analyzing
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submitPlan(@RequestBody TripPlanRequest request) {
        long userId = currentUserId();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(tripPlanFacade.submitPlan(request, userId));
    }

    /**
     * 查询用户历史方案列表（C-017 DECISION-2：扩展筛选参数，不新增 Controller）。
     *
     * @param page        页码（1-based，默认 1）
     * @param pageSize    每页条数（默认 10，最大 100）
     * @param keyword     目的地关键词（可选）
     * @param travelStyle 旅行风格（FAMILY/COUPLE/SOLO/TEAM，可选）
     * @param planStatus  终态（confirmed/failed，可选）
     * @param startDate   起始日期下限（含，格式 yyyy-MM-dd，可选）
     * @param endDate     起始日期上限（含，格式 yyyy-MM-dd，可选）
     * @return 200 + 分页历史列表
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getPlanHistory(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "travelStyle", required = false) TravelStyle travelStyle,
            @RequestParam(name = "planStatus", required = false) String planStatus,
            @RequestParam(name = "startDate", required = false) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) LocalDate endDate) {
        long userId = currentUserId();
        return ResponseEntity.ok(tripPlanFacade.getUserPlanHistory(
                userId, page, pageSize, keyword, travelStyle, planStatus, startDate, endDate));
    }

    /**
     * 查询规划进度/结果（AC-6/Case-8）。
     *
     * @param planId 方案 ID
     * @return 200 + 规划详情
     */
    @GetMapping("/{planId}")
    public ResponseEntity<Map<String, Object>> queryPlan(@PathVariable("planId") String planId) {
        long userId = currentUserId();
        return ResponseEntity.ok(tripPlanFacade.queryPlan(planId, userId));
    }

    /**
     * 删除历史方案（DELETE /api/v1/trip-plan/{planId}）。
     *
     * @param planId 方案 ID
     * @return 200 + 删除结果
     */
    @DeleteMapping("/{planId}")
    public ResponseEntity<Map<String, Object>> deletePlan(@PathVariable("planId") String planId) {
        long userId = currentUserId();
        return ResponseEntity.ok(tripPlanFacade.deletePlan(planId, userId));
    }


    /**
     * 清除当前登录用户的全部记忆（C-015 AC-10）。
     *
     * <p>调用 ReMe API 删除该用户的记忆 workspace，
     * 后续规划不再召回该用户的历史偏好与行程。</p>
     *
     * @return 200 + 清除结果
     */
    @DeleteMapping("/memory")
    public ResponseEntity<Map<String, Object>> clearMemory() {
        long userId = currentUserId();
        return ResponseEntity.ok(tripPlanFacade.clearMemory(userId));
    }

    /**
     * HITL 人工介入（AC-9/Case-9）。
     *
     * @param planId  方案 ID
     * @param request 介入请求（action/dayIndex/changes）
     * @return 200 + 更新后的规划详情
     */
    @PostMapping("/{planId}/intervene")
    public ResponseEntity<Map<String, Object>> intervene(
            @PathVariable("planId") String planId,
            @RequestBody InterveneRequest request) {
        long userId = currentUserId();
        return ResponseEntity.ok(
                tripPlanFacade.intervene(planId, request.action(), request.dayIndex(),
                        request.changes(), userId));
    }

    /**
     * 提交用户反馈（AC-6）。
     *
     * @param planId   方案 ID
     * @param feedback 反馈内容
     * @return 200 + 确认消息
     */
    @PostMapping("/{planId}/feedback")
    public ResponseEntity<Map<String, Object>> feedback(
            @PathVariable("planId") String planId,
            @RequestBody Map<String, Object> feedback) {
        long userId = currentUserId();
        return ResponseEntity.ok(tripPlanFacade.submitFeedback(planId, feedback, userId));
    }

    /**
     * 导出行程方案 PDF（C-014 AC-1/AC-7/Case-1/Case-6）。
     *
     * @param planId 方案 ID
     * @param format 导出格式（目前仅支持 pdf）
     * @return 200 + PDF 文件流
     */
    @GetMapping("/{planId}/export")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable("planId") String planId,
            @RequestParam(name = "format", defaultValue = "pdf") String format) {
        if (!"pdf".equalsIgnoreCase(format)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "不支持的导出格式: " + format);
        }
        long userId = currentUserId();
        byte[] pdf = pdfExportFacade.exportPdf(planId, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(planId + ".pdf")
                .build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /**
     * HITL 介入请求体（接口协议.md §1.3）。
     *
     * @param action   介入动作（CONFIRM/MODIFY/REJECT）
     * @param dayIndex MODIFY 时目标天索引，可为 null
     * @param changes  MODIFY 时的变更内容（removeAttraction/addAttraction），可为 null
     */
    public record InterveneRequest(String action, Integer dayIndex, Map<String, Object> changes) {}

    private static long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        return p.userId();
    }
}
