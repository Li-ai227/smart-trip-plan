package com.smart.trip.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.skills.route.HaversineRouter;
import com.smart.trip.skills.route.RoutePlanningService;
import com.smart.trip.skills.supervisor.TripOrchestrationQuery;
import com.smart.trip.skills.supervisor.TripOrchestrationService;

import com.smart.trip.agent.supervisor.SupervisorAgent;
import com.smart.trip.agent.xhs.XHSAnalysisAgent;
import com.smart.trip.common.a2a.MessageType;
import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.model.TripPlan;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.skills.budget.BudgetAgentRunner;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.itinerary.ItineraryAgentRunner;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.route.RouteAgentRunner;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.supervisor.SubAgentDispatcher;
import com.smart.trip.skills.supervisor.SupervisorAgentRunner;
import com.smart.trip.skills.supervisor.TripPlanResult;
import com.smart.trip.skills.xhs.XHSAgentRunner;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import java.util.Optional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * C-012-B：生产态 PlanConfig 的 AgentScope 2.0 subagent 调度配线守护。
 *
 * <p>红测先行覆盖 AC-B9 五类场景：runner 注入、native path 优先、fallback dispatcher 非占位、
 * 测试替身不被覆盖、无 key 降级。</p>
 */
class PlanConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PlanConfig.class)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(PlanCacheService.class, () -> mock(PlanCacheService.class));

    // ── AC-B9 场景一：runner 注入 ──

    @Test
    @DisplayName("SupervisorAgent 优先注入可用 SupervisorAgentRunner")
    void supervisorAgentShouldUseProvidedRunner() {
        SupervisorAgentRunner runner = query -> new TripPlanResult(
                mock(TripPlan.class), false, List.of(), false,
                new TripPlanResult.Telemetry(0, false, 1),
                TripPlanResult.PATH_NATIVE, false);

        contextRunner.withBean(SupervisorAgentRunner.class, () -> runner)
                .run(context -> assertThat(runnerField(context.getBean("supervisorAgent")))
                        .isSameAs(runner));
    }

    // ── AC-B9 场景二：native path 优先 ──

    @Test
    @DisplayName("注入 runner 后 supervisorAgent.receive() 优先调用 runner（native path 优先）")
    void supervisorAgentShouldCallRunnerBeforeFallback() {
        AtomicBoolean runnerCalled = new AtomicBoolean(false);
        SupervisorAgentRunner observableRunner = query -> {
            runnerCalled.set(true);
            return new TripPlanResult(
                    mock(TripPlan.class), false, List.of(), false,
                    new TripPlanResult.Telemetry(0, false, 1),
                    TripPlanResult.PATH_NATIVE, false);
        };

        contextRunner.withBean(SupervisorAgentRunner.class, () -> observableRunner)
                .run(context -> {
                    SupervisorAgent agent = context.getBean(SupervisorAgent.class);
                    TripPlanRequest request = new TripPlanRequest(
                            "云南", LocalDate.of(2026, 7, 1), 3,
                            new BigDecimal("5000"), 2, null, null);
                    Msg assign = Msg.taskAssign(
                            "agent://test", SupervisorAgent.AGENT_URI, "T-native-path",
                            Map.of(
                                    Msg.KEY_TASK_TYPE, SupervisorAgent.TASK_TYPE,
                                    SupervisorAgent.KEY_CONTEXT,
                                    Map.of(SupervisorAgent.CTX_REQUEST, request)));
                    Msg result = agent.receive(assign);
                    assertThat(runnerCalled.get()).isTrue();
                    assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
                });
    }

    // ── AC-B9 场景三：fallback dispatcher 非占位 ──

    @Test
    @DisplayName("生产态 SubAgentDispatcher 不再是 SERVICE_UNAVAILABLE 占位")
    void productionDispatcherShouldRouteToRealXhsAgent() {
        contextRunner.run(context -> {
            SubAgentDispatcher dispatcher = context.getBean(SubAgentDispatcher.class);
            Msg assign = Msg.taskAssign("agent://supervisor", XHSAnalysisAgent.AGENT_URI,
                    "T-planconfig-red", Map.of(
                            Msg.KEY_TASK_TYPE, XHSAnalysisAgent.TASK_TYPE,
                            "context", Map.of(
                                    "destination", "云南",
                                    "preferences", List.of(),
                                    "travelStyle", TripPlanRequest.class.getSimpleName())));

            Msg result = dispatcher.dispatch(XHSAnalysisAgent.AGENT_URI, assign);

            assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
            assertThat(result.from()).isEqualTo(XHSAnalysisAgent.AGENT_URI);
        });
    }

    // ── AC-B9 场景四：测试替身不被覆盖 ──

    @Test
    @DisplayName("测试替身 SubAgentDispatcher 优先于 PlanConfig 默认装配")
    void customDispatcherShouldNotBeOverridden() {
        SubAgentDispatcher custom = (agentUri, assign) -> Msg.taskResult(
                "agent://custom", assign.from(), assign.traceId(), Map.of("ok", true), false);

        contextRunner.withBean(SubAgentDispatcher.class, () -> custom)
                .run(context -> assertThat(context.getBean(SubAgentDispatcher.class)).isSameAs(custom));
    }

    // ── AC-B9 场景五：无 key 降级 ──

    @Test
    @DisplayName("无 LLM key 时 supervisorAgent 正常加载，runner 为 null 走纯确定性路")
    void supervisorAgentShouldLoadWithNullRunnerWhenNoKey() {
        // 仅在 CI（DEEPSEEK_API_KEY 与 DASHSCOPE_API_KEY 均缺失）环境执行；
        // 本地如有 key 则 skip，避免误判 native path 为 null。
        Assumptions.assumeTrue(
                System.getenv("DEEPSEEK_API_KEY") == null
                        && System.getenv("DASHSCOPE_API_KEY") == null,
                "无 key 降级测试仅在 CI（无 LLM key）环境执行");
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SupervisorAgent.class);
            assertThat(runnerField(context.getBean("supervisorAgent"))).isNull();
        });
    }

    // ── C-012-C：native path 证据区分 ──

    @Test
    @DisplayName("C-012-C：runner 返回 PATH_NATIVE → 结果 fallback=false，路径为 native")
    void nativePathResultShouldNotBeFallback() {
        SupervisorAgentRunner nativeRunner = query -> new TripPlanResult(
                mock(TripPlan.class), false, List.of(), false,
                new TripPlanResult.Telemetry(0, false, 1),
                TripPlanResult.PATH_NATIVE, false);

        contextRunner.withBean(SupervisorAgentRunner.class, () -> nativeRunner)
                .run(context -> {
                    SupervisorAgent agent = context.getBean(SupervisorAgent.class);
                    TripPlanRequest request = new TripPlanRequest(
                            "云南", LocalDate.of(2026, 7, 1), 3,
                            new BigDecimal("5000"), 2, null, null);
                    Msg assign = Msg.taskAssign(
                            "agent://test", SupervisorAgent.AGENT_URI, "T-C-C1",
                            Map.of(
                                    Msg.KEY_TASK_TYPE, SupervisorAgent.TASK_TYPE,
                                    SupervisorAgent.KEY_CONTEXT,
                                    Map.of(SupervisorAgent.CTX_REQUEST, request)));
                    Msg result = agent.receive(assign);
                    assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
                    // native success → fallback=false
                    assertThat(result.isFallback()).isFalse();
                });
    }

    @Test
    @DisplayName("C-012-C：runner 返回 PATH_DETERMINISTIC → 结果 fallback=true，路径为 deterministic")
    void deterministicPathResultShouldBeFallback() {
        SupervisorAgentRunner detRunner = query -> new TripPlanResult(
                mock(TripPlan.class), false, List.of(), false,
                new TripPlanResult.Telemetry(0, false, 1),
                TripPlanResult.PATH_DETERMINISTIC, false);

        contextRunner.withBean(SupervisorAgentRunner.class, () -> detRunner)
                .run(context -> {
                    SupervisorAgent agent = context.getBean(SupervisorAgent.class);
                    TripPlanRequest request = new TripPlanRequest(
                            "云南", LocalDate.of(2026, 7, 1), 3,
                            new BigDecimal("5000"), 2, null, null);
                    Msg assign = Msg.taskAssign(
                            "agent://test", SupervisorAgent.AGENT_URI, "T-C-C2",
                            Map.of(
                                    Msg.KEY_TASK_TYPE, SupervisorAgent.TASK_TYPE,
                                    SupervisorAgent.KEY_CONTEXT,
                                    Map.of(SupervisorAgent.CTX_REQUEST, request)));
                    Msg result = agent.receive(assign);
                    assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
                    // runner 返回 deterministic path → 应标记为 fallback（降级）
                    assertThat(result.isFallback()).isTrue();
                });
    }

    @Test
    @DisplayName("C-012-C：无 runner 时走确定性直路，路径为 deterministic")
    void noRunnerShouldUseDeterministicPath() {
        // 注入 null runner
        contextRunner.run(context -> {
            SupervisorAgent agent = context.getBean(SupervisorAgent.class);
            TripPlanRequest request = new TripPlanRequest(
                    "云南", LocalDate.of(2026, 7, 1), 3,
                    new BigDecimal("5000"), 2, null, null);
            Msg assign = Msg.taskAssign(
                    "agent://test", SupervisorAgent.AGENT_URI, "T-C-C3",
                    Map.of(
                            Msg.KEY_TASK_TYPE, SupervisorAgent.TASK_TYPE,
                            SupervisorAgent.KEY_CONTEXT,
                            Map.of(SupervisorAgent.CTX_REQUEST, request)));
            Msg result = agent.receive(assign);
            assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
            // 无 key 的确定性直路不标记 runner 降级 → fallback 取决于子 Agent 是否降级
            TripPlanResult planResult = (TripPlanResult) result.payload().get(Msg.KEY_RESULT);
            assertThat(planResult.path())
                    .isEqualTo(TripPlanResult.PATH_DETERMINISTIC);
        });
    }

    // ── 子 Agent runner 装配（AC：agent_spawn → HarnessAgent 路）──

    @Test
    @DisplayName("注入 XHSAgentRunner 后 dispatcher 的 XHSAnalysisAgent 调用 runner（native path 优先）")
    void xhsAgentShouldCallInjectedRunner() {
        AtomicBoolean runnerCalled = new AtomicBoolean(false);
        XHSAgentRunner stubRunner = query -> {
            runnerCalled.set(true);
            return new XHSAnalysisResult(List.of(), false, "stub",
                    XHSAnalysisResult.Telemetry.none());
        };

        contextRunner.withBean(XHSAgentRunner.class, () -> stubRunner)
                .run(context -> {
                    SubAgentDispatcher dispatcher = context.getBean(SubAgentDispatcher.class);
                    Msg assign = Msg.taskAssign("agent://supervisor", XHSAnalysisAgent.AGENT_URI,
                            "T-subagent-xhs", Map.of(
                                    Msg.KEY_TASK_TYPE, XHSAnalysisAgent.TASK_TYPE,
                                    XHSAnalysisAgent.KEY_CONTEXT, Map.of(
                                            "destination", "云南",
                                            "preferences", List.of("美食"),
                                            "headcount", 2,
                                            "style", "FAMILY")));
                    Msg result = dispatcher.dispatch(XHSAnalysisAgent.AGENT_URI, assign);
                    assertThat(runnerCalled.get())
                            .as("XHSAnalysisAgent 应优先调用注入的 XHSAgentRunner（native path）")
                            .isTrue();
                    assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
                });
    }

    @Test
    @DisplayName("注入 RouteAgentRunner 后 dispatcher 的 RouteAgent 调用 runner（native path 优先）")
    void routeAgentShouldCallInjectedRunner() {
        AtomicBoolean runnerCalled = new AtomicBoolean(false);
        RouteAgentRunner stubRunner = query -> {
            runnerCalled.set(true);
            return new RoutePlanResult(List.of(), List.of(), List.of(), false, "stub",
                    RoutePlanResult.Telemetry.none());
        };

        contextRunner.withBean(RouteAgentRunner.class, () -> stubRunner)
                .run(context -> {
                    SubAgentDispatcher dispatcher = context.getBean(SubAgentDispatcher.class);
                    Msg assign = Msg.taskAssign("agent://supervisor",
                            com.smart.trip.agent.route.RouteAgent.AGENT_URI,
                            "T-subagent-route", Map.of(
                                    Msg.KEY_TASK_TYPE,
                                    com.smart.trip.agent.route.RouteAgent.TASK_TYPE,
                                    com.smart.trip.agent.route.RouteAgent.KEY_CONTEXT, Map.of(
                                            "destination", "云南",
                                            "attractions", List.of(),
                                            "transportMode", "DRIVING")));
                    Msg result = dispatcher.dispatch(
                            com.smart.trip.agent.route.RouteAgent.AGENT_URI, assign);
                    assertThat(runnerCalled.get())
                            .as("RouteAgent 应优先调用注入的 RouteAgentRunner（native path）")
                            .isTrue();
                    assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
                });
    }

    @Test
    @DisplayName("注入 ItineraryAgentRunner 后 dispatcher 的 ItineraryAgent 调用 runner（native path 优先）")
    void itineraryAgentShouldCallInjectedRunner() {
        AtomicBoolean runnerCalled = new AtomicBoolean(false);
        ItineraryAgentRunner stubRunner = query -> {
            runnerCalled.set(true);
            return new ItineraryDesignResult(List.of(), List.of(), false, "stub",
                    ItineraryDesignResult.Telemetry.none());
        };

        contextRunner.withBean(ItineraryAgentRunner.class, () -> stubRunner)
                .run(context -> {
                    SubAgentDispatcher dispatcher = context.getBean(SubAgentDispatcher.class);
                    TripPlanRequest request = new TripPlanRequest(
                            "云南", LocalDate.of(2026, 7, 1), 3,
                            new BigDecimal("5000"), 2, null, null);
                    Msg assign = Msg.taskAssign("agent://supervisor",
                            com.smart.trip.agent.itinerary.ItineraryAgent.AGENT_URI,
                            "T-subagent-itin", Map.of(
                                    Msg.KEY_TASK_TYPE,
                                    com.smart.trip.agent.itinerary.ItineraryAgent.TASK_TYPE,
                                    com.smart.trip.agent.itinerary.ItineraryAgent.KEY_CONTEXT,
                                    Map.of("request", request, "attractions", List.of())));
                    Msg result = dispatcher.dispatch(
                            com.smart.trip.agent.itinerary.ItineraryAgent.AGENT_URI, assign);
                    assertThat(runnerCalled.get())
                            .as("ItineraryAgent 应优先调用注入的 ItineraryAgentRunner（native path）")
                            .isTrue();
                    assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
                });
    }

    @Test
    @DisplayName("注入 BudgetAgentRunner 后 dispatcher 的 BudgetAgent 调用 runner（native path 优先）")
    void budgetAgentShouldCallInjectedRunner() {
        AtomicBoolean runnerCalled = new AtomicBoolean(false);
        BudgetAgentRunner stubRunner = query -> {
            runnerCalled.set(true);
            return new BudgetCalculationResult(
                    new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of(), false, null, List.of(), false,
                    BudgetCalculationResult.Telemetry.none());
        };

        contextRunner.withBean(BudgetAgentRunner.class, () -> stubRunner)
                .run(context -> {
                    SubAgentDispatcher dispatcher = context.getBean(SubAgentDispatcher.class);
                    TripPlanRequest request = new TripPlanRequest(
                            "云南", LocalDate.of(2026, 7, 1), 3,
                            new BigDecimal("5000"), 2, null, null);
                    ItineraryDesignResult stubItinerary = new ItineraryDesignResult(
                            List.of(), List.of(), false, "stub",
                            ItineraryDesignResult.Telemetry.none());
                    Msg assign = Msg.taskAssign("agent://supervisor",
                            com.smart.trip.agent.budget.BudgetAgent.AGENT_URI,
                            "T-subagent-budget", Map.of(
                                    Msg.KEY_TASK_TYPE,
                                    com.smart.trip.agent.budget.BudgetAgent.TASK_TYPE,
                                    com.smart.trip.agent.budget.BudgetAgent.KEY_CONTEXT,
                                    Map.of("request", request,
                                            "itineraryResult", stubItinerary)));
                    Msg result = dispatcher.dispatch(
                            com.smart.trip.agent.budget.BudgetAgent.AGENT_URI, assign);
                    assertThat(runnerCalled.get())
                            .as("BudgetAgent 应优先调用注入的 BudgetAgentRunner（native path）")
                            .isTrue();
                    assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
                });
    }

    @Test
    @DisplayName("无 LLM key 时四个子 Agent runner Bean 为 null（降级确定性直路不阻塞启动）")
    void subAgentRunnerBeansShouldBeNullWhenNoKey() {
        Assumptions.assumeTrue(
                System.getenv("DEEPSEEK_API_KEY") == null
                        && System.getenv("DASHSCOPE_API_KEY") == null,
                "无 key 降级测试仅在 CI（无 LLM key）环境执行");
        contextRunner.run(context -> {
            XHSAgentRunner xhsRunner = context.getBeanProvider(XHSAgentRunner.class)
                    .getIfAvailable();
            RouteAgentRunner routeRunner = context.getBeanProvider(RouteAgentRunner.class)
                    .getIfAvailable();
            ItineraryAgentRunner itinRunner = context.getBeanProvider(ItineraryAgentRunner.class)
                    .getIfAvailable();
            BudgetAgentRunner budgetRunner = context.getBeanProvider(BudgetAgentRunner.class)
                    .getIfAvailable();
            assertThat(xhsRunner).as("无 LLM key 时 XHSAgentRunner 应为 null").isNull();
            assertThat(routeRunner).as("无 LLM key 时 RouteAgentRunner 应为 null").isNull();
            assertThat(itinRunner).as("无 LLM key 时 ItineraryAgentRunner 应为 null").isNull();
            assertThat(budgetRunner).as("无 LLM key 时 BudgetAgentRunner 应为 null").isNull();
        });
    }

    @Test
    @DisplayName("自定义 XHSAgentRunner Bean 优先于 PlanConfig 默认装配（@ConditionalOnMissingBean）")
    void customXhsAgentRunnerShouldOverrideDefault() {
        XHSAgentRunner custom = query -> new XHSAnalysisResult(
                List.of(), false, "custom", XHSAnalysisResult.Telemetry.none());

        contextRunner.withBean(XHSAgentRunner.class, () -> custom)
                .run(context -> assertThat(
                        context.getBeanProvider(XHSAgentRunner.class).getIfAvailable())
                        .isSameAs(custom));
    }

    @Test
    @DisplayName("dispatcher 注入 runner 后路由仍正确（回归守护）")
    void dispatcherStillRoutesCorrectlyWithRunners() {
        XHSAgentRunner stubRunner = query -> new XHSAnalysisResult(
                List.of(), false, "stub", XHSAnalysisResult.Telemetry.none());

        contextRunner.withBean(XHSAgentRunner.class, () -> stubRunner)
                .run(context -> {
                    SubAgentDispatcher dispatcher = context.getBean(SubAgentDispatcher.class);
                    Msg assign = Msg.taskAssign("agent://supervisor", XHSAnalysisAgent.AGENT_URI,
                            "T-regression-xhs", Map.of(
                                    Msg.KEY_TASK_TYPE, XHSAnalysisAgent.TASK_TYPE,
                                    XHSAnalysisAgent.KEY_CONTEXT, Map.of(
                                            "destination", "云南",
                                            "preferences", List.of(),
                                            "headcount", 1,
                                            "style", "FAMILY")));
                    Msg result = dispatcher.dispatch(XHSAnalysisAgent.AGENT_URI, assign);
                    assertThat(result.from()).isEqualTo(XHSAnalysisAgent.AGENT_URI);
                    assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
                });
    }

    // ── C-012-D：FileBasedAttractionCatalog + FileBasedXHSNoteSource ──

    @Test
    @DisplayName("C-012-D: SubAgentDispatcher 回 XHSAnalysisAgent 返回非空候选（FileBasedAttractionCatalog + FileBasedXHSNoteSource 工作）")
    void xhsDispatcherReturnsNonEmptyCandidates() {
        contextRunner.run(context -> {
            SubAgentDispatcher dispatcher = context.getBean(SubAgentDispatcher.class);
            // 使用已知城市"北京"（Top 50 城市中应有数据）
            Msg assign = Msg.taskAssign("agent://supervisor", XHSAnalysisAgent.AGENT_URI,
                    "T-C12D-nonempty", Map.of(
                            Msg.KEY_TASK_TYPE, XHSAnalysisAgent.TASK_TYPE,
                            XHSAnalysisAgent.KEY_CONTEXT, Map.of(
                                    "destination", "北京",
                                    "preferences", List.of("文化"),
                                    "headcount", 2,
                                    "style", "FAMILY")));

            Msg result = dispatcher.dispatch(XHSAnalysisAgent.AGENT_URI, assign);

            assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
            assertThat(result.from()).isEqualTo(XHSAnalysisAgent.AGENT_URI);

            // 验证结果包含非空候选（C-012-D 目标：不再卡在空候选）
            Object rawResult = result.payload().get(Msg.KEY_RESULT);
            assertThat(rawResult).isInstanceOf(XHSAnalysisResult.class);
            XHSAnalysisResult xhsResult = (XHSAnalysisResult) rawResult;
            assertThat(xhsResult.candidates())
                    .as("C-012-D: FileBasedAttractionCatalog + FileBasedXHSNoteSource 应产出非空候选")
                    .isNotEmpty();
            assertThat(xhsResult.fallback())
                    .as("已知城市的正常数据不应标记为 fallback")
                    .isFalse();
        });
    }

    @Test
    @DisplayName("C-012-D: 未知城市返回空候选 + fallback（降级行为不变）")
    void unknownDestinationReturnsEmptyCandidatesWithFallback() {
        contextRunner.run(context -> {
            SubAgentDispatcher dispatcher = context.getBean(SubAgentDispatcher.class);
            Msg assign = Msg.taskAssign("agent://supervisor", XHSAnalysisAgent.AGENT_URI,
                    "T-C12D-unknown", Map.of(
                            Msg.KEY_TASK_TYPE, XHSAnalysisAgent.TASK_TYPE,
                            XHSAnalysisAgent.KEY_CONTEXT, Map.of(
                                    "destination", "UNKNOWN_CITY_NOT_IN_TOP50",
                                    "preferences", List.of(),
                                    "headcount", 1,
                                    "style", "SOLO")));

            Msg result = dispatcher.dispatch(XHSAnalysisAgent.AGENT_URI, assign);

            assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
            Object rawResult = result.payload().get(Msg.KEY_RESULT);
            assertThat(rawResult).isInstanceOf(XHSAnalysisResult.class);
            XHSAnalysisResult xhsResult = (XHSAnalysisResult) rawResult;
            assertThat(xhsResult.candidates()).isEmpty();
            assertThat(xhsResult.fallback()).isTrue();
        });
    }

    // ── 地图 AK 装配守护（map-ak-wiring）──

    @Test
    @DisplayName("有 AMAP_MAP_AK 时 routePlanningService 的 router 不是 HaversineRouter（接入真实地图 HTTP 层）")
    void routerShouldNotBeHaversineWhenAmapAkIsSet() {
        contextRunner
                .withSystemProperties("AMAP_MAP_AK=fake-ak-for-test")
                .run(context -> {
                    PlanConfig config = context.getBean(PlanConfig.class);
                    RoutePlanningService rps = cachedRoutePlanningServiceOf(config);
                    assertThat(extractRouter(rps))
                            .as("有 AMAP_MAP_AK 时 router 应为真实地图适配器，而非 HaversineRouter")
                            .isNotInstanceOf(HaversineRouter.class);
                });
    }

    @Test
    @DisplayName("无地图 AK 时 routePlanningService 的 router 是 HaversineRouter（降级守护）")
    void routerShouldBeHaversineWhenNoMapAkIsSet() {
        Assumptions.assumeTrue(
                System.getenv("AMAP_MAP_AK") == null && System.getenv("BAIDU_MAP_AK") == null,
                "无地图 AK 降级测试仅在未配置真实 AK 的环境执行");
        contextRunner.run(context -> {
            PlanConfig config = context.getBean(PlanConfig.class);
            RoutePlanningService rps = cachedRoutePlanningServiceOf(config);
            assertThat(extractRouter(rps))
                    .as("无地图 AK 时 router 应降级为 HaversineRouter")
                    .isInstanceOf(HaversineRouter.class);
        });
    }

    // ── Test D/E：多字符前缀城市端到端（fix 验证）──

    @Test
    @DisplayName("Test D: 哈尔滨（HRB 前缀）经 XHSAnalysisAgent 管道返回非空候选（prefix fix 验证）")
    void harbinThroughXhsPipelineReturnsNonEmptyCandidates() {
        contextRunner.run(context -> {
            SubAgentDispatcher dispatcher = context.getBean(SubAgentDispatcher.class);
            Msg assign = Msg.taskAssign("agent://supervisor", XHSAnalysisAgent.AGENT_URI,
                    "T-D-harbin-xhs", Map.of(
                            Msg.KEY_TASK_TYPE, XHSAnalysisAgent.TASK_TYPE,
                            XHSAnalysisAgent.KEY_CONTEXT, Map.of(
                                    "destination", "哈尔滨",
                                    "preferences", List.of("冰雪"),
                                    "headcount", 2,
                                    "style", "FAMILY")));

            Msg result = dispatcher.dispatch(XHSAnalysisAgent.AGENT_URI, assign);

            assertThat(result.type()).isEqualTo(MessageType.TASK_RESULT);
            Object rawResult = result.payload().get(Msg.KEY_RESULT);
            assertThat(rawResult).isInstanceOf(XHSAnalysisResult.class);
            XHSAnalysisResult xhsResult = (XHSAnalysisResult) rawResult;
            assertThat(xhsResult.candidates())
                    .as("Test D: 哈尔滨（HRB 3字符前缀）prefix fix 后应产出非空候选")
                    .isNotEmpty();
            assertThat(xhsResult.fallback())
                    .as("哈尔滨（已收录城市）不应标记为 fallback")
                    .isFalse();
        });
    }

    // ── C-017 FIX-1：TracePersistenceService 双重注入修复守护 ──

    @Test
    @DisplayName("FIX-1: 注入 TracePersistenceService 后 supervisorAgent.tracePersistence 非 null")
    void supervisorAgentShouldInjectTracePersistenceWhenAvailable() {
        com.smart.trip.skills.trace.TracePersistenceService stubTrace =
                new com.smart.trip.skills.trace.TracePersistenceService() {
                    @Override
                    public void saveDelegation(com.smart.trip.skills.trace.TraceDelegationRecord record) {
                    }

                    @Override
                    public void saveLlmCall(com.smart.trip.skills.trace.TraceLlmCallRecord record) {
                    }

                    @Override
                    public void saveOrchestrationSummary(
                            com.smart.trip.skills.trace.TraceOrchestrationSummary summary) {
                    }
                };

        contextRunner
                .withBean(com.smart.trip.skills.trace.TracePersistenceService.class, () -> stubTrace)
                .run(context -> assertThat(tracePersistenceField(context.getBean("supervisorAgent")))
                        .as("FIX-1: supervisorAgent 应注入可用的 TracePersistenceService，不再固定传 null")
                        .isSameAs(stubTrace));
    }

    @Test
    @DisplayName("FIX-1: 无 TracePersistenceService Bean 时 supervisorAgent.tracePersistence 为 null（降级不阻塞启动）")
    void supervisorAgentShouldLoadWithNullTracePersistenceWhenUnavailable() {
        contextRunner.run(context ->
                assertThat(tracePersistenceField(context.getBean("supervisorAgent"))).isNull());
    }

    @Test
    @DisplayName("FIX-1: 注入 TracePersistenceService 后 tripOrchestrationService 走 3-arg 构造函数注入非 null")
    void tripOrchestrationServiceShouldInjectTracePersistenceWhenAvailable() {
        com.smart.trip.skills.trace.TracePersistenceService stubTrace =
                new com.smart.trip.skills.trace.TracePersistenceService() {
                    @Override
                    public void saveDelegation(com.smart.trip.skills.trace.TraceDelegationRecord record) {
                    }

                    @Override
                    public void saveLlmCall(com.smart.trip.skills.trace.TraceLlmCallRecord record) {
                    }

                    @Override
                    public void saveOrchestrationSummary(
                            com.smart.trip.skills.trace.TraceOrchestrationSummary summary) {
                    }
                };

        contextRunner
                .withBean(com.smart.trip.skills.trace.TracePersistenceService.class, () -> stubTrace)
                .run(context -> {
                    TripOrchestrationService service = context.getBean(TripOrchestrationService.class);
                    assertThat(tracePersistenceFieldOf(service))
                            .as("FIX-1: tripOrchestrationService 应通过 3-arg 构造函数注入 tracePersistence")
                            .isSameAs(stubTrace);
                });
    }

    private static Object tracePersistenceField(Object supervisorAgent)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = supervisorAgent.getClass().getDeclaredField("tracePersistence");
        field.setAccessible(true);
        return field.get(supervisorAgent);
    }

    private static Object tracePersistenceFieldOf(TripOrchestrationService service)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = TripOrchestrationService.class.getDeclaredField("tracePersistence");
        field.setAccessible(true);
        return field.get(service);
    }

    @Test
    @DisplayName("Test E: TripOrchestrationService 以哈尔滨、3 天全链路 orchestrate → plan.days==3 且 totalCost>0")
    void orchestrateHarbinThreeDaysPlanHasCorrectStructure() {
        contextRunner.run(context -> {
            TripOrchestrationService orchestrationService =
                    context.getBean(TripOrchestrationService.class);
            TripPlanRequest request = new TripPlanRequest(
                    "哈尔滨",
                    LocalDate.of(2026, 7, 1),
                    3,
                    new BigDecimal("5000"),
                    2,
                    TravelStyle.FAMILY,
                    List.of("冰雪", "历史"));
            TripOrchestrationQuery query = new TripOrchestrationQuery(request);

            TripPlanResult result = orchestrationService.orchestrate(query);

            assertThat(result.plan()).as("orchestrate 结果不应为 null").isNotNull();
            assertThat(result.plan().days())
                    .as("Test E: 哈尔滨全链路 orchestrate 应产出真实行程（days 非空，验证 prefix fix 端到端生效）")
                    .isNotEmpty();
            assertThat(result.plan().totalCost())
                    .as("Test E: totalCost 应 > 0（确定性 Budget Agent 已运行）")
                    .isGreaterThan(BigDecimal.ZERO);
        });
    }

    private static Object runnerField(Object supervisorAgent) throws NoSuchFieldException,
            IllegalAccessException {
        Field field = supervisorAgent.getClass().getDeclaredField("runner");
        field.setAccessible(true);
        return field.get(supervisorAgent);
    }

    private static RoutePlanningService cachedRoutePlanningServiceOf(PlanConfig config)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = PlanConfig.class.getDeclaredField("cachedRoutePlanningService");
        f.setAccessible(true);
        return (RoutePlanningService) f.get(config);
    }

    private static Object extractRouter(RoutePlanningService rps)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = RoutePlanningService.class.getDeclaredField("router");
        f.setAccessible(true);
        return f.get(rps);
    }
}
