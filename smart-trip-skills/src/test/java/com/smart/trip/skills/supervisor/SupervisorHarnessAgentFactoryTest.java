package com.smart.trip.skills.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.agent.RuntimeContext;

import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.xhs.AttractionCandidate;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link SupervisorHarnessAgentFactory} 单测（C-010 v2 + C-012-C）。
 *
 * <p>仅覆盖可离线单测部分：构造参数校验。真实 LLM 调用归 env-gated 集成测试（JaCoCo 排除）。</p>
 */
class SupervisorHarnessAgentFactoryTest {

    @TempDir
    Path tempDir;

    private static TripOrchestrationService stubService() {
        SubAgentDispatcher stubDispatcher = (agentUri, assign) -> {
            Object result = switch (agentUri) {
                case "agent://xhs-analyst" -> new XHSAnalysisResult(
                        List.of(), false, null, null);
                case "agent://route-planner" -> new RoutePlanResult(
                        List.of(), List.of(), List.of(), false, null, null);
                case "agent://itinerary-designer" -> new ItineraryDesignResult(
                        List.of(), List.of(), false, null, null);
                case "agent://budget-controller" -> new BudgetCalculationResult(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, List.of(), false, null, List.of(), false, null);
                default -> null;
            };
            return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, false);
        };
        return new TripOrchestrationService(stubDispatcher, new OutputQualityGate());
    }

    private static OutputQualityGate stubQualityGate() {
        return new OutputQualityGate();
    }

    private static SubAgentToolFactory stubToolFactory() {
        return (runContext) -> List.of();
    }

    @Nested
    @DisplayName("构造参数校验（null 拒绝）")
    class ConstructorValidation {

        @Test
        @DisplayName("primaryModel 为 null → 抛 IllegalArgumentException")
        void nullPrimaryModel() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SupervisorHarnessAgentFactory(
                            null, null, stubService(), stubToolFactory(),
                            stubQualityGate(), tempDir, null));
        }

        @Test
        @DisplayName("service 为 null → 抛 IllegalArgumentException")
        void nullService() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SupervisorHarnessAgentFactory(
                            fakeModel(), null, null, stubToolFactory(),
                            stubQualityGate(), tempDir, null));
        }

        @Test
        @DisplayName("toolFactory 为 null → 抛 IllegalArgumentException")
        void nullToolFactory() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SupervisorHarnessAgentFactory(
                            fakeModel(), null, stubService(), null,
                            stubQualityGate(), tempDir, null));
        }

        @Test
        @DisplayName("qualityGate 为 null → 抛 IllegalArgumentException")
        void nullQualityGate() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SupervisorHarnessAgentFactory(
                            fakeModel(), null, stubService(), stubToolFactory(),
                            null, tempDir, null));
        }

        @Test
        @DisplayName("workspace 为 null → 抛 IllegalArgumentException")
        void nullWorkspace() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SupervisorHarnessAgentFactory(
                            fakeModel(), null, stubService(), stubToolFactory(),
                            stubQualityGate(), null, null));
        }
    }

    @Nested
    @DisplayName("per-run workspace 隔离（C-workspace-isolation）")
    class PerRunWorkspaceIsolation {

        @Test
        @DisplayName("两次不同 planId 产生不同 workspace 路径")
        void differentPlanIdsProduceDifferentWorkspaces() {
            SupervisorHarnessAgentFactory factory = new SupervisorHarnessAgentFactory(
                    fakeModel(), null, stubService(), stubToolFactory(),
                    stubQualityGate(), tempDir, null);

            Path ws1 = factory.resolveRunWorkspace("P-20260621-001");
            Path ws2 = factory.resolveRunWorkspace("P-20260621-002");

            assertNotEquals(ws1, ws2);
        }

        @Test
        @DisplayName("resolveRunWorkspace 返回基础路径的 planId 子目录且尚未创建")
        void runWorkspaceIsSubdirOfBaseAndNotYetCreated() {
            SupervisorHarnessAgentFactory factory = new SupervisorHarnessAgentFactory(
                    fakeModel(), null, stubService(), stubToolFactory(),
                    stubQualityGate(), tempDir, null);

            String planId = "P-20260621-TEST";
            Path runWorkspace = factory.resolveRunWorkspace(planId);

            assertTrue(runWorkspace.startsWith(tempDir), "runWorkspace 必须是基础路径的子目录");
            assertEquals(planId, runWorkspace.getFileName().toString(), "末级目录名应为 planId");
            assertTrue(Files.notExists(runWorkspace), "run() 前 workspace 不应预先存在");
        }

        @Test
        @DisplayName("相同 planId 两次调用返回相同路径（幂等）")
        void samePlanIdProducesSamePath() {
            SupervisorHarnessAgentFactory factory = new SupervisorHarnessAgentFactory(
                    fakeModel(), null, stubService(), stubToolFactory(),
                    stubQualityGate(), tempDir, null);

            String planId = "P-20260621-SAME";
            Path ws1 = factory.resolveRunWorkspace(planId);
            Path ws2 = factory.resolveRunWorkspace(planId);

            assertEquals(ws1, ws2);
        }
    }

    @Nested
    @DisplayName("Prompt 约束：禁 Plan Mode、强制 XHS-first（C-012-fix）")
    class PromptConstraints {

        @Test
        @DisplayName("SYS_PROMPT 不推荐使用 Plan Mode（防 LLM 读源码迷失）")
        void sysPromptMustNotRecommendPlanMode() {
            String prompt = SupervisorHarnessAgentFactory.SYS_PROMPT;
            assertFalse(prompt.contains("使用 Plan Mode") || prompt.contains("可使用 Plan Mode"),
                    "SYS_PROMPT 含有 Plan Mode 推荐语，LLM 会进入文件读取模式导致原生路失败");
            // 应该是禁止，而非推荐
            assertTrue(prompt.contains("禁止"),
                    "SYS_PROMPT 缺少禁止性约束");
        }

        @Test
        @DisplayName("SYS_PROMPT 禁止文件工具（含 read_file/glob_files/grep_files 等关键词）")
        void sysPromptMustProhibitFileTools() {
            String prompt = SupervisorHarnessAgentFactory.SYS_PROMPT;
            assertTrue(prompt.contains("禁止") || prompt.contains("read_file"),
                    "SYS_PROMPT 应明确禁止 LLM 使用文件工具");
        }

        @Test
        @DisplayName("SYS_PROMPT xhs-analyst 顺序在 route-planner 之前（XHS-first 强制）")
        void sysPromptXhsBeforeRoute() {
            String prompt = SupervisorHarnessAgentFactory.SYS_PROMPT;
            int xhsIdx = prompt.indexOf("xhs-analyst");
            int routeIdx = prompt.indexOf("route-planner");
            assertTrue(xhsIdx >= 0 && routeIdx >= 0 && xhsIdx < routeIdx,
                    "SYS_PROMPT 中 xhs-analyst 必须出现在 route-planner 之前");
        }

        @Test
        @DisplayName("buildPrompt 不含 Plan Mode 关键词")
        void buildPromptMustNotMentionPlanMode() {
            String text = SupervisorHarnessAgentFactory.buildPrompt(stubQuery()).getTextContent();
            assertFalse(text.contains("Plan Mode"),
                    "buildPrompt 含有 'Plan Mode'，会触发 LLM 读取文件");
        }

        @Test
        @DisplayName("buildPrompt xhs-analyst 步骤在 route-planner 之前")
        void buildPromptXhsBeforeRoute() {
            String text = SupervisorHarnessAgentFactory.buildPrompt(stubQuery()).getTextContent();
            int xhsIdx = text.indexOf("xhs-analyst");
            int routeIdx = text.indexOf("route-planner");
            assertTrue(xhsIdx >= 0 && routeIdx >= 0 && xhsIdx < routeIdx,
                    "buildPrompt 中 xhs-analyst 必须出现在 route-planner 之前");
        }

        @Test
        @DisplayName("SYS_PROMPT 明确指定 attractions_json 为 candidates JSON 数组格式（C-013-B）")
        void sysPromptSpecifiesAttractionsJsonFormat() {
            String prompt = SupervisorHarnessAgentFactory.SYS_PROMPT;
            assertTrue(prompt.contains("candidates") && prompt.contains("attractions_json"),
                    "SYS_PROMPT 应同时包含 'candidates' 和 'attractions_json'，指导 LLM 正确传参");
        }

        @Test
        @DisplayName("buildPrompt 明确指定 attractions_json 为 candidates JSON 数组格式（C-013-B）")
        void buildPromptSpecifiesAttractionsJsonFormat() {
            String text = SupervisorHarnessAgentFactory.buildPrompt(stubQuery()).getTextContent();
            assertTrue(text.contains("candidates") && text.contains("attractions_json"),
                    "buildPrompt 应同时包含 'candidates' 和 'attractions_json'，指导 LLM 正确传参");
        }

        @Test
        @DisplayName("SYS_PROMPT 步骤①要求调用 analyze_xhs_notes 工具（防 xhs-analyst 返回文本）")
        void sysPromptStep1RequiresAnalyzeXhsNotesTool() {
            String prompt = SupervisorHarnessAgentFactory.SYS_PROMPT;
            assertTrue(prompt.contains("analyze_xhs_notes"),
                    "SYS_PROMPT 应包含 'analyze_xhs_notes'，明确要求 xhs-analyst 调工具而非返回文本");
            assertTrue(prompt.indexOf("analyze_xhs_notes") < prompt.indexOf("route-planner"),
                    "SYS_PROMPT 中 analyze_xhs_notes 引用应出现在步骤①（xhs-analyst）中，早于 route-planner");
        }

        @Test
        @DisplayName("SYS_PROMPT 步骤④要求调用 calculate_budget 工具（防 budget-controller 返回文本）")
        void sysPromptStep4RequiresCalculateBudgetTool() {
            String prompt = SupervisorHarnessAgentFactory.SYS_PROMPT;
            assertTrue(prompt.contains("calculate_budget"),
                    "SYS_PROMPT 应包含 'calculate_budget'，明确要求 budget-controller 调工具而非返回文本");
        }

        @Test
        @DisplayName("buildPrompt 步骤1要求调用 analyze_xhs_notes 工具")
        void buildPromptStep1RequiresAnalyzeXhsNotesTool() {
            String text = SupervisorHarnessAgentFactory.buildPrompt(stubQuery()).getTextContent();
            assertTrue(text.contains("analyze_xhs_notes"),
                    "buildPrompt 应包含 'analyze_xhs_notes'，明确要求 xhs-analyst 调工具");
        }

        @Test
        @DisplayName("buildPrompt 步骤4要求调用 calculate_budget 工具")
        void buildPromptStep4RequiresCalculateBudgetTool() {
            String text = SupervisorHarnessAgentFactory.buildPrompt(stubQuery()).getTextContent();
            assertTrue(text.contains("calculate_budget"),
                    "buildPrompt 应包含 'calculate_budget'，明确要求 budget-controller 调工具");
        }

        @Test
        @DisplayName("SYS_PROMPT 包含通信约束与极简状态指引（提速优化）")
        void sysPromptContainsCompactCommunicationConstraint() {
            String prompt = SupervisorHarnessAgentFactory.SYS_PROMPT;
            assertTrue(prompt.contains("通信约束") && prompt.contains("极简通信协议"),
                    "SYS_PROMPT 应包含极简通信协议约束以降低响应延迟");
        }

        @Test
        @DisplayName("四个子 Agent 声明文件均包含通信约束与一句话极简回复规范")
        void subagentsContainCompactCommunicationConstraint() throws Exception {
            String[] files = {
                    "xhs-analyst.md",
                    "route-planner.md",
                    "itinerary-designer.md",
                    "budget-controller.md"
            };
            for (String file : files) {
                try (var is = getClass().getClassLoader().getResourceAsStream("subagents/" + file)) {
                    assertNotNull(is, "subagents/" + file + " 必须存在于 classpath");
                    String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    assertTrue(content.contains("【通信约束】"),
                            file + " 缺少【通信约束】，必须限制子 Agent 输出一句话极简状态以减少推理耗时");
                }
            }
        }

        private static TripOrchestrationQuery stubQuery() {
            return new TripOrchestrationQuery(
                    new com.smart.trip.common.dto.TripPlanRequest(
                            "北京",
                            java.time.LocalDate.of(2026, 7, 1),
                            5,
                            new java.math.BigDecimal("5000"),
                            2, null, List.of()));
        }
    }

    @Nested
    @DisplayName("C-012-fix: 原生路超时数据流完整性检查（C-013-timeout-dataflow）")
    class DataFlowIntegrity {

        private static final GeoLocation LOC = new GeoLocation(116.4, 39.9, "北京市东城区", "北京");

        private static Attraction attraction(String id, String name, double rating) {
            return new Attraction(id, name, LOC, rating, 120,
                    BigDecimal.ZERO, List.of());
        }

        @Test
        @DisplayName("XHS 有候选但 plan days 为空 → 数据流破坏，应触发降级")
        void xhsCandidatesNonEmptyButPlanDaysEmptyShouldDetectBrokenFlow() {
            SupervisorNativeRunContext ctx = new SupervisorNativeRunContext();
            // 模拟 XHS 工具成功产出候选（如 agent_spawn 收尾超时前已写入）
            ctx.setXhsResult(new XHSAnalysisResult(
                    List.of(new AttractionCandidate(
                            attraction("BJ001", "故宫", 4.8), 10, 7, 2, 1)),
                    false, null, null));
            // 模拟下游因 LLM 传空数组而产出空结果
            ctx.setRouteResult(new RoutePlanResult(
                    List.of(), List.of(), List.of(), false, null, null));
            ctx.setItineraryResult(new ItineraryDesignResult(
                    List.of(), List.of(), false, null, null));
            ctx.setBudgetResult(new BudgetCalculationResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of(), false, null, List.of(), false, null));

            TripPlanRequest request = new TripPlanRequest(
                    "北京", LocalDate.of(2026, 7, 1), 5,
                    new BigDecimal("8000"), 3, null, List.of("古镇", "美食"));
            TripPlan plan = TripPlanAssembler.assemble(
                    "P-20260622-001", request, ctx.itineraryResult(), ctx.budgetResult());
            TripPlanResult result = new TripPlanResult(
                    plan, true, List.of("天数不一致: 需求 5 天，实际 0 天"),
                    false, null, TripPlanResult.PATH_NATIVE, false);

            assertTrue(NativeDataFlowGuard.isDataFlowBroken(ctx, result),
                    "XHS 有候选景点但 plan days 为空 → 应判定为数据流破坏");
        }

        @Test
        @DisplayName("XHS 候选为空且 plan days 为空 → 不是数据流破坏（真实空结果）")
        void xhsCandidatesEmptyPlanDaysEmptyShouldNotDetectBrokenFlow() {
            SupervisorNativeRunContext ctx = new SupervisorNativeRunContext();
            ctx.setXhsResult(new XHSAnalysisResult(
                    List.of(), false, "未知城市", null));
            ctx.setRouteResult(new RoutePlanResult(
                    List.of(), List.of(), List.of(), false, null, null));
            ctx.setItineraryResult(new ItineraryDesignResult(
                    List.of(), List.of(), false, null, null));
            ctx.setBudgetResult(new BudgetCalculationResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of(), false, null, List.of(), false, null));

            TripPlanRequest request = new TripPlanRequest(
                    "未知城市", LocalDate.of(2026, 7, 1), 3,
                    new BigDecimal("3000"), 1, null, List.of());
            TripPlan plan = TripPlanAssembler.assemble(
                    "P-TEST", request, ctx.itineraryResult(), ctx.budgetResult());
            TripPlanResult result = new TripPlanResult(
                    plan, false, List.of(), false, null, TripPlanResult.PATH_NATIVE, false);

            assertFalse(NativeDataFlowGuard.isDataFlowBroken(ctx, result),
                    "XHS 候选为空且 plan days 为空 → 不判定为数据流破坏（真实空结果）");
        }

        @Test
        @DisplayName("XHS 有候选且 plan days 非空 → 正常完成，不触发降级")
        void xhsCandidatesNonEmptyPlanDaysNonEmptyShouldNotDetectBrokenFlow() {
            SupervisorNativeRunContext ctx = new SupervisorNativeRunContext();
            ctx.setXhsResult(new XHSAnalysisResult(
                    List.of(new AttractionCandidate(
                            attraction("BJ001", "故宫", 4.8), 10, 7, 2, 1)),
                    false, null, null));
            // 模拟正常完成：itinerary 有非空 days
            ctx.setItineraryResult(new ItineraryDesignResult(
                    List.of(), List.of(), false, null, null));
            ctx.setBudgetResult(new BudgetCalculationResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of(), false, null, List.of(), false, null));

            TripPlanRequest request = new TripPlanRequest(
                    "北京", LocalDate.of(2026, 7, 1), 5,
                    new BigDecimal("8000"), 3, null, List.of("古镇", "美食"));
            // 构造一个 days 非空的 plan（空景点但有一条 TripDay 占位）
            TripPlan nonEmptyPlan = new TripPlan(
                    "P-OK", request,
                    List.of(new TripDay(
                            LocalDate.of(2026, 7, 1), 1,
                            List.of(), List.of(), List.of(), BigDecimal.ZERO)),
                    BigDecimal.ZERO,
                    new com.smart.trip.common.model.Budget(
                            new BigDecimal("8000"), BigDecimal.ZERO, BigDecimal.ZERO,
                            new com.smart.trip.common.model.BudgetBreakdown(
                                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)),
                    com.smart.trip.common.enums.PlanStatus.CONFIRMED, false);
            TripPlanResult result = new TripPlanResult(
                    nonEmptyPlan, false, List.of(), false, null, TripPlanResult.PATH_NATIVE, false);

            assertFalse(NativeDataFlowGuard.isDataFlowBroken(ctx, result),
                    "plan days 非空 → 不判定为数据流破坏（编排正常完成）");
        }

        @Test
        @DisplayName("四个 typed artifact 完整且 days 非空 → native finalizer 返回 PATH_NATIVE")
        void completeTypedArtifactsShouldFinalizePathNative() {
            SupervisorHarnessAgentFactory factory = new SupervisorHarnessAgentFactory(
                    fakeModel(), null, stubService(), stubToolFactory(),
                    stubQualityGate(), tempDir, null);
            SupervisorNativeRunContext ctx = new SupervisorNativeRunContext();
            ctx.setXhsResult(new XHSAnalysisResult(
                    List.of(new AttractionCandidate(
                            attraction("BJ001", "故宫", 4.8), 10, 7, 2, 1)),
                    false, null, null));
            ctx.setRouteResult(new RoutePlanResult(
                    List.of(), List.of(), List.of(), false, null, null));
            TripDay day = new TripDay(
                    LocalDate.of(2026, 7, 1), 1,
                    List.of(attraction("BJ001", "故宫", 4.8)), List.of(), List.of(),
                    new BigDecimal("100"));
            ctx.setItineraryResult(new ItineraryDesignResult(
                    List.of(day), List.of(), false, null, null));
            ctx.setBudgetResult(new BudgetCalculationResult(
                    new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("100"), BigDecimal.ZERO,
                    List.of(), false, null, List.of(), false, null));
            TripPlanRequest request = new TripPlanRequest(
                    "北京", LocalDate.of(2026, 7, 1), 5,
                    new BigDecimal("8000"), 3, null, List.of("古镇", "美食"));

            TripPlanResult result = factory.finalizeNative(
                    "P-20260622-001", request, ctx, false, "T-test-native-001");

            assertTrue(ctx.isComplete(), "四个 typed artifact 应全部写入 run context");
            assertNotNull(result);
            assertEquals(TripPlanResult.PATH_NATIVE, result.path());
            assertFalse(result.plan().days().isEmpty());
            assertFalse(NativeDataFlowGuard.isDataFlowBroken(ctx, result));
        }

        @Test
        @DisplayName("nativeResult 为 null → 不判定为数据流破坏（由 resolve() 降级处理）")
        void nullResultShouldNotBeBrokenFlow() {
            SupervisorNativeRunContext ctx = new SupervisorNativeRunContext();
            ctx.setXhsResult(new XHSAnalysisResult(
                    List.of(new AttractionCandidate(
                            attraction("BJ001", "故宫", 4.8), 10, 7, 2, 1)),
                    false, null, null));

            assertFalse(NativeDataFlowGuard.isDataFlowBroken(ctx, null),
                    "nativeResult 为 null 时不应判定为数据流破坏");
        }

        @Test
        @DisplayName("xhsResult 为 null → 不判定为数据流破坏（XHS 未产出）")
        void nullXhsResultShouldNotBeBrokenFlow() {
            SupervisorNativeRunContext ctx = new SupervisorNativeRunContext();
            // xhsResult 未设置（null）

            TripPlanRequest request = new TripPlanRequest(
                    "北京", LocalDate.of(2026, 7, 1), 5,
                    new BigDecimal("8000"), 3, null, List.of());
            TripPlan plan = TripPlanAssembler.assemble(
                    "P-TEST", request, null, null);
            TripPlanResult result = new TripPlanResult(
                    plan, false, List.of(), false, null, TripPlanResult.PATH_NATIVE, false);

            assertFalse(NativeDataFlowGuard.isDataFlowBroken(ctx, result),
                    "xhsResult 为 null → 不判定为数据流破坏");
        }
    }

    @Nested
    @DisplayName("C-017 FIX-3: native 成功路径的子 Agent 委派审计")
    class NativeDelegationTrace {

        private static final GeoLocation LOC = new GeoLocation(116.4, 39.9, "北京市东城区", "北京");

        private static Attraction attraction(String id, String name, double rating) {
            return new Attraction(id, name, LOC, rating, 120, BigDecimal.ZERO, List.of());
        }

        /** 捕获式 TracePersistenceService：记录每次 saveDelegation 调用的record，供断言。 */
        private static final class CapturingTracePersistenceService
                implements com.smart.trip.skills.trace.TracePersistenceService {
            final List<com.smart.trip.skills.trace.TraceDelegationRecord> delegations =
                    new java.util.ArrayList<>();

            @Override
            public void saveDelegation(com.smart.trip.skills.trace.TraceDelegationRecord record) {
                delegations.add(record);
            }

            @Override
            public void saveOrchestrationSummary(
                    com.smart.trip.skills.trace.TraceOrchestrationSummary summary) {
                throw new AssertionError(
                        "finalizeNative 不应写 orchestration_summary（唯一写入者是 SupervisorAgent）");
            }

            @Override
            public void saveLlmCall(com.smart.trip.skills.trace.TraceLlmCallRecord record) {
                throw new AssertionError(
                        "finalizeNative 不应写 llm_call（唯一写入者是 SupervisorAgent）");
            }
        }

        private SupervisorNativeRunContext completeContext() {
            SupervisorNativeRunContext ctx = new SupervisorNativeRunContext();
            ctx.setXhsResult(new XHSAnalysisResult(
                    List.of(new AttractionCandidate(attraction("BJ001", "故宫", 4.8), 10, 7, 2, 1)),
                    false, null, null));
            ctx.setRouteResult(new RoutePlanResult(List.of(), List.of(), List.of(), false, null, null));
            TripDay day = new TripDay(LocalDate.of(2026, 7, 1), 1,
                    List.of(attraction("BJ001", "故宫", 4.8)), List.of(), List.of(),
                    new BigDecimal("100"));
            ctx.setItineraryResult(new ItineraryDesignResult(List.of(day), List.of(), false, null, null));
            ctx.setBudgetResult(new BudgetCalculationResult(
                    new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("100"), BigDecimal.ZERO,
                    List.of(), false, null, List.of(), false, null));
            return ctx;
        }

        @Test
        @DisplayName("finalizeNative 成功 → 恰好写入 4 条委派记录（XHS/Route/Itinerary/Budget）")
        void finalizeNativeWritesFourDelegationRecords() {
            CapturingTracePersistenceService capturing = new CapturingTracePersistenceService();
            SupervisorHarnessAgentFactory factory = new SupervisorHarnessAgentFactory(
                    fakeModel(), null, stubService(), stubToolFactory(),
                    stubQualityGate(), tempDir, null, capturing);
            SupervisorNativeRunContext ctx = completeContext();
            TripPlanRequest request = new TripPlanRequest(
                    "北京", LocalDate.of(2026, 7, 1), 5,
                    new BigDecimal("8000"), 3, null, List.of("古镇", "美食"));

            factory.finalizeNative("P-20260622-001", request, ctx, false, "T-test-native-002");

            assertEquals(4, capturing.delegations.size(), "四个子 Agent 均应写入一条委派记录");
            List<String> subAgentIds = capturing.delegations.stream()
                    .map(com.smart.trip.skills.trace.TraceDelegationRecord::subAgentId).toList();
            assertTrue(subAgentIds.contains("agent://xhs-analyst"));
            assertTrue(subAgentIds.contains("agent://route-planner"));
            assertTrue(subAgentIds.contains("agent://itinerary-designer"));
            assertTrue(subAgentIds.contains("agent://budget-controller"));
            capturing.delegations.forEach(record ->
                    assertEquals("T-test-native-002", record.traceId(),
                            "四条委派记录应共用同一个 traceId，便于与 summary/llm_call 关联"));
        }

        @Test
        @DisplayName("tracePersistence 为 null → finalizeNative 正常返回，不抛异常")
        void nullTracePersistenceSkipsAuditSafely() {
            SupervisorHarnessAgentFactory factory = new SupervisorHarnessAgentFactory(
                    fakeModel(), null, stubService(), stubToolFactory(),
                    stubQualityGate(), tempDir, null, null);
            SupervisorNativeRunContext ctx = completeContext();
            TripPlanRequest request = new TripPlanRequest(
                    "北京", LocalDate.of(2026, 7, 1), 5,
                    new BigDecimal("8000"), 3, null, List.of("古镇", "美食"));

            TripPlanResult result = factory.finalizeNative(
                    "P-20260622-001", request, ctx, false, "T-test-native-003");

            assertNotNull(result);
            assertEquals(TripPlanResult.PATH_NATIVE, result.path());
        }
    }

    @Nested
    @DisplayName("C-018 AC-2/AC-3/AC-7 + T-14: 计划模式装配（官方 programmatic API，纯 Java）")
    class PlanModeAssembly {

        @Test
        @DisplayName("PLAN_SYS_PROMPT 禁止 agent_spawn，要求 plan_write 后停止（T-14：不调 plan_exit）")
        void planSysPromptForbidsAgentSpawnAndPlanExit() {
            String prompt = SupervisorPlanModeSupport.PLAN_SYS_PROMPT;
            assertTrue(prompt.contains("禁止") && prompt.contains("agent_spawn"),
                    "PLAN_SYS_PROMPT 应明确禁止调用 agent_spawn");
            assertTrue(prompt.contains("plan_write"),
                    "PLAN_SYS_PROMPT 应指示 LLM 调用 plan_write");
            // T-14：指令 LLM 直接结束，不要调 plan_exit
            assertTrue(prompt.contains("直接结束"),
                    "T-14: PLAN_SYS_PROMPT 应要求 LLM 写完后直接结束");
            assertFalse(prompt.contains("调用 plan_exit 请求批准"),
                    "T-14: 不应再要求调用 plan_exit 请求批准");
        }

        @Test
        @DisplayName("PLAN_SYS_PROMPT 与默认 SYS_PROMPT 是两个独立常量")
        void planSysPromptIsDistinctFromDefaultSysPrompt() {
            assertNotEquals(SupervisorHarnessAgentFactory.SYS_PROMPT,
                    SupervisorPlanModeSupport.PLAN_SYS_PROMPT);
        }

        @Test
        @DisplayName("buildPlanPrompt 携带请求内容，不要求 plan_exit（T-14）")
        void buildPlanPromptCarriesRequestWithoutPlanExit() {
            TripOrchestrationQuery query = new TripOrchestrationQuery(
                    new TripPlanRequest("云南", LocalDate.of(2026, 7, 1), 5,
                            new BigDecimal("8000"), 2, null, List.of(), true));
            String text = SupervisorPlanModeSupport.buildPlanPrompt(query).getTextContent();
            assertTrue(text.contains("云南"), "计划提示词应包含目的地等请求内容");
            assertTrue(text.contains("plan_write"), "计划提示词应要求 LLM 调用 plan_write");
            // T-14：要求直接结束，不再要求"调用 plan_exit 请求批准"
            assertTrue(text.contains("直接结束"),
                    "T-14: buildPlanPrompt 应要求 LLM 写完后直接结束");
            assertFalse(text.contains("调用 plan_exit 请求批准"),
                    "T-14: buildPlanPrompt 不应再包含旧式 plan_exit 批准指令");
        }

        @Test
        @DisplayName("readPlanDraft: 计划草案存在 → 返回全文")
        void readPlanDraftReturnsContentWhenFileExists() throws IOException {
            Path plansDir = tempDir.resolve("plans");
            Files.createDirectories(plansDir);
            Files.writeString(plansDir.resolve("PLAN.md"), "# 云南 5 日游计划\n1. XHS...\n2. Route...\n");
            String draft = SupervisorPlanModeSupport.readPlanDraft(tempDir);
            assertTrue(draft.contains("云南 5 日游计划"));
            assertTrue(draft.contains("XHS"));
        }

        @Test
        @DisplayName("readPlanDraft: 文件缺失 → 返回空串（降级容忍，不抛异常）")
        void readPlanDraftReturnsEmptyWhenFileMissing() throws IOException {
            String draft = SupervisorPlanModeSupport.readPlanDraft(tempDir);
            assertTrue(draft.isEmpty());
        }

        @Test
        @DisplayName("sessionContext: planId 非空 → 构造带 sessionId 的 RuntimeContext")
        void sessionContextWithPlanId() {
            TripOrchestrationQuery query = new TripOrchestrationQuery(
                    new TripPlanRequest("云南", LocalDate.of(2026, 7, 1), 5,
                            new BigDecimal("8000"), 2, null, List.of(), true),
                    "P-20260711-001", "user-1", null, "trace-1");
            RuntimeContext ctx = SupervisorPlanModeSupport.sessionContext(query);
            assertEquals("P-20260711-001", ctx.getSessionId());
            assertEquals("user-1", ctx.getUserId());
        }

        @Test
        @DisplayName("sessionContext: planId 为 null → 返回 RuntimeContext.empty()")
        void sessionContextWithNullPlanId() {
            TripOrchestrationQuery query = new TripOrchestrationQuery(
                    new TripPlanRequest("北京", LocalDate.of(2026, 7, 1), 3,
                            new BigDecimal("3000"), 1, null, List.of()));
            RuntimeContext ctx = SupervisorPlanModeSupport.sessionContext(query);
            assertNull(ctx.getSessionId());
        }

        @Test
        @DisplayName("PlanDraftPause: draftMarkdown 可读写，pendingConfirmationJson 为空（T-14）")
        void planDraftPauseHasDraftButNoPendingConfirmation() {
            PlanDraftPause pause = new PlanDraftPause("# 桂林 3 日游计划\n...", "");
            assertEquals("# 桂林 3 日游计划\n...", pause.draftMarkdown());
            assertTrue(pause.pendingConfirmationJson().isEmpty(),
                    "T-14: pendingConfirmationJson 应为空串（不再存储 ToolUseBlock）");
        }
    }

    /**
     * 创建一个假 Model 桩（仅用于触发后续参数的非 null 校验）。
     * 生产代码中 Model 由 LlmModelFactory 构建，此处仅需非 null 引用。
     */
    private static io.agentscope.core.model.Model fakeModel() {
        return io.agentscope.core.model.OpenAIChatModel.builder()
                .apiKey("sk-test")
                .modelName("test")
                .baseUrl("http://localhost")
                .stream(false)
                .build();
    }
}
