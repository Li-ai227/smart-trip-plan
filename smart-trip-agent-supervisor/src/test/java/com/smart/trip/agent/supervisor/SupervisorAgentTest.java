package com.smart.trip.agent.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.supervisor.SubAgentDispatcher;
import com.smart.trip.skills.supervisor.TripOrchestrationQuery;
import com.smart.trip.skills.supervisor.TripOrchestrationService;
import com.smart.trip.skills.supervisor.TripPlanResult;
import com.smart.trip.skills.xhs.AttractionCandidate;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SupervisorAgent} 单测（C-010 v2：AgentScope 2.0 对齐）。
 *
 * <p>覆盖：A2A 收发契约、原生/确定性双路接缝（4 例）、子 Agent 降级聚合、
 * HITL 多条件触发、编排顺序验证、构造参数校验。</p>
 */
class SupervisorAgentTest {

    private static final GeoLocation LOC = new GeoLocation(116.4, 39.9, "北京", "北京");
    private static final AgentMetrics METRICS = new AgentMetrics(new SimpleMeterRegistry());

    private static TripPlanRequest request() {
        return new TripPlanRequest("北京", LocalDate.of(2026, 7, 1), 2,
                new BigDecimal("5000"), 2, TravelStyle.COUPLE, List.of("古迹"));
    }

    private static TripPlanRequest requestWithBudget(String budget) {
        return new TripPlanRequest("北京", LocalDate.of(2026, 7, 1), 2,
                new BigDecimal(budget), 2, TravelStyle.COUPLE, List.of("古迹"));
    }

    private static Attraction attraction(String id) {
        return new Attraction(id, "景点" + id, LOC, 4.5, 120,
                new BigDecimal("50"), List.of("古迹"));
    }

    private static Meal meal(MealType type) {
        return new Meal("餐厅", LOC, type, new BigDecimal("50"), List.of());
    }

    private static TripDay tripDay(int index) {
        return new TripDay(LocalDate.of(2026, 7, index), index,
                List.of(attraction("A-" + index)),
                List.of(meal(MealType.LUNCH), meal(MealType.DINNER)),
                List.of(), new BigDecimal("500"));
    }

    private static SubAgentDispatcher happyDispatcher() {
        return (agentUri, assign) -> {
            Object result = switch (agentUri) {
                case "agent://xhs-analyst" -> new XHSAnalysisResult(
                        List.of(new AttractionCandidate(attraction("A-1"), 10, 8, 1, 1),
                                new AttractionCandidate(attraction("A-2"), 8, 6, 1, 1)),
                        false, null, null);
                case "agent://route-planner" -> new RoutePlanResult(
                        List.of(), List.of(), List.of(), false, null, null);
                case "agent://itinerary-designer" -> new ItineraryDesignResult(
                        List.of(tripDay(1), tripDay(2)), List.of(), false, null, null);
                case "agent://budget-controller" -> new BudgetCalculationResult(
                        new BigDecimal("900"), new BigDecimal("200"), new BigDecimal("400"),
                        new BigDecimal("200"), new BigDecimal("100"),
                        new BigDecimal("-0.02"), List.of(), false, null, List.of(), false, null);
                default -> null;
            };
            return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, false);
        };
    }

    /**
     * 构造一个子 Agent 全部降级的 dispatcher（每个子 Agent 返回 fallback=true）。
     */
    private static SubAgentDispatcher allFallbackDispatcher() {
        return (agentUri, assign) -> {
            Object result = switch (agentUri) {
                case "agent://xhs-analyst" -> new XHSAnalysisResult(
                        List.of(new AttractionCandidate(attraction("A-1"), 10, 8, 1, 1)),
                        true, "XHS 降级", null);
                case "agent://route-planner" -> new RoutePlanResult(
                        List.of(), List.of(), List.of(), true, "路线降级", null);
                case "agent://itinerary-designer" -> new ItineraryDesignResult(
                        List.of(tripDay(1), tripDay(2)), List.of(), true, "行程降级", null);
                case "agent://budget-controller" -> new BudgetCalculationResult(
                        new BigDecimal("900"), new BigDecimal("200"), new BigDecimal("400"),
                        new BigDecimal("200"), new BigDecimal("100"),
                        new BigDecimal("-0.02"), List.of(), false, null, List.of(), true, null);
                default -> null;
            };
            return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, true);
        };
    }

    private static TripOrchestrationService service() {
        return new TripOrchestrationService(happyDispatcher(), new OutputQualityGate());
    }

    private static TripOrchestrationService serviceWithDispatcher(SubAgentDispatcher dispatcher) {
        return new TripOrchestrationService(dispatcher, new OutputQualityGate());
    }

    private static Msg taskAssign(TripPlanRequest req) {
        return taskAssign(req, "T-test-001");
    }

    private static Msg taskAssign(TripPlanRequest req, String traceId) {
        Map<String, Object> context = Map.of("request", req);
        Map<String, Object> payload = Map.of(
                Msg.KEY_TASK_TYPE, "TRIP_PLAN",
                "context", context);
        return Msg.taskAssign("agent://server", "agent://supervisor", traceId, payload);
    }

    // --- 构造校验 ---

    @Nested
    @DisplayName("构造校验")
    class ConstructorValidation {

        @Test
        @DisplayName("orchestrationService 为 null → 抛 InvalidRequestException")
        void nullService() {
            assertThrows(InvalidRequestException.class,
                    () -> new SupervisorAgent(null, METRICS));
        }

        @Test
        @DisplayName("metrics 为 null → 抛 InvalidRequestException")
        void nullMetrics() {
            assertThrows(InvalidRequestException.class,
                    () -> new SupervisorAgent(service(), null));
        }
    }

    // --- A2A 契约 ---

    @Nested
    @DisplayName("A2A 契约")
    class A2AContract {

        @Test
        @DisplayName("agentUri 返回 agent://supervisor")
        void agentUri() {
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            assertEquals("agent://supervisor", agent.agentUri());
        }

        @Test
        @DisplayName("初始状态为 IDLE")
        void initialStatus() {
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            assertEquals(AgentStatus.IDLE, agent.status());
        }

        @Test
        @DisplayName("receive(null) → 抛 InvalidRequestException")
        void nullMsg() {
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            assertThrows(InvalidRequestException.class, () -> agent.receive(null));
        }

        @Test
        @DisplayName("投递 taskType=COST_CALCULATION → 快速失败 TASK_ERROR")
        void wrongTaskType() {
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            Map<String, Object> payload = Map.of(
                    Msg.KEY_TASK_TYPE, "COST_CALCULATION",
                    "context", Map.of("request", request()));
            Msg assign = Msg.taskAssign("agent://server", "agent://supervisor",
                    "T-test-001", payload);
            Msg response = agent.receive(assign);
            assertEquals(MessageType.TASK_ERROR, response.type());
        }

        @Test
        @DisplayName("context.request 缺失 → 快速失败 TASK_ERROR")
        void missingRequest() {
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            Map<String, Object> payload = Map.of(
                    Msg.KEY_TASK_TYPE, "TRIP_PLAN",
                    "context", Map.of());
            Msg assign = Msg.taskAssign("agent://server", "agent://supervisor",
                    "T-test-001", payload);
            Msg response = agent.receive(assign);
            assertEquals(MessageType.TASK_ERROR, response.type());
        }

        @Test
        @DisplayName("receive 非 TASK_ASSIGN 消息 → 抛 InvalidRequestException")
        void nonTaskAssign() {
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            Msg badMsg = Msg.taskResult("agent://x", "agent://y", "T-001", "result", false);
            assertThrows(InvalidRequestException.class, () -> agent.receive(badMsg));
        }
    }

    // --- Happy Path ---

    @Nested
    @DisplayName("Happy Path：确定性直路（无 runner）")
    class HappyPath {

        @Test
        @DisplayName("Case-1：北京 2 日游 → 完整 TripPlan，requiresHITL=false")
        void happyCase() {
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            Msg response = agent.receive(taskAssign(request()));

            assertEquals(MessageType.TASK_RESULT, response.type());
            assertFalse(response.isFallback());
            Object result = response.payload().get(Msg.KEY_RESULT);
            assertTrue(result instanceof TripPlanResult);
            TripPlanResult planResult = (TripPlanResult) result;
            assertNotNull(planResult.plan());
            assertEquals(2, planResult.plan().days().size());
            assertFalse(planResult.requiresHITL());
        }

        @Test
        @DisplayName("执行后状态为 DONE")
        void statusAfterExecution() {
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            agent.receive(taskAssign(request()));
            assertEquals(AgentStatus.DONE, agent.status());
        }

        @Test
        @DisplayName("编排顺序正确：XHS→Route→Itinerary→Budget→QualityGate 全链路")
        void orchestrationOrder() {
            // 记录 dispatcher 调用顺序
            List<String> callOrder = new java.util.ArrayList<>();
            SubAgentDispatcher orderTrackingDispatcher = (agentUri, assign) -> {
                callOrder.add(agentUri);
                return happyDispatcher().dispatch(agentUri, assign);
            };
            TripOrchestrationService svc = new TripOrchestrationService(
                    orderTrackingDispatcher, new OutputQualityGate());
            SupervisorAgent agent = new SupervisorAgent(svc, METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());

            // 验证顺序：XHS → Route → Itinerary → Budget
            assertEquals(4, callOrder.size());
            assertEquals("agent://xhs-analyst", callOrder.get(0));
            assertEquals("agent://route-planner", callOrder.get(1));
            assertEquals("agent://itinerary-designer", callOrder.get(2));
            assertEquals("agent://budget-controller", callOrder.get(3));
        }
    }

    // --- 原生接缝 4 例 ---

    @Nested
    @DisplayName("原生接缝：4 例降级测试")
    class NativeSeam {

        @Test
        @DisplayName("① 注入成功桩 runner → 原生产物返回，fallback=false")
        void successRunner() {
            TripPlanResult stubResult = service().orchestrate(
                    new TripOrchestrationQuery(request()));
            // C-012-C：显式标记 PATH_NATIVE，避免因 stub 来自 orchestrate() 被误判为 fallback
            TripPlanResult nativeStub = new TripPlanResult(
                    stubResult.plan(), stubResult.requiresHITL(), stubResult.hitlReasons(),
                    stubResult.fallback(), stubResult.telemetry(), TripPlanResult.PATH_NATIVE, false);
            SupervisorAgent agent = new SupervisorAgent(
                    q -> nativeStub, service(), METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            assertFalse(response.isFallback());
        }

        @Test
        @DisplayName("② 注入异常桩 runner → 降级确定性 + fallback=true")
        void exceptionRunner() {
            SupervisorAgent agent = new SupervisorAgent(
                    q -> { throw new RuntimeException("LLM 超时"); },
                    service(), METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            assertTrue(response.isFallback());
        }

        @Test
        @DisplayName("③ 注入 null-result 桩 runner → 降级确定性 + fallback=true")
        void nullResultRunner() {
            SupervisorAgent agent = new SupervisorAgent(
                    q -> null, service(), METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            assertTrue(response.isFallback());
        }

        @Test
        @DisplayName("④ runner 为 null → 纯确定性直路（无 LLM 调用）")
        void nullRunner() {
            SupervisorAgent agent = new SupervisorAgent(null, service(), METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            assertFalse(response.isFallback());
            Object result = response.payload().get(Msg.KEY_RESULT);
            assertTrue(result instanceof TripPlanResult);
        }

        @Test
        @DisplayName("⑤ 原生路 LLM 未调 orchestrate_trip（lastResult=null）→ 降级确定性兜底 + fallback=true")
        void llmDidNotCallOrchestrateTrip() {
            // runner 返回 null 模拟 LLM 未调 orchestrate_trip 工具
            SupervisorAgent agent = new SupervisorAgent(
                    q -> null, service(), METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            assertTrue(response.isFallback());
            // 确定性兜底仍产出完整方案
            Object result = response.payload().get(Msg.KEY_RESULT);
            assertTrue(result instanceof TripPlanResult);
            assertNotNull(((TripPlanResult) result).plan());
        }

        @Test
        @DisplayName("⑥ 成功桩 runner → orchestrate() 未被调用 + path=PATH_NATIVE + fallback=false")
        void successRunnerOrchestrateNotCalled() {
            // Arrange: 通过 SubAgentDispatcher 计数来检测是否触发了降级路径
            AtomicInteger dispatchCount = new AtomicInteger(0);
            SubAgentDispatcher countingDispatcher = (agentUri, assign) -> {
                dispatchCount.incrementAndGet();
                return happyDispatcher().dispatch(agentUri, assign);
            };
            TripOrchestrationService spyService = new TripOrchestrationService(
                    countingDispatcher, new OutputQualityGate());

            // 创建 native stub，由 spyService 构建但重置计数器
            TripPlanResult stubResult = spyService.orchestrate(
                    new TripOrchestrationQuery(request()));
            TripPlanResult nativeStub = new TripPlanResult(
                    stubResult.plan(), stubResult.requiresHITL(), stubResult.hitlReasons(),
                    stubResult.fallback(), stubResult.telemetry(), TripPlanResult.PATH_NATIVE, false);
            dispatchCount.set(0); // 重置

            SupervisorAgent agent = new SupervisorAgent(q -> nativeStub, spyService, METRICS);

            // Act
            Msg response = agent.receive(taskAssign(request()));

            // Assert
            assertEquals(MessageType.TASK_RESULT, response.type());
            assertFalse(response.isFallback());

            TripPlanResult result = (TripPlanResult) response.payload().get(Msg.KEY_RESULT);
            assertEquals(TripPlanResult.PATH_NATIVE, result.path(),
                    "原生路成功时应返回 PATH_NATIVE");

            assertEquals(0, dispatchCount.get(),
                    "原生路 runner 返回非 null 后不应触发降级子 Agent 分发");
        }

        @Test
        @DisplayName("⑦ null-result 桩 runner → 降级触发恰好 4 次子 Agent 分发 + fallback=true")
        void nullResultTriggersExactlyFourDispatches() {
            AtomicInteger dispatchCount = new AtomicInteger(0);
            SubAgentDispatcher countingDispatcher = (agentUri, assign) -> {
                dispatchCount.incrementAndGet();
                return happyDispatcher().dispatch(agentUri, assign);
            };
            TripOrchestrationService spyService = new TripOrchestrationService(
                    countingDispatcher, new OutputQualityGate());

            SupervisorAgent agent = new SupervisorAgent(q -> null, spyService, METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            assertTrue(response.isFallback());
            // 确定性路径会分发 4 个子 Agent
            assertEquals(4, dispatchCount.get(),
                    "null-result runner 应触发恰好 4 次子 Agent 分发（XHS/Route/Itinerary/Budget）");
        }

        @Test
        @DisplayName("⑧ 异常桩 runner → 降级触发恰好 4 次子 Agent 分发 + fallback=true")
        void exceptionTriggersExactlyFourDispatches() {
            AtomicInteger dispatchCount = new AtomicInteger(0);
            SubAgentDispatcher countingDispatcher = (agentUri, assign) -> {
                dispatchCount.incrementAndGet();
                return happyDispatcher().dispatch(agentUri, assign);
            };
            TripOrchestrationService spyService = new TripOrchestrationService(
                    countingDispatcher, new OutputQualityGate());

            SupervisorAgent agent = new SupervisorAgent(
                    q -> { throw new RuntimeException("LLM 超时"); },
                    spyService, METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            assertTrue(response.isFallback());
            assertEquals(4, dispatchCount.get(),
                    "异常 runner 应触发恰好 4 次子 Agent 分发（XHS/Route/Itinerary/Budget）");
        }
    }

    // --- 子 Agent 降级聚合 ---

    @Nested
    @DisplayName("子 Agent 降级聚合：部分/全部降级整体不崩")
    class SubAgentDegradation {

        @Test
        @DisplayName("全部子 Agent fallback=true → 整体标记降级仍产出 TripPlan")
        void allSubAgentsFallback() {
            TripOrchestrationService svc = new TripOrchestrationService(
                    allFallbackDispatcher(), new OutputQualityGate());
            SupervisorAgent agent = new SupervisorAgent(svc, METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            assertTrue(response.isFallback());
            Object result = response.payload().get(Msg.KEY_RESULT);
            assertTrue(result instanceof TripPlanResult);
            TripPlanResult planResult = (TripPlanResult) result;
            assertNotNull(planResult.plan());
            // fallback 标记应由 orchestration 聚合
            assertTrue(planResult.fallback());
        }

        @Test
        @DisplayName("仅 XHS 降级 → 整体仍产出 + fallback=true")
        void onlyXhsFallback() {
            SubAgentDispatcher dispatcher = (agentUri, assign) -> {
                if ("agent://xhs-analyst".equals(agentUri)) {
                    XHSAnalysisResult degraded = new XHSAnalysisResult(
                            List.of(new AttractionCandidate(attraction("A-1"), 10, 8, 1, 1)),
                            true, "XHS API 降级", null);
                    return Msg.taskResult(agentUri, assign.from(), assign.traceId(), degraded, true);
                }
                return happyDispatcher().dispatch(agentUri, assign);
            };
            TripOrchestrationService svc = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            SupervisorAgent agent = new SupervisorAgent(svc, METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            assertTrue(response.isFallback());
            TripPlanResult planResult = (TripPlanResult) response.payload().get(Msg.KEY_RESULT);
            assertNotNull(planResult.plan());
            assertTrue(planResult.fallback());
        }

        @Test
        @DisplayName("All 子 Agent TASK_ERROR → 仍产出 TripPlan（空 days）+ fallback=true")
        void allSubAgentsError() {
            SubAgentDispatcher errorDispatcher = (agentUri, assign) ->
                    Msg.taskError(agentUri, assign.from(), assign.traceId(),
                            "AGENT_FAILURE", "模拟 " + agentUri + " 故障");
            TripOrchestrationService svc = new TripOrchestrationService(
                    errorDispatcher, new OutputQualityGate());
            SupervisorAgent agent = new SupervisorAgent(svc, METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            TripPlanResult planResult = (TripPlanResult) response.payload().get(Msg.KEY_RESULT);
            assertNotNull(planResult.plan());
            assertTrue(planResult.fallback());
        }
    }

    // --- HITL 多条件触发 ---

    @Nested
    @DisplayName("HITL 多条件触发")
    class HITLTrigger {

        @Test
        @DisplayName("超预算 ≥15% → requiresHITL=true + 原因含 BUDGET_OVERRUN")
        void budgetOverrun() {
            SubAgentDispatcher dispatcher = (agentUri, assign) -> {
                Object result = switch (agentUri) {
                    case "agent://xhs-analyst" -> new XHSAnalysisResult(
                            List.of(new AttractionCandidate(attraction("A-1"), 10, 8, 1, 1)),
                            false, null, null);
                    case "agent://route-planner" -> new RoutePlanResult(
                            List.of(), List.of(), List.of(), false, null, null);
                    case "agent://itinerary-designer" -> new ItineraryDesignResult(
                            List.of(tripDay(1), tripDay(2)), List.of(), false, null, null);
                    case "agent://budget-controller" -> new BudgetCalculationResult(
                            new BigDecimal("6000"), new BigDecimal("1500"), new BigDecimal("2000"),
                            new BigDecimal("1500"), new BigDecimal("1000"),
                            new BigDecimal("0.20"), List.of(), true,
                            "超支20%，超过15%阈值", List.of("降档住宿", "减少餐饮"), false, null);
                    default -> null;
                };
                return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, false);
            };
            TripOrchestrationService svc = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            SupervisorAgent agent = new SupervisorAgent(svc, METRICS);

            Msg response = agent.receive(taskAssign(requestWithBudget("5000")));
            assertEquals(MessageType.TASK_RESULT, response.type());
            TripPlanResult planResult = (TripPlanResult) response.payload().get(Msg.KEY_RESULT);
            assertTrue(planResult.requiresHITL());
            assertFalse(planResult.hitlReasons().isEmpty());
            assertTrue(planResult.hitlReasons().stream()
                    .anyMatch(r -> r.contains("BUDGET_OVERRUN") || r.contains("超预算")));
        }

        @Test
        @DisplayName("天数不一致 → requiresHITL=true + 原因含 DAYS_MISMATCH")
        void daysMismatch() {
            SubAgentDispatcher dispatcher = (agentUri, assign) -> {
                Object result = switch (agentUri) {
                    case "agent://xhs-analyst" -> new XHSAnalysisResult(
                            List.of(new AttractionCandidate(attraction("A-1"), 10, 8, 1, 1)),
                            false, null, null);
                    case "agent://route-planner" -> new RoutePlanResult(
                            List.of(), List.of(), List.of(), false, null, null);
                    // 只产出 1 天，但请求是 2 天
                    case "agent://itinerary-designer" -> new ItineraryDesignResult(
                            List.of(tripDay(1)), List.of(), false, null, null);
                    case "agent://budget-controller" -> new BudgetCalculationResult(
                            new BigDecimal("450"), new BigDecimal("100"), new BigDecimal("200"),
                            new BigDecimal("100"), new BigDecimal("50"),
                            new BigDecimal("-0.10"), List.of(), false, null, List.of(), false, null);
                    default -> null;
                };
                return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, false);
            };
            TripOrchestrationService svc = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            SupervisorAgent agent = new SupervisorAgent(svc, METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            TripPlanResult planResult = (TripPlanResult) response.payload().get(Msg.KEY_RESULT);
            assertTrue(planResult.requiresHITL());
            assertTrue(planResult.hitlReasons().stream()
                    .anyMatch(r -> r.contains("DAYS_MISMATCH") || r.contains("天数不一致")));
        }

        @Test
        @DisplayName("超预算 + 天数不一致同时触发 → 两条原因均记录")
        void multipleHITLReasons() {
            SubAgentDispatcher dispatcher = (agentUri, assign) -> {
                Object result = switch (agentUri) {
                    case "agent://xhs-analyst" -> new XHSAnalysisResult(
                            List.of(new AttractionCandidate(attraction("A-1"), 10, 8, 1, 1)),
                            false, null, null);
                    case "agent://route-planner" -> new RoutePlanResult(
                            List.of(), List.of(), List.of(), false, null, null);
                    // 只产出 1 天 + 评分低
                    case "agent://itinerary-designer" -> new ItineraryDesignResult(
                            List.of(new TripDay(LocalDate.of(2026, 7, 1), 1,
                                    List.of(new Attraction("bad", "低分景点", LOC, 3.0, 60,
                                            BigDecimal.ZERO, List.of())),
                                    List.of(meal(MealType.LUNCH), meal(MealType.DINNER)),
                                    List.of(), new BigDecimal("500"))),
                            List.of(), false, null, null);
                    case "agent://budget-controller" -> new BudgetCalculationResult(
                            new BigDecimal("6000"), new BigDecimal("1500"), new BigDecimal("2000"),
                            new BigDecimal("1500"), new BigDecimal("1000"),
                            new BigDecimal("0.20"), List.of(), true,
                            "超支20%", List.of("降档住宿"), false, null);
                    default -> null;
                };
                return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, false);
            };
            TripOrchestrationService svc = new TripOrchestrationService(
                    dispatcher, new OutputQualityGate());
            SupervisorAgent agent = new SupervisorAgent(svc, METRICS);

            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
            TripPlanResult planResult = (TripPlanResult) response.payload().get(Msg.KEY_RESULT);
            assertTrue(planResult.requiresHITL());
            // 至少触发 2 条违规
            assertTrue(planResult.hitlReasons().size() >= 2,
                    "应至少触发超预算 + 天数不一致/评分低共 2+ 条 HITL 原因，实际: " + planResult.hitlReasons());
        }
    }

    // --- TripPlanResult 结构完整性 ---

    @Nested
    @DisplayName("TripPlanResult 结构完整性")
    class ResultStructure {

        @Test
        @DisplayName("产出含 planId、days、totalCost、budget、requiresHITL、fallback、telemetry")
        void completeStructure() {
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            Msg response = agent.receive(taskAssign(request()));

            TripPlanResult result = (TripPlanResult) response.payload().get(Msg.KEY_RESULT);
            assertNotNull(result.plan());
            assertNotNull(result.plan().planId());
            assertFalse(result.plan().planId().isBlank());
            assertNotNull(result.plan().days());
            assertNotNull(result.plan().totalCost());
            assertNotNull(result.plan().budget());
            assertNotNull(result.telemetry());
            assertTrue(result.telemetry().totalLatencyMs() >= 0);
        }

        @Test
        @DisplayName("traceId 在 TASK_RESULT 中透传")
        void traceIdPassthrough() {
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            Msg assign = taskAssign(request());
            Msg response = agent.receive(assign);

            assertEquals(assign.traceId(), response.traceId());
        }
    }

    // --- Trace 审计集成 ---

    @Nested
    @DisplayName("Trace 审计集成：TracePersistenceService 接缝")
    class TraceIntegration {

        /** 捕获式 TracePersistenceService：记录每次调用的 record，供断言 traceId 是否真实透传。 */
        private final class CapturingTracePersistenceService
                implements com.smart.trip.skills.trace.TracePersistenceService {
            final java.util.List<com.smart.trip.skills.trace.TraceDelegationRecord> delegations =
                    new java.util.ArrayList<>();
            final java.util.List<com.smart.trip.skills.trace.TraceOrchestrationSummary> summaries =
                    new java.util.ArrayList<>();
            final java.util.List<com.smart.trip.skills.trace.TraceLlmCallRecord> llmCalls =
                    new java.util.ArrayList<>();

            @Override
            public void saveDelegation(com.smart.trip.skills.trace.TraceDelegationRecord r) {
                delegations.add(r);
            }

            @Override
            public void saveLlmCall(com.smart.trip.skills.trace.TraceLlmCallRecord r) {
                llmCalls.add(r);
            }

            @Override
            public void saveOrchestrationSummary(
                    com.smart.trip.skills.trace.TraceOrchestrationSummary s) {
                summaries.add(s);
            }
        }

        @Test
        @DisplayName("C-017 FIX-3: 纯确定性直路 → summary 使用请求真实 traceId（不再是硬编码 \"\"）")
        void tracePersistenceDoesNotBreakMainFlow() {
            CapturingTracePersistenceService stubTrace = new CapturingTracePersistenceService();
            // 生产环境 PlanConfig 会把同一个 tracePersistence 同时注入 TripOrchestrationService
            // （委派记录）和 SupervisorAgent（summary/llmCall），此处沿用相同装配方式。
            TripOrchestrationService serviceWithTrace =
                    new TripOrchestrationService(happyDispatcher(), new OutputQualityGate(), stubTrace);
            SupervisorAgent agent = new SupervisorAgent(null, serviceWithTrace, METRICS, stubTrace);

            Msg assign = taskAssign(request());
            Msg response = agent.receive(assign);
            assertEquals(MessageType.TASK_RESULT, response.type());
            Object result = response.payload().get(Msg.KEY_RESULT);
            assertTrue(result instanceof TripPlanResult);

            assertEquals(1, stubTrace.summaries.size(), "SupervisorAgent 应恰好写入一条 summary");
            assertEquals(assign.traceId(), stubTrace.summaries.get(0).traceId(),
                    "summary 的 traceId 应等于本次请求的真实 traceId，不再是硬编码 \"\"");
            assertEquals(4, stubTrace.delegations.size(),
                    "确定性直路应产出 4 条委派记录（XHS/Route/Itinerary/Budget）");
            stubTrace.delegations.forEach(d -> assertEquals(assign.traceId(), d.traceId(),
                    "委派记录应与 summary 共用同一个 traceId，便于关联"));
        }

        @Test
        @DisplayName("C-017 FIX-3: 原生路 → summary/llmCall 使用请求真实 traceId")
        void nativePathWithTrace() {
            CapturingTracePersistenceService stubTrace = new CapturingTracePersistenceService();
            TripPlanResult stubResult = service().orchestrate(
                    new TripOrchestrationQuery(request()));
            TripPlanResult nativeStub = new TripPlanResult(
                    stubResult.plan(), stubResult.requiresHITL(), stubResult.hitlReasons(),
                    stubResult.fallback(), stubResult.telemetry(), TripPlanResult.PATH_NATIVE, false);
            SupervisorAgent agent = new SupervisorAgent(
                    q -> nativeStub, service(), METRICS, stubTrace);

            Msg assign = taskAssign(request());
            Msg response = agent.receive(assign);
            assertEquals(MessageType.TASK_RESULT, response.type());

            assertEquals(1, stubTrace.summaries.size());
            assertEquals(assign.traceId(), stubTrace.summaries.get(0).traceId());
            assertEquals(1, stubTrace.llmCalls.size());
            assertEquals(assign.traceId(), stubTrace.llmCalls.get(0).traceId());
        }

        @Test
        @DisplayName("C-017 FIX-3: 连续两次不同 traceId 的请求 → summary 均成功记录各自的真实 traceId")
        void repeatedRequestsEachKeepOwnTraceId() {
            CapturingTracePersistenceService stubTrace = new CapturingTracePersistenceService();
            SupervisorAgent agent = new SupervisorAgent(null, service(), METRICS, stubTrace);

            agent.receive(taskAssign(request(), "T-req-001"));
            agent.receive(taskAssign(request(), "T-req-002"));

            assertEquals(2, stubTrace.summaries.size(),
                    "修复前硬编码 \"\" 会导致第二次因 UNIQUE 冲突而丢失；修复后两次都应记录");
            assertEquals("T-req-001", stubTrace.summaries.get(0).traceId());
            assertEquals("T-req-002", stubTrace.summaries.get(1).traceId());
        }

        @Test
        @DisplayName("null tracePersistence（默认构造器）→ 不抛 NPE")
        void nullTracePersistence() {
            // 2-arg 构造器内部传 null tracePersistence
            SupervisorAgent agent = new SupervisorAgent(service(), METRICS);
            assertNotNull(agent);
        }

        @Test
        @DisplayName("3-arg 构造器（runner + service + metrics）→ tracePersistence=null 不抛异常")
        void defaultConstructorNullTrace() {
            SupervisorAgent agent = new SupervisorAgent(null, service(), METRICS);
            Msg response = agent.receive(taskAssign(request()));
            assertEquals(MessageType.TASK_RESULT, response.type());
        }
    }
}
