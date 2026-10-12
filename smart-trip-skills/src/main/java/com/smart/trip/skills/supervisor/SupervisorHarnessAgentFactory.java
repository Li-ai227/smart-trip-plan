package com.smart.trip.skills.supervisor;

import com.smart.trip.common.dto.MemoryContext;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.env.EnvAccessor;
import com.smart.trip.skills.llm.LlmModelFactory;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.skills.quality.QualityGateResult;
import com.smart.trip.skills.quality.QualityViolation;
import com.smart.trip.skills.trace.TracePersistenceService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Supervisor 原生 Agent 工厂（C-010 v2 + C-012-C + C-018 Plan Mode）：组装真实
 * {@code HarnessAgent}（主 LLM + Toolkit（含全部 5 个 @Tool 类）+ Skill + SubagentsHook）
 * 并实现 {@link SupervisorAgentRunner}，让编排链真正经 AgentScope + 真实 LLM + agent_spawn
 * 跑通。C-018 新增计划模式规划态（{@code enablePlanMode()} + limited Toolkit）与恢复
 *（{@link #resume}）。双模型 ADR-009（deepseek-v4-pro 主 / qwen3-max 降级）。
 * 涉及真实 LLM/网络，单测不可离线覆盖，JaCoCo 排除。
 */
public final class SupervisorHarnessAgentFactory implements SupervisorAgentRunner, AutoCloseable {

    private static final System.Logger LOG =
            System.getLogger(SupervisorHarnessAgentFactory.class.getName());

    /**
     * 当前 run() 的 artifact 容器。子 Agent 的 @Tool 方法通过
     * {@link #activeRunContext()} 回写 typed result，使得 agent_spawn
     * 范式下的子 Agent 产出能被 Java 收口逻辑感知。
     *
     * <p>单次 run() 内 sync agent_spawn 不并发写；run() 结束清 null。</p>
     */
    private static final AtomicReference<SupervisorNativeRunContext> activeRunContextRef =
            new AtomicReference<>(null);

    /** @return 当前 run 的 artifact 容器，或 null（不在 run 生命周期内） */
    public static SupervisorNativeRunContext activeRunContext() {
        return activeRunContextRef.get();
    }

    private static final String SKILLS_RESOURCE_DIR = "skills";

    /** classpath 子 Agent 声明文件目录（src/main/resources/subagents/）。 */
    private static final String SUBAGENTS_RESOURCE_DIR = "subagents";

    /** workspace 下子 Agent 声明目录名。 */
    private static final String SUBAGENTS_WORKSPACE_DIR = "subagents";

    // 包级可见供测试断言（非 API，禁止外部依赖）
    static final String SYS_PROMPT =
            "你是智能旅游规划系统的主管 Agent（Supervisor），负责按固定顺序编排四个子 Agent。"
                    + "禁止使用 read_file/glob_files/grep_files 等文件工具——旅行规划不需要读取代码文件。"
                    + "禁止进入 Plan Mode。直接按以下顺序执行，不得跳过任何步骤：\n"
                    + "① agent_spawn xhs-analyst — 必须调用 analyze_xhs_notes 工具分析目的地小红书笔记，获取候选景点（禁止直接返回文本）\n"
                    + "② agent_spawn route-planner — 将第①步 analyze_xhs_notes 工具结果中的 candidates 字段（JSON 数组）"
                    + "原样作为 attractions_json 参数传入，规划路线\n"
                    + "③ agent_spawn itinerary-designer — 将第①步 analyze_xhs_notes 工具结果中的 candidates 字段作为 attractions_json、"
                    + "第②步路线结果作为 route_result_json 参数传入，编排行程\n"
                    + "④ agent_spawn budget-controller — 必须调用 calculate_budget 工具核算费用（禁止直接返回文本），传入第③步行程结果\n"
                    + "⑤ finalize_native_trip_plan — 收口，产出最终 TripPlanResult\n"
                    + "任一子 Agent 返回降级结果时继续下一步，不得中断。"
                    + "业务不变量由各子 Agent 工具强制，你仅负责传递上下文与收口。\n"
                    + "【严禁】禁止在 native 路中主动调用 orchestrate_trip——该工具仅由系统在无 LLM key 或 native 完全失败时自动调用，"
                    + "主动调用会导致编排被重复执行两次，产生错误结果。\n"
                    + "【严禁】若四个子 Agent 已全部完成（artifact 齐全），只调用 finalize_native_trip_plan 收口，禁止再调 orchestrate_trip。\n"
                    + "【严禁】若子 Agent 返回空结果或 fallback=true，停止当前路径并等待，由 Java 层决策是否 fallback，"
                    + "禁止自行重试、禁止重复调用 orchestrate_trip、禁止调用 agent_list/task_list 探查状态。";

    private final Model primaryModel;
    private final Model fallbackModel;
    private final TripOrchestrationService service;
    private final SubAgentToolFactory toolFactory;
    private final OutputQualityGate qualityGate;
    private final ClasspathSkillRepository skillRepository;
    private final Path workspace;
    private final SupervisorNativeRunContext.ProgressListener progressListener;
    private final TracePersistenceService tracePersistence;

    /** 向后兼容重载（{@code tracePersistence=null}，跳过 native 委派审计）。 */
    public SupervisorHarnessAgentFactory(Model primaryModel, Model fallbackModel,
                                          TripOrchestrationService service,
                                          SubAgentToolFactory toolFactory,
                                          OutputQualityGate qualityGate,
                                          Path workspace,
                                          SupervisorNativeRunContext.ProgressListener progressListener) {
        this(primaryModel, fallbackModel, service, toolFactory, qualityGate, workspace,
                progressListener, null);
    }

    /**
     * 完整参数构造（含审计追踪，C-017 FIX-3）。参数非 null 校验+异常参见 {@link #run}。
     * @param primaryModel     主 LLM 模型（如 deepseek-v4-pro），非 null
     * @param fallbackModel    降级 LLM 模型（如 qwen3-max），可为 null
     * @param service          确定性编排 Skill（承载全部业务不变量），非 null
     * @param toolFactory      子 Agent @Tool 实例工厂（每次 run 创建新实例，保证 artifact 隔离），非 null
     * @param qualityGate      全局复核门禁（native finalizer 复用），非 null
     * @param workspace        HarnessAgent 本地 workspace 目录，非 null
     * @param progressListener 进度监听器，可为 null（null 时跳过进度推送）
     * @param tracePersistence 审计追踪持久化，可为 null（null 时跳过 native 委派审计）
     */
    public SupervisorHarnessAgentFactory(Model primaryModel, Model fallbackModel,
                                          TripOrchestrationService service,
                                          SubAgentToolFactory toolFactory,
                                          OutputQualityGate qualityGate,
                                          Path workspace,
                                          SupervisorNativeRunContext.ProgressListener progressListener,
                                          TracePersistenceService tracePersistence) {
        if (primaryModel == null) {
            throw new IllegalArgumentException("primaryModel 不可为 null");
        }
        if (service == null) {
            throw new IllegalArgumentException("service 不可为 null");
        }
        if (toolFactory == null) {
            throw new IllegalArgumentException("toolFactory 不可为 null");
        }
        if (qualityGate == null) {
            throw new IllegalArgumentException("qualityGate 不可为 null");
        }
        if (workspace == null) {
            throw new IllegalArgumentException("workspace 不可为 null");
        }
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.service = service;
        this.toolFactory = toolFactory;
        this.qualityGate = qualityGate;
        this.workspace = workspace;
        this.progressListener = progressListener;
        this.tracePersistence = tracePersistence;
        try {
            this.skillRepository = new ClasspathSkillRepository(SKILLS_RESOURCE_DIR);
        } catch (Exception ex) {
            throw new IllegalStateException("加载 classpath skills 失败: " + SKILLS_RESOURCE_DIR, ex);
        }
    }

    /**
     * 用环境变量构造工厂（ADR-009）。两者皆缺则返回 {@code null}（走纯确定性直路）。
     * @deprecated 使用 {@link #fromEnvironment(TripOrchestrationService, SubAgentToolFactory, OutputQualityGate, Path, EnvAccessor, SupervisorNativeRunContext.ProgressListener)}
     */
    @Deprecated
    public static SupervisorHarnessAgentFactory fromEnvironment(
            TripOrchestrationService service, SubAgentToolFactory toolFactory,
            OutputQualityGate qualityGate, Path workspace) {
        return fromEnvironment(service, toolFactory, qualityGate, workspace,
                EnvAccessor.fromSystemEnv(), null, null);
    }

    /** 向后兼容重载（{@code tracePersistence=null}，跳过 native 委派审计）。 */
    public static SupervisorHarnessAgentFactory fromEnvironment(
            TripOrchestrationService service, SubAgentToolFactory toolFactory,
            OutputQualityGate qualityGate, Path workspace, EnvAccessor env,
            SupervisorNativeRunContext.ProgressListener progressListener) {
        return fromEnvironment(service, toolFactory, qualityGate, workspace, env,
                progressListener, null);
    }

    /** 用环境变量构造工厂（含审计追踪，C-017 FIX-3）。两者皆缺 → null（走纯确定性直路）。 */
    public static SupervisorHarnessAgentFactory fromEnvironment(
            TripOrchestrationService service, SubAgentToolFactory toolFactory,
            OutputQualityGate qualityGate, Path workspace, EnvAccessor env,
            SupervisorNativeRunContext.ProgressListener progressListener,
            TracePersistenceService tracePersistence) {
        LlmModelFactory.ResolvedModels models = LlmModelFactory.fromEnvironment(env);
        if (!models.hasAny()) {
            return null;
        }
        return new SupervisorHarnessAgentFactory(
                models.preferred(), models.runtime(), service, toolFactory, qualityGate, workspace,
                progressListener, tracePersistence);
    }

    @Override
    public TripPlanResult run(TripOrchestrationQuery query) throws Exception {
        // 1. 计算本次 run 专属 workspace（planId 子目录），隔离跨请求对话历史
        Path runWorkspace = resolveRunWorkspace(query.planId());
        // 清除上一轮残留的 agent_state.json，防止跨请求对话历史污染导致子 Agent 跳过 tool 调用
        deleteIfExists(runWorkspace);
        prepareSubagentWorkspace(runWorkspace);

        // C-018 AC-2：计划模式规划态与固定路径分叉——规划态用受限 Toolkit 只读起草计划，
        // 不在本次调用内委派任何子 Agent（委派留给 plan_exit 批准后的 resume()）。
        if (query.request() != null && Boolean.TRUE.equals(query.request().planMode())) {
            return runPlanningPhase(query, runWorkspace);
        }
        // 2~4. 组装 per-run artifact 容器 + 完整业务 Toolkit + HarnessAgent（提取自
        // resume() 复用，避免 PMD CPD 重复代码告警）
        ExecutionAssembly assembly = assembleExecutionToolkit(query.planId());
        HarnessAgent.Builder builder = executionAgentBuilder(assembly.toolkit(), runWorkspace);
        try (HarnessAgent agent = builder.build()) {
            agent.call(buildPrompt(query), SupervisorPlanModeSupport.sessionContext(query)).block();
        } finally {
            activeRunContextRef.set(null); // 清掉，不泄漏到下一次请求
        }

        // 5. 收口与校验
        return collectNativeResult(query, assembly.runContext(), assembly.orchestrationTools());
    }

    /** run()/resume() 共用的执行态 Toolkit 组装结果（提取以消除 PMD CPD 重复代码告警）。 */
    private record ExecutionAssembly(SupervisorNativeRunContext runContext,
            TripOrchestrationTools orchestrationTools, Toolkit toolkit) {
    }

    /**
     * 创建 per-run artifact 容器 + 完整业务 Toolkit（确定性编排 @Tool + 全部子 Agent @Tool）。{@code run()}（固定路径）与 {@code resume()}（计划模式恢复执行）共用此组装逻辑。
     * 该方法负责为每次执行（run 或 resume）组装完整的工具集，包含：
     * {@link SupervisorNativeRunContext} — 本次执行的运行时上下文，承载 planId、进度监听器、子 Agent 执行结果等
     * {@link TripOrchestrationTools} — 编排层核心工具，封装与 Service、质量门禁的交互
     * 子 Agent 工具集 — 由 {@code toolFactory.createTools(runContext)} 创建的全部子 Agent @Tool 方法
     * 关键设计：RunContext 通过 {@link #activeRunContextRef} 暴露，供子 Agent 的 @Tool 方法回写 typed result，Toolkit 聚合所有工具，统一注册后供 Agent 调用，run 和 resume 共用此组装逻辑，保证工具集一致性
     * @param planId 计划 ID，用于标识本次执行的归属
     * @return {@link ExecutionAssembly} 包含 runContext、orchestrationTools、toolkit 的组装结果
     */
    private ExecutionAssembly assembleExecutionToolkit(String planId) {
        // 1. 创建本次执行的运行时上下文
        SupervisorNativeRunContext runContext = new SupervisorNativeRunContext();
        runContext.setPlanId(planId);
        runContext.setProgressListener(progressListener);
        activeRunContextRef.set(runContext); // 挂上，供子 Agent 的 @Tool 方法回写 typed result
        // 2. 创建子 Agent 工具集（由 toolFactory 统一生产）
        List<Object> runTools = toolFactory.createTools(runContext);
        // 3. 创建编排层核心工具（封装 Service 调用 + 质量门禁）
        TripOrchestrationTools orchestrationTools = new TripOrchestrationTools(
                service, runContext, qualityGate);
        // 4. 聚合所有工具到 Toolkit
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(orchestrationTools);
        for (Object tool : runTools) {
            toolkit.registerTool(tool);
        }
        // 5. 返回组装结果
        return new ExecutionAssembly(runContext, orchestrationTools, toolkit);
    }

    /**
     * 构建执行态（固定路径 / 计划模式恢复）的 {@code HarnessAgent.Builder}。
     *
     * <p>disableFilesystemTools()：框架层阻止 LLM 调用 read_file/glob_files/grep_files，
     * 防止 LLM 读取源码文件浪费 66s 并跳过 XHS（C-012-fix 方案 C）。不调
     * enableTaskList(true)：Plan Mode 是 LLM 迷失的根源，Supervisor 不需要计划模式。</p>
     */
    private HarnessAgent.Builder executionAgentBuilder(Toolkit toolkit, Path runWorkspace) {
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("supervisor")
                .sysPrompt(SYS_PROMPT)
                .model(primaryModel)
                .toolkit(toolkit)
                .skillRepository(skillRepository)
                .workspace(runWorkspace)
                .disableFilesystemTools();
        if (fallbackModel != null) {
            builder.fallbackModel(fallbackModel);
        }
        return builder;
    }

    private TripPlanResult runPlanningPhase(TripOrchestrationQuery query, Path runWorkspace)
            throws Exception {
        // 1. 构建空 Toolkit + enablePlanMode
        Toolkit toolkit = new Toolkit();
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("supervisor")
                .sysPrompt(SupervisorPlanModeSupport.PLAN_SYS_PROMPT)
                .model(primaryModel)
                .toolkit(toolkit)
                .skillRepository(skillRepository)
                .workspace(runWorkspace)
                .disableFilesystemTools()
                .enablePlanMode();  // ← 框架注册 plan_write/plan_enter/plan_exit
        if (fallbackModel != null) {
            builder.fallbackModel(fallbackModel);
        }
        RuntimeContext ctx = SupervisorPlanModeSupport.sessionContext(query);
        // 读取 workspace 下的计划草案（必须在 agent close 前读取，因为 PlanModeManager
        // 通过 WorkspaceManager 写入 ShellAwareOverlay，Files.readString() 读不到）
        final String draftMarkdown;
        try (HarnessAgent agent = builder.build()) {
            // T-14：官方 programmatic API — 程序化进入 Plan Mode（无需 LLM 调 plan_enter）
            // 2. 程序化进入 Plan Mode
            agent.enterPlanMode(ctx); // ← ② enterPlanMode
            // LLM 仅调 plan_write 起草计划后停止（不调 plan_exit）
            // 3. LLM 调用 plan_write 起草计划
            agent.call(SupervisorPlanModeSupport.buildPlanPrompt(query), ctx).block();  // ← ③ call(planPrompt)
            // 通过 WorkspaceManager 读取，确保走 ShellAwareOverlay 文件系统层
            // 4. 通过 WorkspaceManager 读取草案
            draftMarkdown = agent.getWorkspaceManager()
                    .readManagedWorkspaceFileUtf8(ctx, "plans/PLAN.md"); // ← ④ 读取草案
        }
        if (draftMarkdown == null || draftMarkdown.isBlank()) {
            return null; // LLM 未产出有效草案 → 降级
        }
        PlanDraftPause pause = new PlanDraftPause(draftMarkdown, "");
        return new TripPlanResult(null, false, List.of(), false, TripPlanResult.Telemetry.none(),
                TripPlanResult.PATH_NATIVE, false, pause);
    }

    /**
     * 恢复计划模式暂停的规划（C-018 AC-4 + T-14 重写）。
     *
     * <p>T-14：不再使用 ConfirmResult 重放，改用官方
     * {@code agent.exitPlanMode(ctx)} 程序化退出 Plan Mode 后直接执行。</p>
     */
    public TripPlanResult resume(TripOrchestrationQuery query, PlanDraftPause pause)
            throws Exception {
        Path runWorkspace = resolveRunWorkspace(query.planId());
        prepareSubagentWorkspace(runWorkspace);

        // 组装完整业务 Toolkit + HarnessAgent（复用 run() 的执行态组装逻辑）
        ExecutionAssembly assembly = assembleExecutionToolkit(query.planId());
        HarnessAgent.Builder builder = executionAgentBuilder(assembly.toolkit(), runWorkspace);
        RuntimeContext ctx = SupervisorPlanModeSupport.sessionContext(query);
        try (HarnessAgent agent = builder.build()) {
            // T-14：官方 API — 程序化退出 Plan Mode，然后正常执行四步编排
            agent.exitPlanMode(ctx);  // 程序化退出 Plan Mode
            agent.call(buildPrompt(query), ctx).block(); // 四步编排
        } finally {
            activeRunContextRef.set(null);
        }
        return collectNativeResult(query, assembly.runContext(), assembly.orchestrationTools());
    }

    /**
     * 收口并校验 native 结果：提前 fallback 守卫 → 读 orchestrationTools.lastResult()
     * 或从 runContext 组装 → dataFlowBroken 检查 → 返回结果或 null。
     */
    private TripPlanResult collectNativeResult(TripOrchestrationQuery query,
            SupervisorNativeRunContext runContext, TripOrchestrationTools orchestrationTools) {
        // 提前检测：XHS 有 ≥2 候选但 route 报"景点数不足"→ 参数解析失败，立即 fallback
        if (NativeRouteParseGuard.isRouteParseFailure(runContext)) {
            LOG.log(System.Logger.Level.WARNING,
                    "native route parse failure: xhsCandidates={0}, routeMessage={1}",
                    runContext.xhsResult().candidates().size(),
                    runContext.routeResult().message());
            return null;
        }

        TripPlanResult nativeResult = orchestrationTools.lastResult();
        if (nativeResult == null && runContext.isComplete()) {
            boolean memFb = query.memoryContext() != null && query.memoryContext().memoryFallback();
            nativeResult = finalizeNative(query.planId(), query.request(), runContext, memFb,
                    query.traceId());
            orchestrationTools.setLastResult(nativeResult);
        }
        // C-013-timeout-dataflow：XHS 已产出候选但最终 plan.days 为空 → 返回 null
        if (nativeResult != null && NativeDataFlowGuard.isDataFlowBroken(runContext, nativeResult)) {
            return null;
        }
        return nativeResult; // null = 不完整，由 SupervisorAgent.resolve() 统一 fallback
    }

    /**
     * Native finalizer：从四个 subagent typed artifact 组装 TripPlanResult。
     * 不调用 {@code TripOrchestrationService.orchestrate()}，仅使用 {@link TripPlanAssembler}
     * + {@link OutputQualityGate}。traceId 可为 null（跳过审计）。
     */
    TripPlanResult finalizeNative(
            String planId,
            com.smart.trip.common.dto.TripPlanRequest request,
            SupervisorNativeRunContext ctx,
            boolean memoryFallback,
            String traceId) {
        long startNanos = System.nanoTime();

        // 组装 TripPlan（复用 TripPlanAssembler，不 dispatch；planId 由上游透传）
        var plan = TripPlanAssembler.assemble(
                planId, request, ctx.itineraryResult(), ctx.budgetResult());

        // QualityGate 复核（复用确定性规则）
        QualityGateResult gateResult = qualityGate.review(plan);
        List<String> hitlReasons = gateResult.violations().stream()
                .map(QualityViolation::detail).collect(Collectors.toList());

        // 统计子步骤 fallback
        int fallbackCount = 0;
        if (ctx.xhsResult() != null && ctx.xhsResult().fallback()) { fallbackCount++; }
        if (ctx.routeResult() != null && ctx.routeResult().fallback()) { fallbackCount++; }
        if (ctx.itineraryResult() != null && ctx.itineraryResult().fallback()) { fallbackCount++; }
        if (ctx.budgetResult() != null && ctx.budgetResult().fallback()) { fallbackCount++; }

        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        boolean anyFallback = fallbackCount > 0;

        // C-017 FIX-3：native 成功不再绕开审计——补写四条子 Agent 委派记录，
        // 与确定性/降级路（TripOrchestrationService#traceDelegation）产出同一张表的数据对齐。
        NativeDelegationTracer.trace(tracePersistence, traceId, planId, ctx);

        return new TripPlanResult(plan, gateResult.requiresHITL(), hitlReasons,
                anyFallback,
                new TripPlanResult.Telemetry(fallbackCount, gateResult.requiresHITL(), latencyMs),
                TripPlanResult.PATH_NATIVE,
                memoryFallback);
    }

    /** 返回本次 run() 的专属 workspace 路径（基础路径下以 planId 命名的子目录）。 */
    Path resolveRunWorkspace(String planId) {
        return workspace.resolve(planId);
    }

    /** 递归删除目录及内容，防止跨请求对话历史污染。 */
    private static void deleteIfExists(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            var sorted = stream.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path p : sorted) {
                Files.deleteIfExists(p);
            }
        }
    }

    /** 将 classpath {@code subagents/*.md} 复制到 workspace（始终覆盖）。 */
    private void prepareSubagentWorkspace(Path runWorkspace) throws IOException {
        Path targetDir = runWorkspace.resolve(SUBAGENTS_WORKSPACE_DIR);
        Files.createDirectories(targetDir);

        String[] declarations = {
                "xhs-analyst.md",
                "route-planner.md",
                "itinerary-designer.md",
                "budget-controller.md"
        };

        for (String filename : declarations) {
            Path target = targetDir.resolve(filename);
            String resourcePath = SUBAGENTS_RESOURCE_DIR + "/" + filename;
            try (InputStream in = getClass().getClassLoader()
                    .getResourceAsStream(resourcePath)) {
                if (in != null) {
                    // 始终覆盖：classpath 资源是真相源，workspace 旧副本会导致子 Agent 行为不一致
                    Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    throw new IOException("classpath 资源不存在: " + resourcePath);
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        skillRepository.close();
    }

    // 包级可见供测试断言（非 API，禁止外部依赖）
    static Msg buildPrompt(TripOrchestrationQuery query) {
        String requestJson = JsonUtils.toJson(query.request());
        StringBuilder sb = new StringBuilder("请为以下旅行规划请求完成端到端编排：")
                .append(requestJson).append("\n");

        // C-015 T-6：注入记忆召回上下文（弱增强，不覆盖硬约束）
        MemoryContext mc = query.memoryContext();
        if (mc != null && !mc.isEmpty() && !mc.memoryFallback()) {
            sb.append("\n### 用户历史参考（当前请求优先，历史仅作参考）\n");
            if (!mc.userProfiles().isEmpty()) {
                sb.append("历史偏好：").append(String.join("; ", mc.userProfiles())).append("\n");
            }
            if (!mc.historyPlans().isEmpty()) {
                sb.append("历史行程：").append(String.join("; ", mc.historyPlans())).append("\n");
            }
            if (!mc.attractionKb().isEmpty()) {
                sb.append("景点知识：").append(String.join("; ", mc.attractionKb())).append("\n");
            }
            sb.append("【注意】历史参考不覆盖当前请求的目的地、日期、预算、人数等硬约束。\n");
        }

        String text = sb.toString()
                + "严格按顺序执行以下步骤，禁止跳过，禁止读取文件：\n"
                + "1. agent_spawn xhs-analyst — 必须调用 analyze_xhs_notes 工具分析目的地小红书笔记，获取候选景点\n"
                + "2. agent_spawn route-planner — 将第 1 步 analyze_xhs_notes 工具结果中的 candidates 字段（JSON 数组）"
                + "原样作为 attractions_json 参数传入，规划路线\n"
                + "3. agent_spawn itinerary-designer — 将第 1 步 analyze_xhs_notes 工具结果中的 candidates 字段作为 attractions_json、"
                + "第 2 步路线结果作为 route_result_json 参数传入，编排行程\n"
                + "4. agent_spawn budget-controller — 必须调用 calculate_budget 工具核算费用，传入第 3 步行程结果\n"
                + "5. finalize_native_trip_plan — 收口，产出最终 TripPlanResult\n"
                + "任一步返回降级结果时继续下一步，最终确保产出完整行程方案。";
        return Msg.builder().role(MsgRole.USER).textContent(text).build();
    }
}
