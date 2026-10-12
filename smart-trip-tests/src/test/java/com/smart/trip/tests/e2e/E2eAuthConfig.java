package com.smart.trip.tests.e2e;

import com.smart.trip.server.auth.facade.AuthFacade;
import com.smart.trip.server.auth.repository.UserRepository;
import com.smart.trip.server.auth.service.JwtTokenProvider;
import com.smart.trip.server.auth.service.TokenCacheService;
import com.smart.trip.server.auth.service.UserService;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.skills.observability.AgentMetrics;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * E2E 认证链路装配（C-012 T-1 基础设施）。
 *
 * <p>替代 {@link com.smart.trip.server.config.AuthDataSourceConfig}（由 e2e profile 关闭）：
 * - {@code TokenCacheService}：声明式注入 {@code StringRedisTemplate}，解决 Spring Boot 4
 *   {@code @ConditionalOnBean} 评估时序问题（Spring 保证先实例化参数 bean）。
 * - {@code UserRepository}：H2 内存数据库（{@code trip_user} 表），无需 MySQL，CI 可离线运行。
 * - {@code UserService} / {@code AuthFacade}：真实实现，HTTP 层注册/登录全链路可验。</p>
 */
@TestConfiguration
public class E2eAuthConfig {

    /**
     * 提供 TokenCacheService，以 StringRedisTemplate 为显式参数。
     *
     * <p>Spring 将在实例化本 bean 前先完成 StringRedisTemplate 的初始化，
     * 从而彻底规避 SecurityConfig 中 {@code @ConditionalOnBean(StringRedisTemplate.class)}
     * 的评估时序问题。</p>
     */
    @Bean
    @Primary
    public TokenCacheService e2eTokenCacheService(StringRedisTemplate redisTemplate) {
        return new TokenCacheService(redisTemplate);
    }

    /** H2 内存数据源（含 trip_user 表 DDL），E2E 替代 MySQL。 */
    @Bean
    @Primary
    public DataSource e2eAuthDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:e2e_auth;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        createSchema(ds);
        return ds;
    }

    /** 真实 UserRepository（H2 数据源支撑，JDBC 行为与生产一致）。 */
    @Bean
    @Primary
    public UserRepository e2eUserRepository(DataSource e2eAuthDataSource) {
        return new UserRepository(e2eAuthDataSource);
    }

    /** 真实 UserService（BCrypt 哈希，注册/登录行为与生产一致）。 */
    @Bean
    @Primary
    public UserService e2eUserService(UserRepository e2eUserRepository) {
        return new UserService(e2eUserRepository);
    }

    /** 真实 AuthFacade（编排 UserService + JWT + Redis，HTTP 层全链路可验）。 */
    @Bean
    @Primary
    public AuthFacade e2eAuthFacade(UserService e2eUserService,
                                    JwtTokenProvider jwtTokenProvider,
                                    TokenCacheService e2eTokenCacheService,
                                    AgentMetrics agentMetrics) {
        return new AuthFacade(e2eUserService, jwtTokenProvider, e2eTokenCacheService, agentMetrics);
    }

    /**
     * 提供 PlanCacheService，以 StringRedisTemplate 为显式参数。
     *
     * <p>与 TokenCacheService 同理：PlanConfig 中的 {@code @ConditionalOnBean(StringRedisTemplate.class)}
     * 在 Spring Boot 4 context refresh 时序下评估失败，导致 PlanCacheService bean 缺失。
     * 此处声明为 @Primary bean 强制正确实例化顺序。</p>
     */
    @Bean
    @Primary
    public PlanCacheService e2ePlanCacheService(StringRedisTemplate redisTemplate) {
        return new PlanCacheService(redisTemplate);
    }

    private static void createSchema(DataSource ds) {
        String ddl = """
                CREATE TABLE IF NOT EXISTS trip_user (
                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                    username    VARCHAR(64)  NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    nickname    VARCHAR(64)  NOT NULL,
                    role        VARCHAR(32)  NOT NULL,
                    created_at  TIMESTAMP    NOT NULL,
                    updated_at  TIMESTAMP    NOT NULL
                )
                """;
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        } catch (SQLException ex) {
            throw new IllegalStateException("E2E H2 schema 初始化失败", ex);
        }
    }
}
