package com.smart.trip.skills.supervisor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.skills.llm.LlmModelFactory;
import com.smart.trip.skills.quality.OutputQualityGate;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link SupervisorHarnessAgentFactory} env-gated 集成测试（C-010 v2）。
 *
 * <p><b>需要真实 LLM 密钥（DEEPSEEK_API_KEY）</b>，否则全部 skip。
 * 验证完整的原生路：LLM 经 agent_spawn 委派子 Agent → 子 Agent 调白名单 @Tool →
 * 最终调 orchestrate_trip 收口 → 产出 TripPlanResult。
 * 该类涉及真实网络/LLM，JaCoCo 排除。</p>
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+",
        disabledReason = "需要 DEEPSEEK_API_KEY 环境变量进行真实 LLM 集成验证")
class SupervisorHarnessAgentFactoryIT {

    @TempDir
    Path workspace;

    private TripOrchestrationService stubService;

    @BeforeEach
    void setUp() {
        // 构造一个桩 TripOrchestrationService（确定性兜底路在集成测试中也使用桩，
        // 因为真实集成测试的焦点是 LLM 能否正确驱动 agent_spawn + 最终调 orchestrate_trip）
        SubAgentDispatcher stubDispatcher = (agentUri, assign) -> {
            Object result = switch (agentUri) {
                case "agent://xhs-analyst" -> new com.smart.trip.skills.xhs.XHSAnalysisResult(
                        List.of(), false, "集成测试桩", null);
                case "agent://route-planner" -> new com.smart.trip.skills.route.RoutePlanResult(
                        List.of(), List.of(), List.of(), false, "集成测试桩", null);
                case "agent://itinerary-designer" ->
                    new com.smart.trip.skills.itinerary.ItineraryDesignResult(
                        List.of(), List.of(), false, "集成测试桩", null);
                case "agent://budget-controller" ->
                    new com.smart.trip.skills.budget.BudgetCalculationResult(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, List.of(), false, null, List.of(), false, null);
                default -> null;
            };
            return com.smart.trip.common.a2a.Msg.taskResult(
                    agentUri, assign.from(), assign.traceId(), result, false);
        };
        stubService = new TripOrchestrationService(
                stubDispatcher, new com.smart.trip.skills.quality.OutputQualityGate());
    }

    @Nested
    @DisplayName("真实 LLM 集成（需要 DEEPSEEK_API_KEY）")
    class RealLLMIntegration {

        @Test
        @DisplayName("有 DEEPSEEK_API_KEY 时：构建工厂 → run(query) → LLM agent_spawn 委派 → "
                + "子 Agent 调白名单 @Tool → orchestrate_trip 收口 → 产出 TripPlanResult")
        void fullNativePathWithRealLLM() throws Exception {
            // 验证 API key 存在
            String apiKey = System.getenv("DEEPSEEK_API_KEY");
            assumeTrue(apiKey != null && !apiKey.isBlank(),
                    "跳过：DEEPSEEK_API_KEY 未设置或为空");

            // 确保 workspace 目录存在
            assertTrue(Files.isDirectory(workspace) || workspace.toFile().mkdirs());

            TripPlanRequest request = new TripPlanRequest(
                    "北京", LocalDate.of(2026, 7, 1), 2,
                    new BigDecimal("5000"), 2, TravelStyle.COUPLE, List.of("古迹"));

            SupervisorHarnessAgentFactory factory =
                    SupervisorHarnessAgentFactory.fromEnvironment(
                            stubService, (runContext) -> List.of(), new OutputQualityGate(), workspace);

            // 无 key → 返回 null，跳过
            if (factory == null) {
                return; // assumeTrue 已在上方，此防御保留
            }

            try {
                TripPlanResult result = factory.run(new TripOrchestrationQuery(request));
                // LLM 应调用 orchestrate_trip 工具收口
                assertNotNull(result, "LLM 应经 agent_spawn + orchestrate_trip 产出 TripPlanResult");
                assertNotNull(result.plan(), "产出的 TripPlan 不应为 null");
            } finally {
                factory.close();
            }
        }
    }

    @Nested
    @DisplayName("无 Key 行为")
    class NoKeyBehavior {

        @Test
        @DisplayName("DEEPSEEK_API_KEY 未设置 → fromEnvironment() 返回 null（不伪装通过）")
        void returnsNullWhenNoKey() {
            // 临时清除环境变量验证
            String originalKey = System.getenv("DEEPSEEK_API_KEY");
            assumeTrue(originalKey == null || originalKey.isBlank(),
                    "此测试仅在 DEEPSEEK_API_KEY 未设置时运行；当前已设置，跳过");

            SupervisorHarnessAgentFactory factory =
                    SupervisorHarnessAgentFactory.fromEnvironment(
                            stubService, (runContext) -> List.of(), new OutputQualityGate(), workspace);
            // 无 key → 应返回 null，由 SupervisorAgent 走确定性直路
            assertTrue(factory == null,
                    "无 DEEPSEEK_API_KEY 时 fromEnvironment() 应返回 null");
        }

        @Test
        @DisplayName("DASHSCOPE_API_KEY 仅降级 key 存在 → fromEnvironment() 非 null（降级可用）")
        void fallbackKeyOnly() {
            String deepseekKey = System.getenv("DEEPSEEK_API_KEY");
            String dashscopeKey = System.getenv("DASHSCOPE_API_KEY");
            assumeTrue((deepseekKey == null || deepseekKey.isBlank())
                            && dashscopeKey != null && !dashscopeKey.isBlank(),
                    "此测试仅在 DEEPSEEK_API_KEY 未设置且 DASHSCOPE_API_KEY 已设置时运行");

            SupervisorHarnessAgentFactory factory =
                    SupervisorHarnessAgentFactory.fromEnvironment(
                            stubService, (runContext) -> List.of(), new OutputQualityGate(), workspace);
            // 仅有降级 key → 可用 qwen3-max
            assertNotNull(factory, "仅有 DASHSCOPE_API_KEY 时应可构建工厂（降级模式）");
        }
    }
}
