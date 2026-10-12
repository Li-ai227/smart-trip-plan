package com.smart.trip.tests.e2e;

import com.smart.trip.agent.budget.BudgetAgent;
import com.smart.trip.agent.itinerary.ItineraryAgent;
import com.smart.trip.agent.route.RouteAgent;
import com.smart.trip.agent.supervisor.SupervisorAgent;
import com.smart.trip.agent.xhs.XHSAnalysisAgent;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.skills.budget.BudgetCalculationService;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RetryPolicy;
import com.smart.trip.skills.governance.Sleeper;
import com.smart.trip.skills.itinerary.ItineraryDesignService;
import com.smart.trip.skills.itinerary.StubMealRecommender;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.skills.route.MapGeocoder;
import com.smart.trip.skills.route.MapRouter;
import com.smart.trip.skills.route.PresetCoordinateCatalog;
import com.smart.trip.skills.route.RoutePlanningService;
import com.smart.trip.skills.supervisor.SubAgentDispatcher;
import com.smart.trip.skills.supervisor.TripOrchestrationService;
import com.smart.trip.skills.xhs.AttractionCatalog;
import com.smart.trip.skills.xhs.XHSNoteAnalyzer;
import com.smart.trip.skills.xhs.XHSNoteCache;
import com.smart.trip.skills.xhs.XHSNoteSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.smart.trip.skills.governance.InMemoryRateLimitStore;
import com.smart.trip.skills.governance.RateLimitRule;
import com.smart.trip.skills.governance.RateLimiter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * E2E Agent 装配（C-012 要素二）：真实 Agent 链路 + 内存桩端口，CI 不联网。
 *
 * <p>用 {@code @Primary} 覆盖 {@code PlanConfig} 中的 Bean，注入确定性分发器：
 * XHS → XHSAnalysisAgent，路线 → RouteAgent，行程 → ItineraryAgent，预算 → BudgetAgent。
 * 所有 Agent 使用内存桩端口（XHSNoteSource/MapGeocoder 等），不发起真实 HTTP 调用。</p>
 */
@TestConfiguration
public class E2eAgentConfig {

    /** 云南景点目录（含评分 ≥ 3.5）。 */
    static final List<Attraction> YUNNAN_ATTRACTIONS = List.of(
            new Attraction("A1", "石林", geo(103.3, 24.8, "石林"), 4.5, 120,
                    new BigDecimal("120"), List.of("自然")),
            new Attraction("A2", "丽江古城", geo(100.2, 26.8, "丽江古城"), 4.8, 180,
                    new BigDecimal("80"), List.of("文化")),
            new Attraction("A3", "大理古城", geo(100.1, 25.6, "大理古城"), 4.6, 150,
                    new BigDecimal("0"), List.of("历史")),
            new Attraction("A4", "洱海", geo(100.2, 25.8, "洱海"), 4.7, 90,
                    new BigDecimal("0"), List.of("湖泊")),
            new Attraction("A5", "玉龙雪山", geo(100.2, 27.1, "玉龙雪山"), 4.9, 240,
                    new BigDecimal("215"), List.of("雪山"))
    );

    private static GeoLocation geo(double lng, double lat, String name) {
        return new GeoLocation(lng, lat, name, "云南");
    }

    /** 正常 XHS 桩：返回云南景点笔记。 */
    static XHSNoteSource normalXhsSource() {
        XHSNote note = new XHSNote(
                "N1", "云南必去景点", "云南旅游攻略正文", List.of("旅游", "云南"), 9800,
                List.of(
                        new AttractionMention("A1", com.smart.trip.common.enums.SentimentType.POSITIVE),
                        new AttractionMention("A2", com.smart.trip.common.enums.SentimentType.POSITIVE),
                        new AttractionMention("A3", com.smart.trip.common.enums.SentimentType.POSITIVE),
                        new AttractionMention("A4", com.smart.trip.common.enums.SentimentType.POSITIVE),
                        new AttractionMention("A5", com.smart.trip.common.enums.SentimentType.POSITIVE)
                ));
        return query -> List.of(note);
    }

    /** 景点目录：按 ID 查找。 */
    static AttractionCatalog attractionCatalog() {
        Map<String, Attraction> catalog = Map.of(
                "A1", YUNNAN_ATTRACTIONS.get(0),
                "A2", YUNNAN_ATTRACTIONS.get(1),
                "A3", YUNNAN_ATTRACTIONS.get(2),
                "A4", YUNNAN_ATTRACTIONS.get(3),
                "A5", YUNNAN_ATTRACTIONS.get(4)
        );
        return id -> Optional.ofNullable(catalog.get(id));
    }

    /** 空缓存桩。 */
    static XHSNoteCache emptyCache() {
        return new XHSNoteCache() {
            @Override
            public Optional<List<XHSNote>> get(String destination) {
                return Optional.empty();
            }

            @Override
            public void put(String destination, List<XHSNote> notes) {
            }
        };
    }

    /** 正常路线桩：直接返回合法 Route（durationMin≤120）。 */
    static MapRouter normalRouter() {
        return (from, to) -> new com.smart.trip.common.model.Route(
                from, to,
                Math.abs(from.longitude() - to.longitude()) * 100,
                60,
                "driving",
                false);
    }

    /** 降级地理编码桩：始终抛异常，触发 haversine 估算（C-012 Case-3）。 */
    static MapGeocoder failingGeocoder() {
        return (address, city) -> {
            throw new RuntimeException("地图 API 5xx 注入");
        };
    }

    /** 正常地理编码桩：景点已含坐标，不需要 geocode，此桩也可抛异常。 */
    static MapGeocoder normalGeocoder() {
        return (address, city) -> {
            throw new RuntimeException("geocode 未期望被调用");
        };
    }

    /** 预置坐标目录（降级兜底）。 */
    static PresetCoordinateCatalog presetCatalog() {
        return name -> YUNNAN_ATTRACTIONS.stream()
                .filter(a -> a.name().equals(name))
                .map(Attraction::location)
                .findFirst();
    }

    /** 治理执行器（不限频、重试 0 次，测试快速失败）。 */
    static GovernedExternalCaller fastCaller() {
        RateLimiter limiter = new RateLimiter(
                new InMemoryRateLimitStore(),
                Map.of(
                        RateLimiter.API_MAP, new RateLimitRule(RateLimiter.API_MAP, 1000, 60),
                        RateLimiter.API_XHS, new RateLimitRule(RateLimiter.API_XHS, 1000, 60)));
        return new GovernedExternalCaller(new RetryPolicy(0, 1L), Sleeper.real(), limiter);
    }

    @Bean
    public AgentMetrics agentMetrics() {
        return new AgentMetrics(new SimpleMeterRegistry());
    }

    @Bean
    public OutputQualityGate outputQualityGate() {
        return new OutputQualityGate();
    }

    /** 正常场景分发器（覆盖 PlanConfig.subAgentDispatcher 占位桩）。 */
    @Bean
    public SubAgentDispatcher subAgentDispatcher(AgentMetrics metrics) {
        XHSAnalysisAgent xhsAgent = buildXhsAgent(normalXhsSource(), metrics);
        RouteAgent routeAgent = buildRouteAgent(normalGeocoder(), normalRouter(), metrics);
        ItineraryAgent itinAgent = buildItineraryAgent(metrics);
        BudgetAgent budgetAgent = buildBudgetAgent(metrics);
        return buildDispatcher(xhsAgent, routeAgent, itinAgent, budgetAgent);
    }

    @Bean
    public TripOrchestrationService tripOrchestrationService(
            SubAgentDispatcher dispatcher, OutputQualityGate qualityGate) {
        return new TripOrchestrationService(dispatcher, qualityGate);
    }

    @Bean
    public SupervisorAgent supervisorAgent(
            TripOrchestrationService orchestrationService, AgentMetrics metrics) {
        return new SupervisorAgent(orchestrationService, metrics);
    }

    // ===== 静态工厂方法（场景测试可复用）=====

    /** 构造正常 XHSAnalysisAgent。 */
    public static XHSAnalysisAgent buildXhsAgent(XHSNoteSource source, AgentMetrics metrics) {
        XHSNoteAnalyzer analyzer = new XHSNoteAnalyzer(
                source, emptyCache(), attractionCatalog(), fastCaller());
        return new XHSAnalysisAgent(analyzer, metrics);
    }

    /** 构造 RouteAgent（注入指定 geocoder + router）。 */
    public static RouteAgent buildRouteAgent(MapGeocoder geocoder, MapRouter router,
                                             AgentMetrics metrics) {
        RoutePlanningService service = new RoutePlanningService(
                geocoder, router, null, presetCatalog(), fastCaller());
        return new RouteAgent(service, metrics);
    }

    /** 构造 ItineraryAgent（StubMealRecommender）。 */
    public static ItineraryAgent buildItineraryAgent(AgentMetrics metrics) {
        ItineraryDesignService service = new ItineraryDesignService(new StubMealRecommender());
        return new ItineraryAgent(service, metrics);
    }

    /** 构造 BudgetAgent。 */
    public static BudgetAgent buildBudgetAgent(AgentMetrics metrics) {
        return new BudgetAgent(new BudgetCalculationService(), metrics);
    }

    /** 构造 SubAgentDispatcher，路由到四个真实 Agent。 */
    public static SubAgentDispatcher buildDispatcher(
            XHSAnalysisAgent xhs, RouteAgent route,
            ItineraryAgent itin, BudgetAgent budget) {
        return (agentUri, msg) -> switch (agentUri) {
            case XHSAnalysisAgent.AGENT_URI -> xhs.receive(msg);
            case RouteAgent.AGENT_URI -> route.receive(msg);
            case ItineraryAgent.AGENT_URI -> itin.receive(msg);
            case BudgetAgent.AGENT_URI -> budget.receive(msg);
            default -> throw new IllegalArgumentException("未知 Agent URI: " + agentUri);
        };
    }
}
