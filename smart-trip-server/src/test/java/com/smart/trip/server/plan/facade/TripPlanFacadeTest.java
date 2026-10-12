package com.smart.trip.server.plan.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smart.trip.agent.supervisor.SupervisorAgent;
import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.PlanStatus;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.Budget;
import com.smart.trip.common.model.BudgetBreakdown;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.server.plan.service.PlanIdSequenceService;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.persistence.TripPlanPersistenceService;
import com.smart.trip.skills.supervisor.TripPlanResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TripPlanFacade 单元测试（C-011 AC-6~AC-9）。
 *
 * <p>直接实例化 TripPlanFacade，Mock PlanCacheService/SupervisorAgent/ObjectMapper/AgentMetrics。
 * 覆盖状态机、所有权校验、HITL 动作、反馈存储、管理端查询等业务路径。</p>
 */
@ExtendWith(MockitoExtension.class)
class TripPlanFacadeTest {

    @Mock PlanCacheService planCacheService;
    @Mock SupervisorAgent supervisorAgent;
    @Mock TripPlanPersistenceService tripPlanPersistenceService;
    @Mock PlanIdSequenceService planIdSequenceService;

    SimpleMeterRegistry meterRegistry;
    AgentMetrics agentMetrics;
    ObjectMapper objectMapper;
    TripPlanFacade facade;

    private static final long USER_ID = 10L;
    private static final String PLAN_ID = "P-20260617-001";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        agentMetrics = new AgentMetrics(meterRegistry);
        // lenient：并非每个测试都会触发 planId 生成，避免 UnnecessaryStubbingException
        lenient().when(planIdSequenceService.nextSequence(any())).thenReturn(1);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        facade = new TripPlanFacade(planCacheService, supervisorAgent,
                objectMapper, agentMetrics, Runnable::run, null,
                tripPlanPersistenceService, planIdSequenceService);
    }

    /**
     * 通用 Answer：setStatus 被调用时，仅当 status 不是 "analyzing" 时 countDown latch。
     * 避免 submitPlan 中初始 "analyzing" 调用触发 latch。
     */
    private CountDownLatch orchestrationLatch() {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            String status = invocation.getArgument(1);
            if (!"analyzing".equals(status)) {
                latch.countDown();
            }
            return null;
        }).when(planCacheService).setStatus(anyString(), anyString());
        return latch;
    }

    // ─── submitPlan ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("submitPlan")
    class SubmitPlanTests {

        @Test
        @DisplayName("提交合法请求返回 planId + status=analyzing，异步触发编排")
        void should_return_plan_id_and_analyzing_status() {
            TripPlanRequest req = validRequest();

            Map<String, Object> result = facade.submitPlan(req, USER_ID);

            assertThat(result).containsKey("planId").containsEntry("status", "analyzing");
            verify(planCacheService).setStatus(anyString(), eq("analyzing"));
            verify(planCacheService).setUserId(anyString(), eq(String.valueOf(USER_ID)));
        }

        @Test
        @DisplayName("days <= 0 时抛出 InvalidRequestException")
        void should_throw_on_invalid_days() {
            TripPlanRequest req = new TripPlanRequest("云南", LocalDate.now(), 0,
                    BigDecimal.valueOf(8000), 2, TravelStyle.COUPLE, null);

            assertThatThrownBy(() -> facade.submitPlan(req, USER_ID))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    // ─── queryPlan ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("queryPlan")
    class QueryPlanTests {

        @Test
        @DisplayName("所有权校验通过时返回详情")
        void should_return_detail_when_owner() throws Exception {
            Map<String, Object> detail = Map.of("planId", PLAN_ID, "status", "review");
            when(planCacheService.exists(PLAN_ID)).thenReturn(true);
            when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of(String.valueOf(USER_ID)));
            when(planCacheService.getPlan(PLAN_ID)).thenReturn(Optional.of("{\"planId\":\"P\"}"));

            Map<String, Object> result = facade.queryPlan(PLAN_ID, USER_ID);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("读取旧版 result.plan 缓存时归一为前端顶层 plan，并保留实时状态覆盖")
        void should_normalize_legacy_result_plan_and_overlay_live_status() throws Exception {
            Map<String, Object> legacyPlan = Map.of("days", List.of(), "totalCost", 0);
            Map<String, Object> legacyResult = Map.of(
                    "plan", legacyPlan,
                    "hitlReasons", List.of("超预算"));
            Map<String, Object> cachedDetail = new HashMap<>();
            cachedDetail.put("planId", PLAN_ID);
            cachedDetail.put("status", "budgeting");
            cachedDetail.put("requiresHumanIntervention", true);
            cachedDetail.put("result", legacyResult);

            when(planCacheService.exists(PLAN_ID)).thenReturn(true);
            when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of(String.valueOf(USER_ID)));
            when(planCacheService.getPlan(PLAN_ID))
                    .thenReturn(Optional.of(new ObjectMapper().writeValueAsString(cachedDetail)));
            when(planCacheService.getStatus(PLAN_ID)).thenReturn(Optional.of("confirmed"));

            Map<String, Object> result = facade.queryPlan(PLAN_ID, USER_ID);

            assertThat(result).containsEntry("status", "confirmed");
            assertThat(result).containsEntry("plan", legacyPlan);
            assertThat(result).doesNotContainKey("result");
            assertThat(result).containsEntry("interventionReason", "超预算");
        }

        @Test
        @DisplayName("planId 不存在时抛出 NOT_FOUND 404")
        void should_throw_not_found_when_plan_missing() {
            when(planCacheService.exists(PLAN_ID)).thenReturn(false);

            assertThatThrownBy(() -> facade.queryPlan(PLAN_ID, USER_ID))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("userId 不匹配时抛出 FORBIDDEN 403")
        void should_throw_forbidden_when_not_owner() {
            when(planCacheService.exists(PLAN_ID)).thenReturn(true);
            when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of("999"));

            assertThatThrownBy(() -> facade.queryPlan(PLAN_ID, USER_ID))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    // ─── queryPlanAsAdmin ────────────────────────────────────────────────────

    @Nested
    @DisplayName("queryPlanAsAdmin")
    class QueryPlanAsAdminTests {

        @Test
        @DisplayName("plan 存在时跳过 userId 隔离直接返回详情")
        void should_return_detail_without_ownership_check() {
            when(planCacheService.exists(PLAN_ID)).thenReturn(true);
            when(planCacheService.getPlan(PLAN_ID))
                    .thenReturn(Optional.of("{\"planId\":\"" + PLAN_ID + "\",\"status\":\"review\"}"));

            Map<String, Object> result = facade.queryPlanAsAdmin(PLAN_ID);

            assertThat(result).containsEntry("planId", PLAN_ID);
            verify(planCacheService, never()).getUserId(any());
        }

        @Test
        @DisplayName("plan 不存在时抛出 NOT_FOUND")
        void should_throw_not_found_when_plan_missing() {
            when(planCacheService.exists(PLAN_ID)).thenReturn(false);

            assertThatThrownBy(() -> facade.queryPlanAsAdmin(PLAN_ID))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        }
    }

    // ─── intervene ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("intervene")
    class InterveneTests {

        @Test
        @DisplayName("CONFIRM: review → confirmed，human.intervention.count 递增")
        void should_confirm_plan_from_review() {
            stubOwnershipAndDetail("review");

            Map<String, Object> result = facade.intervene(PLAN_ID, "CONFIRM", null, null, USER_ID);

            assertThat(result).containsEntry("status", "confirmed");
            verify(planCacheService).setStatus(PLAN_ID, "confirmed");
            assertThat(meterRegistry.counter("human.intervention.count").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("REJECT: review → failed，human.intervention.count 递增")
        void should_reject_plan_from_review() {
            stubOwnershipAndDetail("review");

            Map<String, Object> result = facade.intervene(PLAN_ID, "REJECT", null, null, USER_ID);

            assertThat(result).containsEntry("status", "failed");
            verify(planCacheService).setStatus(PLAN_ID, "failed");
            assertThat(meterRegistry.counter("human.intervention.count").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("MODIFY 不含 removeAttraction: review → planning")
        void should_modify_plan_no_remove() {
            stubOwnershipAndDetail("review");

            Map<String, Object> result = facade.intervene(PLAN_ID, "MODIFY", 0,
                    Map.of("addAttraction", "A-099"), USER_ID);

            assertThat(result).containsEntry("status", "planning");
            verify(planCacheService).setStatus(PLAN_ID, "planning");
        }

        @Test
        @DisplayName("MODIFY removeAttraction 存在时 review → planning")
        void should_modify_plan_with_valid_remove() {
            stubOwnership();
            Map<String, Object> detail = buildDetailWithAttractionId("review", "A-007");
            stubDetailJson(detail);

            Map<String, Object> changes = new HashMap<>();
            changes.put("removeAttraction", "A-007");
            Map<String, Object> result = facade.intervene(PLAN_ID, "MODIFY", 0, changes, USER_ID);

            assertThat(result).containsEntry("status", "planning");
        }

        @Test
        @DisplayName("MODIFY removeAttraction 不存在时抛出 InvalidRequestException")
        void should_throw_when_attraction_not_found() {
            stubOwnershipAndDetail("review");

            Map<String, Object> changes = new HashMap<>();
            changes.put("removeAttraction", "A-NONEXISTENT");

            assertThatThrownBy(() -> facade.intervene(PLAN_ID, "MODIFY", 0, changes, USER_ID))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("A-NONEXISTENT");
        }

        @Test
        @DisplayName("非 review 态执行 intervene 抛出 InvalidRequestException")
        void should_throw_when_status_not_review() {
            stubOwnershipAndDetail("confirmed");

            assertThatThrownBy(() -> facade.intervene(PLAN_ID, "CONFIRM", null, null, USER_ID))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("confirmed");
        }

        @Test
        @DisplayName("非法 action 抛出 InvalidRequestException（在 checkOwnership 前）")
        void should_throw_on_invalid_action() {
            assertThatThrownBy(() -> facade.intervene(PLAN_ID, "UNKNOWN", null, null, USER_ID))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("null action 抛出 InvalidRequestException（在 checkOwnership 前）")
        void should_throw_on_null_action() {
            assertThatThrownBy(() -> facade.intervene(PLAN_ID, null, null, null, USER_ID))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("不存在的 planId 抛出 NOT_FOUND")
        void should_throw_not_found_when_plan_missing() {
            when(planCacheService.exists(PLAN_ID)).thenReturn(false);

            assertThatThrownBy(() -> facade.intervene(PLAN_ID, "CONFIRM", null, null, USER_ID))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("userId 不匹配时抛出 FORBIDDEN")
        void should_throw_forbidden_when_not_owner() {
            when(planCacheService.exists(PLAN_ID)).thenReturn(true);
            when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of("999"));

            assertThatThrownBy(() -> facade.intervene(PLAN_ID, "CONFIRM", null, null, USER_ID))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        }

        private void stubOwnership() {
            when(planCacheService.exists(PLAN_ID)).thenReturn(true);
            when(planCacheService.getUserId(PLAN_ID))
                    .thenReturn(Optional.of(String.valueOf(USER_ID)));
        }

        private void stubOwnershipAndDetail(String status) {
            stubOwnership();
            Map<String, Object> detail = new HashMap<>();
            detail.put("planId", PLAN_ID);
            detail.put("status", status);
            detail.put("requiresHumanIntervention", false);
            stubDetailJson(detail);
        }

        private void stubDetailJson(Map<String, Object> detail) {
            try {
                String json = new ObjectMapper().writeValueAsString(detail);
                when(planCacheService.getPlan(PLAN_ID)).thenReturn(Optional.of(json));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("MODIFY removeAttraction 支持前端 plan.attractions[].id 字段")
        void should_modify_plan_with_frontend_attraction_id() {
            stubOwnership();
            Map<String, Object> attraction = new HashMap<>();
            attraction.put("id", "A-007");
            attraction.put("name", "Test attraction");
            Map<String, Object> day = new HashMap<>();
            day.put("attractions", List.of(attraction));
            Map<String, Object> plan = new HashMap<>();
            plan.put("days", List.of(day));
            Map<String, Object> detail = new HashMap<>();
            detail.put("planId", PLAN_ID);
            detail.put("status", "review");
            detail.put("requiresHumanIntervention", false);
            detail.put("plan", plan);
            stubDetailJson(detail);

            Map<String, Object> changes = new HashMap<>();
            changes.put("removeAttraction", "A-007");
            Map<String, Object> result = facade.intervene(PLAN_ID, "MODIFY", 0, changes, USER_ID);

            assertThat(result).containsEntry("status", "planning");
        }

        private Map<String, Object> buildDetailWithAttractionId(String status, String attractionId) {
            Map<String, Object> attraction = new HashMap<>();
            attraction.put("attractionId", attractionId);
            attraction.put("name", "Test attraction");
            Map<String, Object> day = new HashMap<>();
            day.put("attractions", List.of(attraction));
            Map<String, Object> detail = new HashMap<>();
            detail.put("planId", PLAN_ID);
            detail.put("status", status);
            detail.put("requiresHumanIntervention", false);
            detail.put("days", List.of(day));
            return detail;
        }

        @Test
        @DisplayName("C-018: plan_review MODIFY rejected")
        void should_reject_modify_on_plan_review() {
            stubOwnershipAndDetail("plan_review");
            assertThatThrownBy(() -> facade.intervene(PLAN_ID, "MODIFY", null, null, USER_ID))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("MODIFY");
        }

        @Test
        @DisplayName("C-018: plan_review REJECT -> failed")
        void should_reject_plan_on_plan_review() {
            stubOwnership();
            Map detail = new HashMap<>();
            detail.put("planId", PLAN_ID);
            detail.put("status", "plan_review");
            stubDetailJson(detail);
            // T-14：不再需要 pendingConfirm — reject 直接置 failed
            Map result = facade.intervene(PLAN_ID, "REJECT", null, null, USER_ID);
            assertThat(result).containsEntry("status", "failed");
            verify(planCacheService).setStatus(PLAN_ID, "failed");
        }

    }

    // ─── submitFeedback ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("submitFeedback")
    class SubmitFeedbackTests {

        @Test
        @DisplayName("反馈提交成功返回确认消息")
        void should_return_confirmation() {
            when(planCacheService.exists(PLAN_ID)).thenReturn(true);
            when(planCacheService.getUserId(PLAN_ID))
                    .thenReturn(Optional.of(String.valueOf(USER_ID)));

            Map<String, Object> feedback = Map.of("rating", "UP", "comment", "满意");
            Map<String, Object> result = facade.submitFeedback(PLAN_ID, feedback, USER_ID);

            assertThat(result).containsEntry("message", "反馈已记录");
            verify(planCacheService).storeFeedback(eq(PLAN_ID), anyString());
        }

        @Test
        @DisplayName("非所有者提交反馈抛出 FORBIDDEN")
        void should_throw_forbidden_when_not_owner() {
            when(planCacheService.exists(PLAN_ID)).thenReturn(true);
            when(planCacheService.getUserId(PLAN_ID)).thenReturn(Optional.of("999"));

            assertThatThrownBy(() -> facade.submitFeedback(PLAN_ID, Map.of(), USER_ID))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    // ─── executeOrchestration (async) ────────────────────────────────────────

    @Nested
    @DisplayName("executeOrchestration (via submitPlan, CountDownLatch)")
    class ExecuteOrchestrationTests {

        @Test
        @DisplayName("Supervisor 返回含 requiresHITL=false 的 TripPlanResult → 缓存前端顶层 plan 且 status=confirmed")
        void should_return_confirmed_after_orchestration() throws Exception {
            when(supervisorAgent.agentUri()).thenReturn("agent://supervisor");
            TripPlanResult confirmedResult = new TripPlanResult(
                    sampleTripPlan(false), false, List.of(), false,
                    TripPlanResult.Telemetry.none(),
                    TripPlanResult.PATH_DETERMINISTIC, false);
            Msg resultMsg = Msg.taskResult("agent://supervisor", "facade://trip-plan",
                    "T-test", confirmedResult, false);
            when(supervisorAgent.receive(any(Msg.class))).thenReturn(resultMsg);

            CountDownLatch latch = orchestrationLatch();
            Map<String, Object> submitResult = facade.submitPlan(validRequest(), USER_ID);
            assertThat(submitResult).containsEntry("status", "analyzing");

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertThat(completed).as("async orchestration should complete within 5s").isTrue();

            verify(planCacheService).setStatus(anyString(), eq("confirmed"));
            Map<String, Object> completedDetail = lastStoredDetail();
            assertThat(completedDetail).containsEntry("status", "confirmed")
                    .containsEntry("requiresHumanIntervention", false)
                    .containsKey("plan")
                    .doesNotContainKey("result");
            Map<String, Object> plan = asMap(completedDetail.get("plan"));
            assertThat(plan).containsKeys("days", "totalCost", "budget");
            List<?> days = (List<?>) plan.get("days");
            assertThat(days).hasSize(1);
            Map<String, Object> day = asMap(days.get(0));
            assertThat(day).containsEntry("index", 1).containsKey("attractions");
            Map<String, Object> attraction = asMap(((List<?>) day.get("attractions")).get(0));
            assertThat(attraction).containsEntry("id", "A-001")
                    .containsEntry("durationMinutes", 120);

            // Full round-trip: GET planId → status=confirmed（非 analyzing）
            String planId = (String) submitResult.get("planId");
            when(planCacheService.exists(planId)).thenReturn(true);
            when(planCacheService.getUserId(planId))
                    .thenReturn(Optional.of(String.valueOf(USER_ID)));
            when(planCacheService.getPlan(planId))
                    .thenReturn(Optional.of(new ObjectMapper().writeValueAsString(completedDetail)));

            Map<String, Object> planDetail = facade.queryPlan(planId, USER_ID);
            assertThat(planDetail).containsEntry("status", "confirmed").containsKey("plan");
        }

        @Test
        @DisplayName("Supervisor 返回含 requiresHITL=true 的 TripPlanResult 时状态设为 review 并缓存 interventionReason")
        void should_set_status_review_on_task_result() throws Exception {
            when(supervisorAgent.agentUri()).thenReturn("agent://supervisor");
            TripPlanResult hitlResult = new TripPlanResult(
                    sampleTripPlan(true), true, List.of("超预算"), false,
                    TripPlanResult.Telemetry.none(),
                    TripPlanResult.PATH_DETERMINISTIC, false);
            Msg resultMsg = Msg.taskResult("agent://supervisor", "facade://trip-plan",
                    "T-test", hitlResult, false);
            when(supervisorAgent.receive(any(Msg.class))).thenReturn(resultMsg);

            CountDownLatch latch = orchestrationLatch();
            facade.submitPlan(validRequest(), USER_ID);

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertThat(completed).as("async orchestration should complete within 5s").isTrue();
            verify(planCacheService).setStatus(anyString(), eq("review"));
            assertThat(lastStoredDetail()).containsEntry("requiresHumanIntervention", true)
                    .containsEntry("interventionReason", "超预算")
                    .containsKey("plan")
                    .doesNotContainKey("result");
        }

        @Test
        @DisplayName("Supervisor 抛出异常时状态设为 failed")
        void should_set_status_failed_on_exception() throws Exception {
            when(supervisorAgent.agentUri()).thenReturn("agent://supervisor");
            when(supervisorAgent.receive(any(Msg.class)))
                    .thenThrow(new RuntimeException("Agent unavailable"));

            CountDownLatch latch = orchestrationLatch();
            facade.submitPlan(validRequest(), USER_ID);
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertThat(completed).as("async orchestration should complete within 5s").isTrue();

            verify(planCacheService).setStatus(anyString(), eq("failed"));
        }

        @Test
        @DisplayName("catch 块中 setStatus 也抛异常时，不吞没异常，日志可观测")
        void should_not_silently_swallow_catch_block_exception() throws Exception {
            when(supervisorAgent.agentUri()).thenReturn("agent://supervisor");
            when(supervisorAgent.receive(any(Msg.class)))
                    .thenThrow(new RuntimeException("Agent unavailable"));

            // 模拟 catch 块中 planCacheService.setStatus 也抛异常（如 Redis 断连）
            // 用 anyString,anyString 覆盖所有 setStatus 调用，避免 Mockito strict 模式冲突
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                String status = invocation.getArgument(1);
                if ("failed".equals(status)) {
                    latch.countDown();
                    throw new RuntimeException("Redis connection refused");
                }
                return null;
            }).when(planCacheService).setStatus(anyString(), anyString());

            facade.submitPlan(validRequest(), USER_ID);
            // 即使 catch 块也抛异常，setStatus(failed) 应仍被调用（latch countDown）
            boolean called = latch.await(5, TimeUnit.SECONDS);
            assertThat(called).as("setStatus(failed) should be called even when catch block also throws").isTrue();
        }
    }

    // ─── planId 生成（C-017 T-9：PlanIdSequenceService 注入替换 dailySequence） ──

    @Nested
    @DisplayName("planId 生成（PlanIdSequenceService 注入）")
    class PlanIdGenerationTests {

        @Test
        @DisplayName("submitPlan 使用注入的 PlanIdSequenceService 生成序列号（替换内存 AtomicInteger）")
        void should_use_injected_sequence_service() {
            when(planIdSequenceService.nextSequence(any())).thenReturn(42);

            Map<String, Object> result = facade.submitPlan(validRequest(), USER_ID);

            verify(planIdSequenceService).nextSequence(any(LocalDate.class));
            assertThat((String) result.get("planId")).endsWith("-042");
        }
    }

    // ─── 历史规划持久化挂载（C-017 FIX-1/DECISION-5） ─────────────────────────

    @Nested
    @DisplayName("历史规划持久化挂载（DECISION-5）")
    class PersistenceHookTests {

        @Test
        @DisplayName("立即 CONFIRMED（无 HITL）→ 调用 TripPlanPersistenceService.save(plan, userId, confirmed)")
        void should_persist_on_immediate_confirmed() throws Exception {
            when(supervisorAgent.agentUri()).thenReturn("agent://supervisor");
            TripPlan plan = sampleTripPlan(false);
            TripPlanResult confirmedResult = new TripPlanResult(
                    plan, false, List.of(), false,
                    TripPlanResult.Telemetry.none(), TripPlanResult.PATH_DETERMINISTIC, false);
            Msg resultMsg = Msg.taskResult("agent://supervisor", "facade://trip-plan",
                    "T-persist-1", confirmedResult, false);
            when(supervisorAgent.receive(any(Msg.class))).thenReturn(resultMsg);

            CountDownLatch latch = orchestrationLatch();
            facade.submitPlan(validRequest(), USER_ID);
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            verify(tripPlanPersistenceService, timeout(2000))
                    .save(eq(plan), eq(String.valueOf(USER_ID)), eq("confirmed"));
        }

        @Test
        @DisplayName("C-017 FIX-3: native 成功（PATH_NATIVE, 无 HITL）→ 同样调用持久化，不依赖编排路径")
        void should_persist_on_immediate_confirmed_via_native_path() throws Exception {
            when(supervisorAgent.agentUri()).thenReturn("agent://supervisor");
            TripPlan plan = sampleTripPlan(false);
            TripPlanResult confirmedResult = new TripPlanResult(
                    plan, false, List.of(), false,
                    TripPlanResult.Telemetry.none(), TripPlanResult.PATH_NATIVE, false);
            Msg resultMsg = Msg.taskResult("agent://supervisor", "facade://trip-plan",
                    "T-persist-native-1", confirmedResult, false);
            when(supervisorAgent.receive(any(Msg.class))).thenReturn(resultMsg);

            CountDownLatch latch = orchestrationLatch();
            facade.submitPlan(validRequest(), USER_ID);
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            verify(tripPlanPersistenceService, timeout(2000))
                    .save(eq(plan), eq(String.valueOf(USER_ID)), eq("confirmed"));
        }

        @Test
        @DisplayName("requiresHITL=true（REVIEW 态）→ 不调用持久化（AC-1 仅终态 CONFIRMED/FAILED 挂载）")
        void should_not_persist_on_review() throws Exception {
            when(supervisorAgent.agentUri()).thenReturn("agent://supervisor");
            TripPlanResult hitlResult = new TripPlanResult(
                    sampleTripPlan(true), true, List.of("超预算"), false,
                    TripPlanResult.Telemetry.none(), TripPlanResult.PATH_DETERMINISTIC, false);
            Msg resultMsg = Msg.taskResult("agent://supervisor", "facade://trip-plan",
                    "T-persist-2", hitlResult, false);
            when(supervisorAgent.receive(any(Msg.class))).thenReturn(resultMsg);

            CountDownLatch latch = orchestrationLatch();
            facade.submitPlan(validRequest(), USER_ID);
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            verify(tripPlanPersistenceService, timeout(500).times(0))
                    .save(any(), any(), any());
        }

        @Test
        @DisplayName("编排异常（FAILED）→ 调用持久化，plan 为空 days + zero 预算（B-1）")
        void should_persist_minimal_plan_on_failure() throws Exception {
            when(supervisorAgent.agentUri()).thenReturn("agent://supervisor");
            when(supervisorAgent.receive(any(Msg.class)))
                    .thenThrow(new RuntimeException("Agent unavailable"));

            CountDownLatch latch = orchestrationLatch();
            facade.submitPlan(validRequest(), USER_ID);
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<TripPlan> planCaptor = ArgumentCaptor.forClass(TripPlan.class);
            verify(tripPlanPersistenceService, timeout(2000))
                    .save(planCaptor.capture(), eq(String.valueOf(USER_ID)), eq("failed"));
            TripPlan failedPlan = planCaptor.getValue();
            assertThat(failedPlan.days()).isEmpty();
            assertThat(failedPlan.totalCost()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(failedPlan.status()).isEqualTo(PlanStatus.FAILED);
        }

        @Test
        @DisplayName("tripPlanPersistenceService 为 null 时不抛异常（降级）")
        void should_not_throw_when_persistence_service_null() throws Exception {
            TripPlanFacade noPersistenceFacade = new TripPlanFacade(
                    planCacheService, supervisorAgent, new ObjectMapper(), agentMetrics);

            when(supervisorAgent.agentUri()).thenReturn("agent://supervisor");
            TripPlanResult confirmedResult = new TripPlanResult(
                    sampleTripPlan(false), false, List.of(), false,
                    TripPlanResult.Telemetry.none(), TripPlanResult.PATH_DETERMINISTIC, false);
            Msg resultMsg = Msg.taskResult("agent://supervisor", "facade://trip-plan",
                    "T-persist-4", confirmedResult, false);
            when(supervisorAgent.receive(any(Msg.class))).thenReturn(resultMsg);

            CountDownLatch latch = orchestrationLatch();
            noPersistenceFacade.submitPlan(validRequest(), USER_ID);
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            // 到达此处即验证通过：无异常抛出
        }
    }

    // ─── requestFingerprint (缓存命中 / 跳过编排) ─────────────────────────────

    @Nested
    @DisplayName("requestFingerprint")
    class RequestFingerprintTests {

        @Test
        @DisplayName("相同参数二次提交命中 confirmed 缓存，直接返回已有 planId，不触发编排")
        void should_return_cached_plan_when_fingerprint_matches_confirmed() {
            TripPlanRequest req = validRequest();
            String fingerprint = TripPlanFacade.computeFingerprint(req);

            when(planCacheService.getRequestFingerprint(fingerprint))
                    .thenReturn(Optional.of(PLAN_ID));
            when(planCacheService.getStatus(PLAN_ID))
                    .thenReturn(Optional.of("confirmed"));

            Map<String, Object> result = facade.submitPlan(req, USER_ID);

            assertThat(result).containsEntry("planId", PLAN_ID)
                    .containsEntry("status", "confirmed");
            verify(supervisorAgent, never()).receive(any());
            verify(planCacheService, never()).setStatus(anyString(), eq("analyzing"));
        }

        @Test
        @DisplayName("相同参数命中 review 缓存，直接返回已有 planId")
        void should_return_cached_plan_when_fingerprint_matches_review() {
            TripPlanRequest req = validRequest();
            String fingerprint = TripPlanFacade.computeFingerprint(req);

            when(planCacheService.getRequestFingerprint(fingerprint))
                    .thenReturn(Optional.of(PLAN_ID));
            when(planCacheService.getStatus(PLAN_ID))
                    .thenReturn(Optional.of("review"));

            Map<String, Object> result = facade.submitPlan(req, USER_ID);

            assertThat(result).containsEntry("planId", PLAN_ID)
                    .containsEntry("status", "review");
            verify(supervisorAgent, never()).receive(any());
        }

        @Test
        @DisplayName("指纹命中但状态为 failed，不命中缓存，走新编排")
        void should_not_hit_cache_when_status_is_failed() {
            TripPlanRequest req = validRequest();
            String fingerprint = TripPlanFacade.computeFingerprint(req);

            when(planCacheService.getRequestFingerprint(fingerprint))
                    .thenReturn(Optional.of(PLAN_ID));
            when(planCacheService.getStatus(PLAN_ID))
                    .thenReturn(Optional.of("failed"));

            Map<String, Object> result = facade.submitPlan(req, USER_ID);

            assertThat(result).containsEntry("status", "analyzing");
            assertThat(result.get("planId")).isNotEqualTo(PLAN_ID);
        }

        @Test
        @DisplayName("指纹命中但状态 key 已过期（Optional.empty），走新编排")
        void should_not_hit_cache_when_status_expired() {
            TripPlanRequest req = validRequest();
            String fingerprint = TripPlanFacade.computeFingerprint(req);

            when(planCacheService.getRequestFingerprint(fingerprint))
                    .thenReturn(Optional.of(PLAN_ID));
            when(planCacheService.getStatus(PLAN_ID))
                    .thenReturn(Optional.empty());

            Map<String, Object> result = facade.submitPlan(req, USER_ID);

            assertThat(result).containsEntry("status", "analyzing");
        }

        @Test
        @DisplayName("无指纹缓存，走正常编排流程")
        void should_orchestrate_when_no_fingerprint_cached() {
            TripPlanRequest req = validRequest();
            String fingerprint = TripPlanFacade.computeFingerprint(req);

            when(planCacheService.getRequestFingerprint(fingerprint))
                    .thenReturn(Optional.empty());

            Map<String, Object> result = facade.submitPlan(req, USER_ID);

            assertThat(result).containsEntry("status", "analyzing");
            verify(planCacheService).setStatus(anyString(), eq("analyzing"));
        }

        @Test
        @DisplayName("computeFingerprint 对相同参数产出稳定 hash")
        void fingerprint_is_stable_for_same_request() {
            TripPlanRequest r1 = validRequest();
            TripPlanRequest r2 = validRequest();

            assertThat(TripPlanFacade.computeFingerprint(r1))
                    .isEqualTo(TripPlanFacade.computeFingerprint(r2));
        }

        @Test
        @DisplayName("computeFingerprint 对不同参数产出不同 hash")
        void fingerprint_differs_for_different_request() {
            TripPlanRequest r1 = validRequest();
            TripPlanRequest r2 = new TripPlanRequest("北京", LocalDate.now(), 3,
                    BigDecimal.valueOf(5000), 1, TravelStyle.SOLO, List.of("美食"));

            assertThat(TripPlanFacade.computeFingerprint(r1))
                    .isNotEqualTo(TripPlanFacade.computeFingerprint(r2));
        }
    }

    // ─── 历史查询改造（C-017 T-6：Redis-first → MySQL-fallback + 筛选扩展） ────

    @Nested
    @DisplayName("历史查询（DECISION-1/2：Redis-first → MySQL-fallback + 筛选扩展）")
    class HistoryQueryTests {

        private static final String UID_STR = String.valueOf(USER_ID);

        @Test
        @DisplayName("Redis 有完整摘要时，使用 Redis 路径，按 travelStyle 筛选")
        void should_filter_by_travel_style_on_redis_path() throws JsonProcessingException {
            stubRedisSummaries(
                    summary("P-001", "云南", TravelStyle.COUPLE, "confirmed", "2026-07-01"),
                    summary("P-002", "北京", TravelStyle.SOLO, "confirmed", "2026-07-02"));

            Map<String, Object> result = facade.getUserPlanHistory(
                    USER_ID, 1, 10, null, TravelStyle.COUPLE, null, null, null);

            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) result.get("items");
            assertThat(items).hasSize(1);
            assertThat(result.get("total")).isEqualTo(1L);
        }

        @Test
        @DisplayName("Redis 有完整摘要时，按 planStatus 筛选")
        void should_filter_by_plan_status_on_redis_path() throws JsonProcessingException {
            stubRedisSummaries(
                    summary("P-001", "云南", TravelStyle.COUPLE, "confirmed", "2026-07-01"),
                    summary("P-002", "北京", TravelStyle.SOLO, "failed", "2026-07-02"));

            Map<String, Object> result = facade.getUserPlanHistory(
                    USER_ID, 1, 10, null, null, "failed", null, null);

            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) result.get("items");
            assertThat(items).hasSize(1);
            assertThat(result.get("total")).isEqualTo(1L);
        }

        @Test
        @DisplayName("Redis 有完整摘要时，按 startDate/endDate 范围筛选")
        void should_filter_by_date_range_on_redis_path() throws JsonProcessingException {
            stubRedisSummaries(
                    summary("P-001", "云南", TravelStyle.COUPLE, "confirmed", "2026-06-15"),
                    summary("P-002", "北京", TravelStyle.SOLO, "confirmed", "2026-07-01"),
                    summary("P-003", "上海", TravelStyle.FAMILY, "confirmed", "2026-07-20"));

            Map<String, Object> result = facade.getUserPlanHistory(
                    USER_ID, 1, 10, null, null, null,
                    LocalDate.of(2026, 6, 20), LocalDate.of(2026, 7, 10));

            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) result.get("items");
            assertThat(items).hasSize(1);
            assertThat(result.get("total")).isEqualTo(1L);
        }

        @Test
        @DisplayName("Redis 摘要部分过期 → 降级到 MySQL 查询完整结果")
        void should_fallback_to_mysql_when_redis_summaries_incomplete()
                throws JsonProcessingException {
            // Redis ZSet 有 3 个 planId，但只有 2 个摘要可解析（第 3 个 expired）
            when(planCacheService.getUserPlanCount(UID_STR)).thenReturn(3L);
            when(planCacheService.getUserPlanIds(UID_STR, 0, 3))
                    .thenReturn(List.of("P-001", "P-002", "P-003"));
            when(planCacheService.getPlanSummary("P-001"))
                    .thenReturn(Optional.of(objectMapper.writeValueAsString(
                            summary("P-001", "云南", TravelStyle.COUPLE, "confirmed", "2026-07-01"))));
            when(planCacheService.getPlanSummary("P-002"))
                    .thenReturn(Optional.of(objectMapper.writeValueAsString(
                            summary("P-002", "北京", TravelStyle.SOLO, "confirmed", "2026-07-02"))));
            when(planCacheService.getPlanSummary("P-003")).thenReturn(Optional.empty());

            // MySQL fallback should be invoked
            com.smart.trip.common.dto.PlanSummary mysqlResult =
                    summary("P-003", "上海", TravelStyle.FAMILY, "confirmed", "2026-07-03");
            when(tripPlanPersistenceService.queryHistory(any()))
                    .thenReturn(List.of(
                            summary("P-001", "云南", TravelStyle.COUPLE, "confirmed", "2026-07-01"),
                            summary("P-002", "北京", TravelStyle.SOLO, "confirmed", "2026-07-02"),
                            mysqlResult));
            when(tripPlanPersistenceService.countHistory(any())).thenReturn(3L);

            Map<String, Object> result = facade.getUserPlanHistory(
                    USER_ID, 1, 10, null, null, null, null, null);

            assertThat(result.get("total")).isEqualTo(3L);
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) result.get("items");
            assertThat(items).hasSize(3);
        }

        @Test
        @DisplayName("MySQL 服务不可用（null）时，返回 Redis 部分结果（优雅降级）")
        void should_return_redis_partial_results_when_mysql_unavailable()
                throws JsonProcessingException {
            // 构造一个无 MySQL 的 facade
            TripPlanFacade noMysqlFacade = new TripPlanFacade(
                    planCacheService, supervisorAgent, objectMapper, agentMetrics,
                    ForkJoinPool.commonPool(), null, null, planIdSequenceService);

            // Redis 有 2 条，第 3 条过期
            when(planCacheService.getUserPlanCount(UID_STR)).thenReturn(3L);
            when(planCacheService.getUserPlanIds(UID_STR, 0, 3))
                    .thenReturn(List.of("P-001", "P-002", "P-003"));
            when(planCacheService.getPlanSummary("P-001"))
                    .thenReturn(Optional.of(objectMapper.writeValueAsString(
                            summary("P-001", "云南", TravelStyle.COUPLE, "confirmed", "2026-07-01"))));
            when(planCacheService.getPlanSummary("P-002"))
                    .thenReturn(Optional.of(objectMapper.writeValueAsString(
                            summary("P-002", "北京", TravelStyle.SOLO, "confirmed", "2026-07-02"))));
            when(planCacheService.getPlanSummary("P-003")).thenReturn(Optional.empty());

            Map<String, Object> result = noMysqlFacade.getUserPlanHistory(
                    USER_ID, 1, 10, null, null, null, null, null);

            // 优雅降级：仅返回 Redis 能解析的 2 条
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) result.get("items");
            assertThat(items).hasSize(2);
        }

        @Test
        @DisplayName("多条件组合筛选（keyword + travelStyle + planStatus）")
        void should_apply_combined_filters() throws JsonProcessingException {
            stubRedisSummaries(
                    summary("P-001", "云南丽江", TravelStyle.COUPLE, "confirmed", "2026-07-01"),
                    summary("P-002", "云南大理", TravelStyle.SOLO, "confirmed", "2026-07-02"),
                    summary("P-003", "云南昆明", TravelStyle.COUPLE, "failed", "2026-07-03"),
                    summary("P-004", "北京故宫", TravelStyle.COUPLE, "confirmed", "2026-07-04"));

            Map<String, Object> result = facade.getUserPlanHistory(
                    USER_ID, 1, 10, "云南", TravelStyle.COUPLE, "confirmed", null, null);

            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) result.get("items");
            assertThat(items).hasSize(1); // 仅 P-001 满足三个条件
        }

        @Test
        @DisplayName("MySQL 降级查询抛异常时返回空列表（不崩溃）")
        void should_return_empty_on_mysql_exception() {
            when(planCacheService.getUserPlanCount(UID_STR)).thenReturn(2L);
            when(planCacheService.getUserPlanIds(UID_STR, 0, 2))
                    .thenReturn(List.of("P-001", "P-002"));
            when(planCacheService.getPlanSummary("P-001")).thenReturn(Optional.empty());
            when(planCacheService.getPlanSummary("P-002")).thenReturn(Optional.empty());
            when(tripPlanPersistenceService.queryHistory(any()))
                    .thenThrow(new RuntimeException("MySQL unavailable"));

            Map<String, Object> result = facade.getUserPlanHistory(
                    USER_ID, 1, 10, null, null, null, null, null);

            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) result.get("items");
            assertThat(items).isEmpty();
            assertThat(result.get("total")).isEqualTo(0L);
        }

        @Test
        @DisplayName("所有筛选条件都满足时结果为空，返回空列表 + total=0")
        void should_return_empty_when_no_match() throws JsonProcessingException {
            stubRedisSummaries(
                    summary("P-001", "云南", TravelStyle.COUPLE, "confirmed", "2026-07-01"));

            Map<String, Object> result = facade.getUserPlanHistory(
                    USER_ID, 1, 10, "不存在的目的地", null, null, null, null);

            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) result.get("items");
            assertThat(items).isEmpty();
            assertThat(result.get("total")).isEqualTo(0L);
        }

        @Test
        @DisplayName("Redis 摘要 JSON 损坏（parseSummary 异常路径）→ 降级到 MySQL")
        void should_fallback_on_corrupt_summary_json() {
            when(planCacheService.getUserPlanCount(UID_STR)).thenReturn(1L);
            when(planCacheService.getUserPlanIds(UID_STR, 0, 1))
                    .thenReturn(List.of("P-001"));
            when(planCacheService.getPlanSummary("P-001"))
                    .thenReturn(Optional.of("{corrupt json}"));

            when(tripPlanPersistenceService.queryHistory(any()))
                    .thenReturn(List.of());
            when(tripPlanPersistenceService.countHistory(any())).thenReturn(0L);

            Map<String, Object> result = facade.getUserPlanHistory(
                    USER_ID, 1, 10, null, null, null, null, null);

            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) result.get("items");
            assertThat(items).isEmpty();
        }

        // ─── helpers ──────────────

        private void stubRedisSummaries(com.smart.trip.common.dto.PlanSummary... summaries)
                throws JsonProcessingException {
            List<String> planIds = new java.util.ArrayList<>();
            for (var s : summaries) {
                planIds.add(s.planId());
                when(planCacheService.getPlanSummary(s.planId()))
                        .thenReturn(Optional.of(objectMapper.writeValueAsString(s)));
            }
            when(planCacheService.getUserPlanCount(UID_STR)).thenReturn((long) summaries.length);
            when(planCacheService.getUserPlanIds(UID_STR, 0, summaries.length))
                    .thenReturn(planIds);
        }

        private static com.smart.trip.common.dto.PlanSummary summary(
                String planId, String dest, TravelStyle style, String status, String startDate) {
            return new com.smart.trip.common.dto.PlanSummary(
                    planId, dest, LocalDate.parse(startDate), 3,
                    BigDecimal.valueOf(5000), null, 2,
                    style != null ? style.name() : null,
                    status, System.currentTimeMillis());
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastStoredDetail() throws Exception {
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(planCacheService, timeout(5000).atLeast(2)).storePlan(anyString(), jsonCaptor.capture());
        List<String> storedJson = jsonCaptor.getAllValues();
        return new ObjectMapper().readValue(storedJson.get(storedJson.size() - 1), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static TripPlan sampleTripPlan(boolean requiresHITL) {
        TripPlanRequest request = validRequest();
        GeoLocation location = new GeoLocation(102.7, 25.0, "测试地址", "云南");
        Attraction attraction = new Attraction("A-001", "测试景点", location, 4.8,
                120, BigDecimal.valueOf(80), List.of("古镇"));
        Meal lunch = new Meal("午餐店", location, MealType.LUNCH,
                BigDecimal.valueOf(60), List.of("米线"));
        Meal dinner = new Meal("晚餐店", location, MealType.DINNER,
                BigDecimal.valueOf(80), List.of("汽锅鸡"));
        Route route = new Route(location, location, 3.2, 15, "驾车", false);
        TripDay day = new TripDay(request.startDate(), 1, List.of(attraction),
                List.of(lunch, dinner), List.of(route), BigDecimal.valueOf(300));
        Budget budget = new Budget(request.budget(), BigDecimal.valueOf(300),
                new BigDecimal("-0.9625"), new BudgetBreakdown(
                BigDecimal.valueOf(20), BigDecimal.valueOf(100),
                BigDecimal.valueOf(140), BigDecimal.valueOf(40)));
        return new TripPlan(PLAN_ID, request, List.of(day), BigDecimal.valueOf(300),
                budget, requiresHITL ? PlanStatus.REVIEW : PlanStatus.CONFIRMED, requiresHITL);
    }

    private static TripPlanRequest validRequest() {
        return new TripPlanRequest("云南", LocalDate.now(), 5,
                BigDecimal.valueOf(8000), 2, TravelStyle.COUPLE, null);
    }
}
