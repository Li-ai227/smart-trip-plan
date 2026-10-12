package com.smart.trip.server.config;

import com.smart.trip.agent.supervisor.persistence.MySqlTripPlanPersistenceService;
import com.smart.trip.agent.supervisor.trace.MySqlTracePersistenceService;
import com.smart.trip.server.plan.repository.PlanDraftRepository;
import com.smart.trip.skills.trace.TracePersistenceService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * C-010 审计追踪数据库装配。
 *
 * <p>从 {@code trip.trace.datasource.*} 配置项构建 HikariCP 连接池，
 * 并装配 {@link MySqlTracePersistenceService} 暴露为 {@link TracePersistenceService} Bean。
 * 测试 profile 通过 {@code trip.trace.datasource.enabled=false} 关闭以替身化（AC-6）。</p>
 *
 * <p>密钥来自环境变量 {@code TRACE_DB_PASSWORD}，禁止硬编码（编码规范 §7）。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "trip.trace.datasource", name = "enabled", matchIfMissing = true)
public class TraceDataSourceConfig {

    /**
     * 构造审计追踪专用 HikariCP 数据源。
     *
     * @param host     数据库主机
     * @param port     端口
     * @param database 数据库名
     * @param username 用户名
     * @param password 密码（来自环境变量）
     * @param poolSize 连接池大小
     * @return HikariCP DataSource
     */
    @Bean(destroyMethod = "close")
    public DataSource traceDataSource(
            @Value("${trip.trace.datasource.host:127.0.0.1}") String host,
            @Value("${trip.trace.datasource.port:3306}") int port,
            @Value("${trip.trace.datasource.database:smart_trip_plan_trace}") String database,
            @Value("${trip.trace.datasource.username:root}") String username,
            @Value("${trip.trace.datasource.password:}") String password,
            @Value("${trip.trace.datasource.pool-size:4}") int poolSize) {
        HikariConfig config = new HikariConfig();
        // characterEncoding 是 Java 侧字符集名（driver 内部走 String.getBytes(name)），
        // 必须是 "UTF-8" 而非 MySQL 侧字符集名 "utf8mb4"；服务端 4 字节 utf8mb4 由建表 DDL
        // 的 CHARACTER SET utf8mb4 决定，与此参数无关，UTF-8 客户端编码足以承载 utf8mb4 数据。
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.max(1, poolSize / 2));
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        config.setPoolName("trace-pool");
        return new HikariDataSource(config);
    }

    /**
     * 装配审计追踪持久化服务。
     *
     * <p>显式 {@code @Qualifier}（而非依赖编译期 {@code -parameters} 参数名回退匹配）：
     * 一旦 {@code AuthDataSourceConfig#authDataSource} 与本类的 {@code traceDataSource}
     * 同时存在，容器中就有两个 {@link DataSource} Bean，按类型注入会歧义报错。</p>
     *
     * @param traceDataSource 追踪专用数据源
     * @return MySqlTracePersistenceService 实例（同时实现 AutoCloseable）
     */
    @Bean(destroyMethod = "close")
    public MySqlTracePersistenceService tracePersistenceService(
            @Qualifier("traceDataSource") DataSource traceDataSource) {
        return new MySqlTracePersistenceService(traceDataSource);
    }

    /**
     * 装配历史规划持久化服务（C-017 DECISION-4：复用 traceDataSource 及其线程池规格，
     * 与审计追踪共用同一 MySQL 库 {@code smart_trip_plan_trace}）。
     *
     * @param traceDataSource 追踪专用数据源
     * @return MySqlTripPlanPersistenceService 实例（同时实现 AutoCloseable）
     */
    @Bean(destroyMethod = "close")
    public MySqlTripPlanPersistenceService tripPlanPersistenceService(
            @Qualifier("traceDataSource") DataSource traceDataSource) {
        return new MySqlTripPlanPersistenceService(traceDataSource);
    }

    /**
     * 装配计划模式草案仓库（C-018 T-13：复用 traceDataSource）。
     *
     * @param traceDataSource 追踪专用数据源
     * @return PlanDraftRepository 实例
     */
    @Bean
    public PlanDraftRepository planDraftRepository(
            @Qualifier("traceDataSource") DataSource traceDataSource) {
        return new PlanDraftRepository(traceDataSource);
    }
}
