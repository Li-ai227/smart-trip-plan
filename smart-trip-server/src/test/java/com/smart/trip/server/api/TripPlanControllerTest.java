package com.smart.trip.server.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.exception.RateLimitedException;
import com.smart.trip.server.admin.AdminController;
import com.smart.trip.server.admin.AgentStatusService;
import com.smart.trip.server.admin.AgentStatusView;
import com.smart.trip.server.auth.filter.JwtAuthenticationFilter;
import com.smart.trip.server.auth.model.UserRole;
import com.smart.trip.server.auth.service.JwtTokenProvider;
import com.smart.trip.server.auth.service.TokenCacheService;
import com.smart.trip.server.config.GlobalExceptionHandler;
import com.smart.trip.server.plan.controller.TripPlanController;
import com.smart.trip.server.plan.facade.PdfExportFacade;
import com.smart.trip.server.plan.facade.TripPlanFacade;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 规划 API 端点测试（C-011 AC-6~AC-11，TDD Green 阶段）。
 *
 * <p>覆盖测试矩阵：参数校验(days≤0→400)、503 下游不可用、429 限频、HITL 三动作状态转换
 * (CONFIRM/MODIFY/REJECT)、非法状态转换拒绝、数据隔离(用户A访问用户B的plan→403)。
 * 分层验证：Controller → Facade，Mock TripPlanFacade（Controller 层测试）。</p>
 */
@ExtendWith(MockitoExtension.class)
class TripPlanControllerTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only-32bytes!!";
    private static final SecretKey KEY = new SecretKeySpec(
            SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private static final String VALID_REQUEST_JSON = """
            {
                "destination": "杭州",
                "startDate": "2026-07-01",
                "days": 3,
                "budget": 5000,
                "headcount": 2,
                "travelStyle": "COUPLE",
                "preferences": ["美食"]
            }
            """;

    @Mock private TripPlanFacade tripPlanFacade;
    @Mock private PdfExportFacade pdfExportFacade;
    @Mock private TokenCacheService tokenCacheService;
    @Mock private AgentStatusService agentStatusService;

    private JwtTokenProvider jwtTokenProvider;
    private MockMvc mockMvc;

    private String validAccessToken(long userId, String username, UserRole role) {
        return jwtTokenProvider.generateAccessToken(userId, username, role);
    }

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(KEY);
        TripPlanController controller = new TripPlanController(tripPlanFacade, pdfExportFacade);
        AdminController adminController = new AdminController(agentStatusService, tripPlanFacade);
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtTokenProvider, tokenCacheService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller, adminController)
                .addFilter(filter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // =========================================================================
    // POST /api/v1/trip-plan — 提交规划请求
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/trip-plan — 提交规划")
    class SubmitPlan {

        @Test
        @DisplayName("AC-6/Case-6: 合法请求+token → 202 + planId + status=analyzing")
        void should_return_202_with_planId_when_valid_request() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitPlan(any(TripPlanRequest.class), eq(1L)))
                    .thenReturn(Map.of("planId", "P-20260701-001", "status", "analyzing"));

            mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST_JSON))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.planId").value("P-20260701-001"))
                    .andExpect(jsonPath("$.status").value("analyzing"));
        }

        @Test
        @DisplayName("AC-8/Case-7: days=0 → 400 INVALID_REQUEST + traceId")
        void should_return_400_when_days_zero() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitPlan(any(), eq(1L)))
                    .thenThrow(new InvalidRequestException("days 必须在 [1, 30]: 0"));

            mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"destination":"杭州","startDate":"2026-07-01","days":0,
                                     "budget":5000,"headcount":2}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.traceId").exists());
        }

        @Test
        @DisplayName("AC-8: days 负数 → 400 INVALID_REQUEST")
        void should_return_400_when_days_negative() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitPlan(any(), eq(1L)))
                    .thenThrow(new InvalidRequestException("days 必须在 [1, 30]: -1"));

            mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"destination":"杭州","startDate":"2026-07-01","days":-1,
                                     "budget":5000,"headcount":2}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-8: destination 缺失 → 400 INVALID_REQUEST")
        void should_return_400_when_destination_missing() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitPlan(any(), eq(1L)))
                    .thenThrow(new InvalidRequestException("destination 不可为空"));

            mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"startDate":"2026-07-01","days":3,
                                     "budget":5000,"headcount":2}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-8: budget ≤ 0 → 400 INVALID_REQUEST")
        void should_return_400_when_budget_non_positive() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitPlan(any(), eq(1L)))
                    .thenThrow(new InvalidRequestException("budget 必须 > 0: 0"));

            mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"destination":"杭州","startDate":"2026-07-01","days":3,
                                     "budget":0,"headcount":2}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-7: 提交后关联当前登录用户 userId")
        void should_associate_plan_with_current_user() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitPlan(any(TripPlanRequest.class), eq(1L)))
                    .thenReturn(Map.of("planId", "P-20260701-001", "status", "analyzing"));

            mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST_JSON))
                    .andExpect(status().isAccepted());

            verify(tripPlanFacade).submitPlan(any(TripPlanRequest.class), eq(1L));
        }

        @Test
        @DisplayName("边界: 未认证 → 401")
        void should_return_401_when_not_authenticated() throws Exception {
            mockMvc.perform(post("/api/v1/trip-plan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // GET /api/v1/trip-plan/{planId} — 查询进度/结果
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/trip-plan/{planId} — 查询进度")
    class QueryPlan {

        @Test
        @DisplayName("AC-6/Case-8: 合法查询 → 200 + plan + budget + requiresHumanIntervention")
        void should_return_200_with_plan_details() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);

            Map<String, Object> budget = new LinkedHashMap<>();
            budget.put("total", 5000);
            budget.put("used", 4200);
            budget.put("overrunRate", -0.16);
            budget.put("breakdown", Map.of("transport", 800, "hotel", 2000, "food", 1000, "ticket", 400));
            Map<String, Object> planMap = new LinkedHashMap<>();
            planMap.put("days", java.util.List.of());
            planMap.put("totalCost", 4200);
            planMap.put("budget", budget);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("planId", "P-001");
            detail.put("status", "review");
            detail.put("requiresHumanIntervention", true);
            detail.put("interventionReason", "超预算");
            detail.put("plan", planMap);
            when(tripPlanFacade.queryPlan("P-001", 1L)).thenReturn(detail);

            mockMvc.perform(get("/api/v1/trip-plan/P-001")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.planId").value("P-001"))
                    .andExpect(jsonPath("$.status").value("review"))
                    .andExpect(jsonPath("$.requiresHumanIntervention").value(true))
                    .andExpect(jsonPath("$.plan").exists())
                    .andExpect(jsonPath("$.plan.budget").exists())
                    .andExpect(jsonPath("$.interventionReason").value("超预算"));
        }

        @Test
        @DisplayName("边界: 不存在的 planId → 404")
        void should_return_404_when_plan_not_found() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.queryPlan("P-NONEXISTENT", 1L))
                    .thenThrow(new BaseException(ErrorCode.NOT_FOUND, "规划不存在: P-NONEXISTENT"));

            mockMvc.perform(get("/api/v1/trip-plan/P-NONEXISTENT")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.traceId").exists());
        }

        @Test
        @DisplayName("AC-7/Case-12/数据隔离: 用户 A 查询用户 B 的 plan → 403 FORBIDDEN")
        void should_return_403_when_accessing_other_users_plan() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.queryPlan("P-001", 1L))
                    .thenThrow(new BaseException(ErrorCode.FORBIDDEN, "无权访问此规划"));

            mockMvc.perform(get("/api/v1/trip-plan/P-001")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // POST /api/v1/trip-plan/{planId}/intervene — HITL 介入
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/trip-plan/{planId}/intervene — HITL")
    class Intervene {

        @Test
        @DisplayName("AC-9/Case-9: CONFIRM → 进入 confirmed 态")
        void should_transition_to_confirmed_when_action_confirm() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.intervene(eq("P-001"), eq("CONFIRM"), any(), any(), eq(1L)))
                    .thenReturn(Map.of("planId", "P-001", "status", "confirmed",
                            "requiresHumanIntervention", false));

            mockMvc.perform(post("/api/v1/trip-plan/P-001/intervene")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"CONFIRM\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("confirmed"));
        }

        @Test
        @DisplayName("AC-9: MODIFY → 局部重算（状态回退到 planning/assembling）")
        void should_trigger_recompute_when_action_modify() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.intervene(eq("P-001"), eq("MODIFY"), any(), any(), eq(1L)))
                    .thenReturn(Map.of("planId", "P-001", "status", "planning",
                            "requiresHumanIntervention", false));

            mockMvc.perform(post("/api/v1/trip-plan/P-001/intervene")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"MODIFY\",\"dayIndex\":2,"
                                    + "\"changes\":{\"removeAttraction\":\"A-007\","
                                    + "\"addAttraction\":\"A-012\"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("planning"));
        }

        @Test
        @DisplayName("AC-9: REJECT → 方案作废（状态到 failed 或重新规划）")
        void should_reject_plan_when_action_reject() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.intervene(eq("P-001"), eq("REJECT"), any(), any(), eq(1L)))
                    .thenReturn(Map.of("planId", "P-001", "status", "failed",
                            "requiresHumanIntervention", false));

            mockMvc.perform(post("/api/v1/trip-plan/P-001/intervene")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"REJECT\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("failed"));
        }

        @Test
        @DisplayName("边界: 对已 confirmed 的 plan 再次 intervene → 非法状态转换被拒")
        void should_reject_intervene_on_already_confirmed_plan() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.intervene(eq("P-001"), eq("CONFIRM"), any(), any(), eq(1L)))
                    .thenThrow(new InvalidRequestException("仅 review 态可执行 intervene，当前状态: confirmed"));

            mockMvc.perform(post("/api/v1/trip-plan/P-001/intervene")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"CONFIRM\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("边界: 对 failed 的 plan 尝试 CONFIRM → 非法状态转换被拒")
        void should_reject_confirm_on_failed_plan() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.intervene(eq("P-001"), eq("CONFIRM"), any(), any(), eq(1L)))
                    .thenThrow(new InvalidRequestException("仅 review 态可执行 intervene，当前状态: failed"));

            mockMvc.perform(post("/api/v1/trip-plan/P-001/intervene")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"CONFIRM\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("边界: 对 analyzing 阶段的 plan 尝试 intervene → 非法（还未到 review）")
        void should_reject_intervene_on_analyzing_plan() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.intervene(eq("P-001"), eq("CONFIRM"), any(), any(), eq(1L)))
                    .thenThrow(new InvalidRequestException("仅 review 态可执行 intervene，当前状态: analyzing"));

            mockMvc.perform(post("/api/v1/trip-plan/P-001/intervene")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"CONFIRM\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("边界: MODIFY 引用不存在的 attractionId → 校验错误")
        void should_return_validation_error_when_modify_references_invalid_attraction() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.intervene(eq("P-001"), eq("MODIFY"), any(), any(), eq(1L)))
                    .thenThrow(new InvalidRequestException("MODIFY 引用的 attractionId 不存在: A-999"));

            mockMvc.perform(post("/api/v1/trip-plan/P-001/intervene")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"MODIFY\",\"dayIndex\":1,"
                                    + "\"changes\":{\"removeAttraction\":\"A-999\"}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("数据隔离: 用户 A 对用户 B 的 plan 执行 intervene → 403")
        void should_return_403_when_intervening_on_other_users_plan() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.intervene(eq("P-001"), eq("CONFIRM"), any(), any(), eq(1L)))
                    .thenThrow(new BaseException(ErrorCode.FORBIDDEN, "无权访问此规划"));

            mockMvc.perform(post("/api/v1/trip-plan/P-001/intervene")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"CONFIRM\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // POST /api/v1/trip-plan/{planId}/feedback — 用户反馈
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/trip-plan/{planId}/feedback")
    class Feedback {

        @Test
        @DisplayName("AC-6: feedback 提交成功 → 200")
        void should_return_200_when_feedback_submitted() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitFeedback(eq("P-001"), any(), eq(1L)))
                    .thenReturn(Map.of("message", "反馈已记录"));

            mockMvc.perform(post("/api/v1/trip-plan/P-001/feedback")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":\"UP\",\"comment\":\"整体满意\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("反馈已记录"));
        }

        @Test
        @DisplayName("数据隔离: 用户 A 对用户 B 的 plan 提交反馈 → 403")
        void should_return_403_when_feedback_on_other_users_plan() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitFeedback(eq("P-001"), any(), eq(1L)))
                    .thenThrow(new BaseException(ErrorCode.FORBIDDEN, "无权访问此规划"));

            mockMvc.perform(post("/api/v1/trip-plan/P-001/feedback")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":\"DOWN\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // GET /api/v1/trip-plan/history — 历史规划查询（C-017 AC-3 / DECISION-2）
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/trip-plan/history — 历史列表（C-017 DECISION-2 筛选扩展）")
    class PlanHistory {

        @Test
        @DisplayName("无筛选参数 → 200 + 使用默认分页，筛选参数透传为 null")
        void should_return_200_with_default_paging_when_no_filters() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.getUserPlanHistory(
                    eq(1L), eq(1), eq(10), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(Map.of("list", List.of(), "total", 0));

            mockMvc.perform(get("/api/v1/trip-plan/history")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            verify(tripPlanFacade).getUserPlanHistory(
                    eq(1L), eq(1), eq(10), isNull(), isNull(), isNull(), isNull(), isNull());
        }

        @Test
        @DisplayName("AC-3/DECISION-2: travelStyle/planStatus/日期范围/keyword/分页 → 正确解析并透传")
        void should_parse_and_forward_all_filters() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.getUserPlanHistory(
                    eq(1L), eq(2), eq(20), eq("北京"), eq(TravelStyle.COUPLE), eq("confirmed"),
                    eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 10))))
                    .thenReturn(Map.of("list", List.of(Map.of("planId", "P-001")), "total", 1));

            mockMvc.perform(get("/api/v1/trip-plan/history")
                            .header("Authorization", "Bearer " + token)
                            .param("page", "2")
                            .param("pageSize", "20")
                            .param("keyword", "北京")
                            .param("travelStyle", "COUPLE")
                            .param("planStatus", "confirmed")
                            .param("startDate", "2026-07-01")
                            .param("endDate", "2026-07-10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.list[0].planId").value("P-001"));

            verify(tripPlanFacade).getUserPlanHistory(
                    eq(1L), eq(2), eq(20), eq("北京"), eq(TravelStyle.COUPLE), eq("confirmed"),
                    eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 10)));
        }

        @Test
        @DisplayName("边界: 未认证 → 401")
        void should_return_401_when_not_authenticated() throws Exception {
            mockMvc.perform(get("/api/v1/trip-plan/history"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 降级与错误码
    // =========================================================================

    @Nested
    @DisplayName("降级与统一错误码")
    class ErrorHandling {

        @Test
        @DisplayName("AC-8/Case-10: 下游 Agent 不可用 → 503 SERVICE_UNAVAILABLE + traceId")
        void should_return_503_when_downstream_unavailable() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitPlan(any(), eq(1L)))
                    .thenThrow(new BaseException(ErrorCode.SERVICE_UNAVAILABLE, "下游编排服务不可用"));

            mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST_JSON))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.traceId").exists());
        }

        @Test
        @DisplayName("AC-8: 限频触发 → 429 RATE_LIMITED")
        void should_return_429_when_rate_limited() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitPlan(any(), eq(1L)))
                    .thenThrow(new RateLimitedException("API 限频"));

            mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST_JSON))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        }

        @Test
        @DisplayName("AC-8/AC-11: 所有错误响应含 traceId + 统一结构 {code,message,traceId}")
        void should_include_traceId_in_all_error_responses() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitPlan(any(), eq(1L)))
                    .thenThrow(new InvalidRequestException("days 必须在 [1, 30]: 0"));

            String body = mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"destination":"杭州","startDate":"2026-07-01","days":0,
                                     "budget":5000,"headcount":2}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).contains("\"code\"").contains("\"message\"").contains("\"traceId\"");
        }

        @Test
        @DisplayName("AC-8: 内部异常不裸 500 → 映射为合适的错误码")
        void should_not_expose_raw_500_for_internal_errors() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(tripPlanFacade.submitPlan(any(), eq(1L)))
                    .thenThrow(new RuntimeException("unexpected null pointer"));

            String body = mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST_JSON))
                    .andExpect(status().isInternalServerError())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).contains("\"code\"")
                    .contains("\"message\"")
                    .contains("\"traceId\"")
                    .doesNotContain("unexpected null pointer");
        }
    }

    // =========================================================================
    // 管理端点
    // =========================================================================

    @Nested
    @DisplayName("管理端点 /api/v1/admin/**")
    class AdminEndpoints {

        @Test
        @DisplayName("AC-6: GET /api/v1/admin/agent/status → 200（ADMIN 角色）")
        void should_return_agent_status_for_admin() throws Exception {
            String token = validAccessToken(2L, "admin", UserRole.ADMIN);
            when(tokenCacheService.isAccessTokenValid(2L, token)).thenReturn(true);
            when(agentStatusService.currentStatus()).thenReturn(
                    List.of(new AgentStatusView("agent://supervisor", "RUNNING", "localhost:8081")));

            mockMvc.perform(get("/api/v1/admin/agent/status")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].agentUri").value("agent://supervisor"))
                    .andExpect(jsonPath("$[0].status").value("RUNNING"));
        }

        @Test
        @DisplayName("AC-6: GET /api/v1/admin/plan/{planId} → 200（ADMIN 角色）")
        void should_return_plan_details_for_admin() throws Exception {
            String token = validAccessToken(2L, "admin", UserRole.ADMIN);
            when(tokenCacheService.isAccessTokenValid(2L, token)).thenReturn(true);
            when(tripPlanFacade.queryPlanAsAdmin("P-001"))
                    .thenReturn(Map.of("planId", "P-001", "status", "review"));

            mockMvc.perform(get("/api/v1/admin/plan/P-001")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.planId").value("P-001"))
                    .andExpect(jsonPath("$.status").value("review"));
        }

        @Test
        @DisplayName("AC-3: USER 角色访问管理端点 → 403")
        void should_return_403_for_user_role_on_admin_endpoints() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);

            mockMvc.perform(get("/api/v1/admin/agent/status")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================================================================
    // GET /api/v1/trip-plan/{planId}/export — PDF 导出（C-014）
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/trip-plan/{planId}/export — PDF 导出（C-014）")
    class ExportPdf {

        private static final byte[] FAKE_PDF = {0x25, 0x50, 0x44, 0x46};

        @Test
        @DisplayName("AC-1/Case-1: confirmed plan → 200 + application/pdf + attachment")
        void should_return_pdf_with_correct_headers() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(pdfExportFacade.exportPdf("P-001", 1L)).thenReturn(FAKE_PDF);

            mockMvc.perform(get("/api/v1/trip-plan/P-001/export?format=pdf")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("Content-Disposition",
                            containsString("attachment")))
                    .andExpect(header().string("Content-Disposition",
                            containsString("P-001.pdf")));
        }

        @Test
        @DisplayName("Case-2: planId 不存在 → 404 NOT_FOUND + traceId")
        void should_return_404_when_plan_not_found() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(pdfExportFacade.exportPdf("P-NOT-EXIST", 1L))
                    .thenThrow(new BaseException(ErrorCode.NOT_FOUND, "规划不存在"));

            mockMvc.perform(get("/api/v1/trip-plan/P-NOT-EXIST/export?format=pdf")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.traceId").exists());
        }

        @Test
        @DisplayName("Case-3: analyzing 状态 → 400 PLAN_NOT_EXPORTABLE")
        void should_return_400_when_plan_not_exportable() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(pdfExportFacade.exportPdf("P-001", 1L))
                    .thenThrow(new BaseException(ErrorCode.PLAN_NOT_EXPORTABLE,
                            "当前状态不可导出"));

            mockMvc.perform(get("/api/v1/trip-plan/P-001/export?format=pdf")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PLAN_NOT_EXPORTABLE"));
        }

        @Test
        @DisplayName("边界: format=excel → 400 INVALID_REQUEST")
        void should_return_400_when_format_unsupported() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);

            mockMvc.perform(get("/api/v1/trip-plan/P-001/export?format=excel")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("边界: 未认证 → 401")
        void should_return_401_when_not_authenticated() throws Exception {
            mockMvc.perform(get("/api/v1/trip-plan/P-001/export?format=pdf"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("数据隔离: 他人 plan → 403 FORBIDDEN")
        void should_return_403_when_other_users_plan() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(pdfExportFacade.exportPdf("P-001", 1L))
                    .thenThrow(new BaseException(ErrorCode.FORBIDDEN, "无权访问此规划"));

            mockMvc.perform(get("/api/v1/trip-plan/P-001/export?format=pdf")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("回归: 导出接口错误响应含统一结构 {code,message,traceId}")
        void should_include_traceId_in_export_error_response() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(pdfExportFacade.exportPdf("P-001", 1L))
                    .thenThrow(new BaseException(ErrorCode.NOT_FOUND, "规划不存在"));

            String body = mockMvc.perform(get("/api/v1/trip-plan/P-001/export?format=pdf")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).contains("\"code\"")
                    .contains("\"message\"")
                    .contains("\"traceId\"");
        }
    }

    // =========================================================================
    // 异步规划流程 Happy Path
    // =========================================================================

    @Nested
    @DisplayName("异步规划闭环 Happy Path")
    class HappyPath {

        @Test
        @DisplayName("AC-7: 提交→轮询至 review→CONFIRM→confirmed（完整闭环）")
        void should_complete_full_lifecycle_submit_poll_confirm() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);

            // 1. 提交 → 202 + analyzing
            when(tripPlanFacade.submitPlan(any(TripPlanRequest.class), eq(1L)))
                    .thenReturn(Map.of("planId", "P-001", "status", "analyzing"));
            mockMvc.perform(post("/api/v1/trip-plan")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST_JSON))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.planId").value("P-001"))
                    .andExpect(jsonPath("$.status").value("analyzing"));

            // 2. 轮询 → review + requiresHumanIntervention
            when(tripPlanFacade.queryPlan("P-001", 1L)).thenReturn(
                    Map.of("planId", "P-001", "status", "review",
                            "requiresHumanIntervention", true));
            mockMvc.perform(get("/api/v1/trip-plan/P-001")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("review"))
                    .andExpect(jsonPath("$.requiresHumanIntervention").value(true));

            // 3. CONFIRM → confirmed
            when(tripPlanFacade.intervene(eq("P-001"), eq("CONFIRM"), any(), any(), eq(1L)))
                    .thenReturn(Map.of("planId", "P-001", "status", "confirmed",
                            "requiresHumanIntervention", false));
            mockMvc.perform(post("/api/v1/trip-plan/P-001/intervene")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"CONFIRM\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("confirmed"));
        }
    }
}
