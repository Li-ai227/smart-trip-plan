package com.smart.trip.server.config;

import com.smart.trip.server.auth.facade.AuthFacade;
import com.smart.trip.server.auth.repository.UserRepository;
import com.smart.trip.server.auth.service.JwtTokenProvider;
import com.smart.trip.server.auth.service.TokenCacheService;
import com.smart.trip.server.auth.service.UserService;
import com.smart.trip.skills.observability.AgentMetrics;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * C-011 用户认证数据库装配（复用 trace DataSource 配置模式）。
 *
 * <p>测试 profile 通过 {@code trip.auth.datasource.enabled=false} 关闭。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "trip.auth.datasource", name = "enabled", matchIfMissing = true)
public class AuthDataSourceConfig {

    /** 创建 HikariCP 认证数据源。 */
    @Bean(destroyMethod = "close")
    public DataSource authDataSource(
            @Value("${trip.auth.datasource.host:127.0.0.1}") String host,
            @Value("${trip.auth.datasource.port:3306}") int port,
            @Value("${trip.auth.datasource.database:smart_trip_plan}") String database,
            @Value("${trip.auth.datasource.username:root}") String username,
            @Value("${trip.auth.datasource.password:}") String password,
            @Value("${trip.auth.datasource.pool-size:4}") int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.max(1, poolSize / 2));
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        config.setPoolName("auth-pool");
        return new HikariDataSource(config);
    }

    /**
     * 创建 JDBC 用户数据访问层。
     *
     * <p>显式 {@code @Qualifier}：{@code trip.trace.datasource.enabled=true} 时容器中
     * 同时存在 {@code authDataSource} 与 {@code traceDataSource} 两个 {@link DataSource}
     * Bean，按类型注入会歧义报错（依赖编译期 {@code -parameters} 的参数名回退不可靠）。</p>
     */
    @Bean
    public UserRepository userRepository(@Qualifier("authDataSource") DataSource authDataSource) {
        return new UserRepository(authDataSource);
    }

    /** 创建用户服务。 */
    @Bean
    public UserService userService(UserRepository userRepository) {
        return new UserService(userRepository);
    }

    /** 创建认证门面（编排 UserService + JWT + Redis）。 */
    @Bean
    public AuthFacade authFacade(UserService userService,
                                 JwtTokenProvider jwtTokenProvider,
                                 TokenCacheService tokenCacheService,
                                 AgentMetrics agentMetrics) {
        return new AuthFacade(userService, jwtTokenProvider, tokenCacheService, agentMetrics);
    }
}
