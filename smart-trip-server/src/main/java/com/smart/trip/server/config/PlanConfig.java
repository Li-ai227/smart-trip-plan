package com.smart.trip.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.agent.budget.BudgetAgent;
import com.smart.trip.agent.itinerary.ItineraryAgent;
import com.smart.trip.agent.route.RouteAgent;
import com.smart.trip.agent.supervisor.SupervisorAgent;
import com.smart.trip.agent.xhs.XHSAnalysisAgent;
import com.smart.trip.server.plan.facade.PdfExportFacade;
import com.smart.trip.server.plan.facade.TripPlanFacade;
import com.smart.trip.skills.pdf.PdfRenderService;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.server.plan.service.PlanIdSequenceService;
import com.smart.trip.skills.budget.BudgetAgentRunner;
import com.smart.trip.skills.env.EnvAccessor;
import com.smart.trip.skills.budget.BudgetCalculationService;
import com.smart.trip.skills.budget.BudgetCalculationTools;
import com.smart.trip.skills.budget.BudgetHarnessAgentFactory;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.InMemoryRateLimitStore;
import com.smart.trip.skills.governance.RateLimiter;
import com.smart.trip.skills.governance.RetryPolicy;
import com.smart.trip.skills.governance.Sleeper;
import com.smart.trip.skills.itinerary.ItineraryAgentRunner;
import com.smart.trip.skills.itinerary.ItineraryDesignService;
import com.smart.trip.skills.itinerary.ItineraryDesignTools;
import com.smart.trip.skills.itinerary.ItineraryHarnessAgentFactory;
import com.smart.trip.skills.itinerary.StubMealRecommender;
import com.smart.trip.skills.memory.MemoryClearService;
import com.smart.trip.skills.memory.MemoryRecaller;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.persistence.TripPlanPersistenceService;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.agent.route.mcp.MapHttpClient;
import com.smart.trip.agent.route.mcp.MapVendorFactory;
import com.smart.trip.skills.route.MapGeocoder;
import com.smart.trip.skills.route.MapRouter;
import com.smart.trip.skills.route.RouteAgentRunner;
import com.smart.trip.skills.route.RouteHarnessAgentFactory;
import com.smart.trip.skills.route.CatalogBasedPresetCoordinateCatalog;
import com.smart.trip.skills.route.HaversineRouter;
import com.smart.trip.skills.route.RoutePlanningService;
import com.smart.trip.skills.route.RoutePlanningTools;
import com.smart.trip.skills.supervisor.SubAgentDispatcher;
import com.smart.trip.skills.supervisor.SubAgentToolFactory;
import com.smart.trip.skills.supervisor.SupervisorAgentRunner;
import com.smart.trip.skills.supervisor.SupervisorHarnessAgentFactory;
import com.smart.trip.skills.supervisor.SupervisorNativeRunContext;
import com.smart.trip.skills.supervisor.TripOrchestrationService;
import com.smart.trip.skills.trace.TracePersistenceService;
import com.smart.trip.skills.xhs.AttractionCatalog;
import com.smart.trip.skills.xhs.XHSAgentRunner;
import com.smart.trip.skills.xhs.XHSAnalysisTools;
import com.smart.trip.skills.xhs.FileBasedAttractionCatalog;
import com.smart.trip.skills.xhs.FileBasedXHSNoteSource;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.skills.xhs.XHSHarnessAgentFactory;
import com.smart.trip.skills.xhs.XHSNoteAnalyzer;
import com.smart.trip.skills.xhs.XHSNoteCache;
import com.smart.trip.skills.xhs.XHSNoteSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 规划模块 Bean 装配（C-011 T-5/T-6）。
 *
 * <p>装配完整链路：AgentMetrics / OutputQualityGate / SubAgentDispatcher →
 * TripOrchestrationService → SupervisorAgent → PlanCacheService → TripPlanFacade。
 * 所有 Bean 均标注 {@code @ConditionalOnMissingBean}，测试 profile 的 Mock 替身优先。</p>
 */
@Configuration(proxyBeanMethods = false)
public class PlanConfig {

    private static final System.Logger LOG = System.getLogger(PlanConfig.class.getName());
    private static final Path SUPERVISOR_WORKSPACE = Path.of("target", "agentscope", "supervisor");

    // ── 服务实例缓存（确保 Supervisor Toolkit 与子 Agent *HarnessAgentFactory 共用同一实例）──

    private XHSNoteAnalyzer cachedXhsAnalyzer;
    private AttractionCatalog cachedAttractionCatalog;
    private Environment springEnv;
    private EnvAccessor cachedEnvAccessor;
    private AgentMetrics cachedAgentMetrics;
    private MapVendorFactory.VendorAdapters cachedVendorAdapters;
    private RoutePlanningService cachedRoutePlanningService;
    private ItineraryDesignService cachedItineraryDesignService;
    private BudgetCalculationService cachedBudgetCalculationService;
    private XHSNoteCache cachedXhsNoteCache;

    // ── 可观测 / 编排基础设施 ──

    /** Agent 指标埋点。同时捕获 Spring Environment 供 EnvAccessor 使用。 */
    @Bean
    @ConditionalOnMissingBean
    public AgentMetrics agentMetrics(MeterRegistry meterRegistry, Environment environment) {
        this.springEnv = environment;
        AgentMetrics metrics = new AgentMetrics(meterRegistry);
        this.cachedAgentMetrics = metrics;
        return metrics;
    }

    /**
     * 地图厂商适配器单例（P1-fix）：高德 MCP 主通道 + HTTP 降级通道（含真实 mcpClient）。
     *
     * <p>确定性路（{@link #routePlanningService()}）与 native 路（{@link #routeAgentRunner()}）
     * 共享同一 {@link MapVendorFactory.VendorAdapters} singleton，
     * 消除"null mcpClient → ChannelFailoverExecutor fallback"的重复警告（3次/请求）。</p>
     *
     * <p>MCP 初始化失败时自动降级 HTTP-only，不阻塞启动。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public MapVendorFactory.VendorAdapters mapVendorAdapters(AgentMetrics agentMetrics) {
        MapVendorFactory.VendorAdapters v = MapVendorFactory.fromEnvironment(
                new MapHttpClient(), agentMetrics, envAccessor());
        this.cachedVendorAdapters = v;
        return v;
    }

    /** 全局质量门禁。 */
    @Bean
    @ConditionalOnMissingBean
    public OutputQualityGate outputQualityGate() {
        return new OutputQualityGate();
    }

    /**
     * 异步编排专用线程池（C-013-C：隔离 ForkJoinPool.commonPool，避免线程饥饿）。
     * 核心线程数 = 2，最大不受限，keepAlive 60s，命名前缀 "trip-orch-"。
     */
    @Bean
    @ConditionalOnMissingBean(name = "tripPlanExecutor")
    public Executor tripPlanExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "trip-orch-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    }

    /** 子 Agent 委派器：native path 不可用时的真实 A2A fallback 链路。 */
    @Bean
    @ConditionalOnMissingBean
    public SubAgentDispatcher subAgentDispatcher(AgentMetrics agentMetrics,
            MapVendorFactory.VendorAdapters vendorAdapters,
            ObjectProvider<XHSAgentRunner> xhsRunnerProvider,
            ObjectProvider<RouteAgentRunner> routeRunnerProvider,
            ObjectProvider<ItineraryAgentRunner> itineraryRunnerProvider,
            ObjectProvider<BudgetAgentRunner> budgetRunnerProvider,
            ObjectProvider<XHSNoteCache> xhsNoteCacheProvider) {
        this.cachedXhsNoteCache = xhsNoteCacheProvider.getIfAvailable();
        XHSAgentRunner xhsRunner = xhsRunnerProvider.getIfAvailable();
        RouteAgentRunner routeRunner = routeRunnerProvider.getIfAvailable();
        ItineraryAgentRunner itineraryRunner = itineraryRunnerProvider.getIfAvailable();
        BudgetAgentRunner budgetRunner = budgetRunnerProvider.getIfAvailable();

        XHSAnalysisAgent xhsAgent = new XHSAnalysisAgent(xhsRunner, xhsAnalyzer(), agentMetrics);
        RouteAgent routeAgent = new RouteAgent(routeRunner, routePlanningService(), agentMetrics);
        ItineraryAgent itineraryAgent = new ItineraryAgent(itineraryRunner,
                itineraryDesignService(), agentMetrics);
        BudgetAgent budgetAgent = new BudgetAgent(budgetRunner, budgetCalculationService(),
                agentMetrics);
        return (agentUri, assign) -> switch (agentUri) {
            case XHSAnalysisAgent.AGENT_URI -> xhsAgent.receive(assign);
            case RouteAgent.AGENT_URI -> routeAgent.receive(assign);
            case ItineraryAgent.AGENT_URI -> itineraryAgent.receive(assign);
            case BudgetAgent.AGENT_URI -> budgetAgent.receive(assign);
            default -> throw new IllegalArgumentException("未知 Agent URI: " + agentUri);
        };
    }

    /**
     * 确定性顺序编排服务（含进度监听器装配）。
     *
     * <p>C-017 FIX-1：{@code tracePersistence} 是 {@code TripOrchestrationService} 的
     * {@code final} 字段，无 setter，必须经 3-arg 构造函数注入，否则审计 Trace 永远不落库。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public TripOrchestrationService tripOrchestrationService(
            SubAgentDispatcher subAgentDispatcher, OutputQualityGate outputQualityGate,
            ObjectProvider<PlanCacheService> planCacheServiceProvider,
            ObjectProvider<MemoryRecaller> memoryRecallerProvider,
            ObjectProvider<TracePersistenceService> tracePersistenceProvider) {
        TripOrchestrationService service = new TripOrchestrationService(
                subAgentDispatcher, outputQualityGate, tracePersistenceProvider.getIfAvailable());
        planCacheServiceProvider.ifAvailable(pcs ->
                service.setProgressListener(pcs::setStatus));
        memoryRecallerProvider.ifAvailable(service::setMemoryRecaller);
        return service;
    }

    /**
     * 主管 Agent。PlanCacheService 仅用于进度推送，不参与编排逻辑。
     *
     * <p>C-017 FIX-1：{@code tracePersistence} 此前固定传 {@code null}，导致审计 Trace
     * 三表从未实际写入；改为经 {@code ObjectProvider} 注入可用的 {@link TracePersistenceService}。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public SupervisorAgent supervisorAgent(TripOrchestrationService orchestrationService,
                                           AgentMetrics agentMetrics,
                                           OutputQualityGate outputQualityGate,
                                           ObjectProvider<SupervisorAgentRunner> runnerProvider,
                                           PlanCacheService planCacheService,
                                           ObjectProvider<MemoryRecaller> memoryRecallerProvider,
                                           ObjectProvider<TracePersistenceService> tracePersistenceProvider) {
        TracePersistenceService tracePersistence = tracePersistenceProvider.getIfAvailable();
        SupervisorAgentRunner runner = runnerProvider.getIfAvailable();
        if (runner == null) {
            runner = supervisorRunnerFromEnvironment(orchestrationService, outputQualityGate,
                    planCacheService, tracePersistence);
        }
        MemoryRecaller memoryRecaller = memoryRecallerProvider.getIfAvailable();
        return new SupervisorAgent(runner, orchestrationService, agentMetrics,
                tracePersistence, memoryRecaller);
    }

    // ── 子 Agent 原生 runner（AC：agent_spawn → HarnessAgent 路）──

    /** XHS Agent 原生 runner：有 LLM key 时装配 XHSHarnessAgentFactory，无 key 返回 null 降级确定性直路。 */
    @Bean
    @ConditionalOnMissingBean
    public XHSAgentRunner xhsAgentRunner() {
        try {
            Path workspace = Files.createTempDirectory("agent-xhs");
            return XHSHarnessAgentFactory.fromEnvironment(xhsAnalyzer(), workspace, envAccessor());
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "XHSHarnessAgentFactory 原生装配失败，降级确定性直路: {0}", ex.getMessage());
            return null;
        }
    }

    /** Route Agent 原生 runner。使用与确定性路共享的 VendorAdapters（MCP 主通道 + HTTP 降级）。 */
    @Bean
    @ConditionalOnMissingBean
    public RouteAgentRunner routeAgentRunner(MapVendorFactory.VendorAdapters vendorAdapters) {
        try {
            Path workspace = Files.createTempDirectory("agent-route");
            return RouteHarnessAgentFactory.fromEnvironment(routePlanningService(), workspace,
                    envAccessor());
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "RouteHarnessAgentFactory 原生装配失败，降级确定性直路: {0}", ex.getMessage());
            return null;
        }
    }

    /** Itinerary Agent 原生 runner。 */
    @Bean
    @ConditionalOnMissingBean
    public ItineraryAgentRunner itineraryAgentRunner() {
        try {
            Path workspace = Files.createTempDirectory("agent-itinerary");
            return ItineraryHarnessAgentFactory.fromEnvironment(itineraryDesignService(),
                    workspace, envAccessor());
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "ItineraryHarnessAgentFactory 原生装配失败，降级确定性直路: {0}",
                    ex.getMessage());
            return null;
        }
    }

    /** Budget Agent 原生 runner。 */
    @Bean
    @ConditionalOnMissingBean
    public BudgetAgentRunner budgetAgentRunner() {
        try {
            Path workspace = Files.createTempDirectory("agent-budget");
            return BudgetHarnessAgentFactory.fromEnvironment(budgetCalculationService(),
                    workspace, envAccessor());
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "BudgetHarnessAgentFactory 原生装配失败，降级确定性直路: {0}",
                    ex.getMessage());
            return null;
        }
    }

    // ── 规划缓存 / 门面 ──

    /** JSON 序列化（Spring Boot 已自动装配，此处兜底）。 */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    /**
     * 规划门面（编排 PlanCacheService + SupervisorAgent）。
     * PlanCacheService 由 {@link RedisServicesAutoConfiguration} 提供；
     * 不可用时（Redis 未就绪或测试替身）由 ObjectProvider 安全注入。
     *
     * <p>C-017 DECISION-5：{@code TripPlanPersistenceService} 经 ObjectProvider 注入，
     * 不可用时跳过历史规划持久化（降级）。T-9：{@code PlanIdSequenceService} 不可用时
     * 兜底为纯内存自增（传 null redisTemplate，内部自动降级）。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public TripPlanFacade tripPlanFacade(ObjectProvider<PlanCacheService> planCacheServiceProvider,
                                         SupervisorAgent supervisorAgent,
                                         ObjectMapper objectMapper,
                                         AgentMetrics agentMetrics,
                                         Executor tripPlanExecutor,
                                         ObjectProvider<MemoryClearService> memoryClearServiceProvider,
                                         ObjectProvider<TripPlanPersistenceService> persistenceProvider,
                                         ObjectProvider<PlanIdSequenceService> sequenceProvider) {
        PlanCacheService planCacheService = planCacheServiceProvider.getIfAvailable();
        if (planCacheService == null) {
            throw new IllegalStateException(
                    "PlanCacheService 不可用：请确保 Redis 已启动（trip.plan.redis.*）");
        }
        MemoryClearService memoryClearService = memoryClearServiceProvider.getIfAvailable();
        TripPlanPersistenceService tripPlanPersistenceService = persistenceProvider.getIfAvailable();
        PlanIdSequenceService planIdSequenceService =
                sequenceProvider.getIfAvailable(() -> new PlanIdSequenceService(null));
        return new TripPlanFacade(planCacheService, supervisorAgent, objectMapper, agentMetrics,
                tripPlanExecutor, memoryClearService, tripPlanPersistenceService, planIdSequenceService);
    }

    /** PDF 渲染服务（C-014 T-1）。 */
    @Bean
    @ConditionalOnMissingBean
    public PdfRenderService pdfRenderService() {
        return new PdfRenderService();
    }

    /** PDF 导出门面（C-014 T-2：状态门禁 + 所有权校验 + 委托渲染）。 */
    @Bean
    @ConditionalOnMissingBean
    public PdfExportFacade pdfExportFacade(ObjectProvider<PlanCacheService> planCacheServiceProvider,
                                           PdfRenderService pdfRenderService,
                                           ObjectMapper objectMapper) {
        PlanCacheService planCacheService = planCacheServiceProvider.getIfAvailable();
        if (planCacheService == null) {
            throw new IllegalStateException(
                    "PlanCacheService 不可用：请确保 Redis 已启动（trip.plan.redis.*）");
        }
        return new PdfExportFacade(planCacheService, pdfRenderService, objectMapper);
    }

    private SupervisorAgentRunner supervisorRunnerFromEnvironment(
            TripOrchestrationService orchestrationService, OutputQualityGate qualityGate,
            PlanCacheService planCacheService, TracePersistenceService tracePersistence) {
        try {
            // 进度推送：子 Agent @Tool 每次写入 typed result 时回调，前端轮询可见
            SupervisorNativeRunContext.ProgressListener progressListener =
                    planCacheService::setStatus;
            // C-017 FIX-3：透传 tracePersistence，使 native 成功路径也能补写子 Agent 委派审计
            return SupervisorHarnessAgentFactory.fromEnvironment(
                    orchestrationService, subAgentToolFactory(), qualityGate,
                    SUPERVISOR_WORKSPACE, envAccessor(), progressListener, tracePersistence);
        } catch (RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "SupervisorAgentRunner 原生装配失败，启动时降级确定性 fallback: {0}",
                    ex.getMessage());
            return null;
        }
    }

    /** C-012-C：每次 run() 创建全新工具实例，确保 artifact 不跨请求串扰。 */
    private SubAgentToolFactory subAgentToolFactory() {
        return (runContext) -> List.of(
                new XHSAnalysisTools(xhsAnalyzer()),
                new RoutePlanningTools(routePlanningService()),
                new ItineraryDesignTools(itineraryDesignService(), attractionCatalog()),
                new BudgetCalculationTools(budgetCalculationService()));
    }

    private XHSNoteAnalyzer xhsAnalyzer() {
        if (cachedXhsAnalyzer == null) {
            XHSNoteSource source = xhsNoteSource();
            // FileBasedXHSNoteSource 是本地文件计算，不走外部 API，无需限频；
            // 仅 XHSOpenApiNoteSource 真实调用外部 API 时才应用 governedExternalCaller
            GovernedExternalCaller caller = (source instanceof FileBasedXHSNoteSource)
                    ? new GovernedExternalCaller(new RetryPolicy(0, 1L), Sleeper.real())
                    : governedExternalCaller();
            XHSNoteCache cache = cachedXhsNoteCache != null ? cachedXhsNoteCache : emptyXhsCache();
            cachedXhsAnalyzer = new XHSNoteAnalyzer(source, cache,
                    attractionCatalog(), caller);
        }
        return cachedXhsAnalyzer;
    }

    private EnvAccessor envAccessor() {
        if (cachedEnvAccessor == null && springEnv != null) {
            cachedEnvAccessor = name -> springEnv.getProperty(name);
        }
        return cachedEnvAccessor != null ? cachedEnvAccessor : EnvAccessor.fromSystemEnv();
    }

    private XHSNoteSource xhsNoteSource() {
        // 小红书无对外数据开放平台，通过本地景点知识库完成（HttpClient 版 XHSOpenApiNoteSource
        // 已移除死代码，如需接入真实 API 请参考 git 历史 smart-trip-skills.xhs.HttpClient）
        return new FileBasedXHSNoteSource(attractionCatalog());
    }

    private static XHSNoteCache emptyXhsCache() {
        return new XHSNoteCache() {
            @Override
            public Optional<List<XHSNote>> get(String destination) {
                return Optional.empty();
            }

            @Override
            public void put(String destination, List<XHSNote> notes) {
                // 无持久化缓存适配前仅跳过写入；不伪造历史笔记。
            }
        };
    }

    private AttractionCatalog attractionCatalog() {
        if (cachedAttractionCatalog == null) {
            cachedAttractionCatalog = new FileBasedAttractionCatalog();
        }
        return cachedAttractionCatalog;
    }

    private RoutePlanningService routePlanningService() {
        if (cachedRoutePlanningService == null) {
            MapVendorFactory.VendorAdapters v = cachedVendorAdapters;
            if (v != null && (v.geocoder() != null || v.router() != null)) {
                MapGeocoder geocoder = v.geocoder() != null ? v.geocoder() : unavailableGeocoder();
                MapRouter router = v.router() != null ? v.router() : new HaversineRouter();
                cachedRoutePlanningService = new RoutePlanningService(geocoder, router, v.traffic(),
                        new CatalogBasedPresetCoordinateCatalog(attractionCatalog()),
                        governedExternalCaller());
            } else {
                LOG.log(System.Logger.Level.WARNING,
                        "无地图 AK（AMAP_MAP_AK/BAIDU_MAP_AK 均未设置），路线规划降级 Haversine");
                cachedRoutePlanningService = new RoutePlanningService(unavailableGeocoder(),
                        new HaversineRouter(), null,
                        new CatalogBasedPresetCoordinateCatalog(attractionCatalog()),
                        governedExternalCaller());
            }
        }
        return cachedRoutePlanningService;
    }

    private static MapGeocoder unavailableGeocoder() {
        return (address, city) -> {
            throw new IllegalStateException("地图地理编码适配器未配置");
        };
    }

    private ItineraryDesignService itineraryDesignService() {
        if (cachedItineraryDesignService == null) {
            cachedItineraryDesignService = new ItineraryDesignService(new StubMealRecommender());
        }
        return cachedItineraryDesignService;
    }

    private BudgetCalculationService budgetCalculationService() {
        if (cachedBudgetCalculationService == null) {
            cachedBudgetCalculationService = new BudgetCalculationService();
        }
        return cachedBudgetCalculationService;
    }

    private static GovernedExternalCaller governedExternalCaller() {
        return new GovernedExternalCaller(new RetryPolicy(0, 1L), Sleeper.real(),
                RateLimiter.withDefaults(new InMemoryRateLimitStore()));
    }
}
