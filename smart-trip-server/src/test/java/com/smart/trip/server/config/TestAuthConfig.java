package com.smart.trip.server.config;

import com.smart.trip.server.admin.AdminController;
import com.smart.trip.server.admin.AgentStatusService;
import com.smart.trip.server.auth.facade.AuthFacade;
import com.smart.trip.server.auth.repository.UserRepository;
import com.smart.trip.server.auth.service.TokenCacheService;
import com.smart.trip.server.auth.service.UserService;
import com.smart.trip.server.plan.facade.TripPlanFacade;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.skills.observability.AgentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 测试 profile 替身配置：当 AuthDataSourceConfig 被禁用时提供 Mock Bean，
 * 避免 {@code @SpringBootTest} 上下文因缺少认证链 Bean 而启动失败。
 */
@TestConfiguration
public class TestAuthConfig {

    /** Stub AgentMetrics bean（使用 SimpleMeterRegistry，无需外部 Prometheus）。 */
    @Bean
    @ConditionalOnMissingBean
    public AgentMetrics stubAgentMetrics() {
        return new AgentMetrics(new SimpleMeterRegistry());
    }

    /** Mock UserRepository bean. */
    @Bean
    @ConditionalOnMissingBean
    public UserRepository stubUserRepository() {
        return Mockito.mock(UserRepository.class);
    }

    /** Mock UserService bean. */
    @Bean
    @ConditionalOnMissingBean
    public UserService stubUserService() {
        return Mockito.mock(UserService.class);
    }

    /** Mock TokenCacheService bean. */
    @Bean
    @ConditionalOnMissingBean
    public TokenCacheService stubTokenCacheService() {
        return Mockito.mock(TokenCacheService.class);
    }

    /** Mock AuthFacade bean. */
    @Bean
    @ConditionalOnMissingBean
    public AuthFacade stubAuthFacade() {
        return Mockito.mock(AuthFacade.class);
    }

    /** Mock PlanCacheService bean. */
    @Bean
    @ConditionalOnMissingBean
    public PlanCacheService stubPlanCacheService() {
        return Mockito.mock(PlanCacheService.class);
    }

    /** Mock TripPlanFacade bean. */
    @Bean
    @ConditionalOnMissingBean
    public TripPlanFacade stubTripPlanFacade() {
        return Mockito.mock(TripPlanFacade.class);
    }

    /** Mock AgentStatusService bean. */
    @Bean
    @ConditionalOnMissingBean
    public AgentStatusService stubAgentStatusService() {
        return Mockito.mock(AgentStatusService.class);
    }

    /** Stub AdminController bean. */
    @Bean
    @ConditionalOnMissingBean
    public AdminController stubAdminController(AgentStatusService agentStatusService,
                                               TripPlanFacade tripPlanFacade) {
        return new AdminController(agentStatusService, tripPlanFacade);
    }
}
