package com.smart.trip.server.config;

import com.smart.trip.server.auth.filter.JwtAuthenticationFilter;
import com.smart.trip.server.auth.filter.TraceIdFilter;
import com.smart.trip.server.auth.service.JwtTokenProvider;
import com.smart.trip.server.auth.service.TokenCacheService;
import com.smart.trip.skills.observability.AgentMetrics;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 配置（C-011 AC-3）。
 *
 * <p>{@code /api/v1/auth/register}、{@code /api/v1/auth/login}、
 * {@code /actuator/health} 公开；{@code /api/v1/admin/**} 仅 ADMIN；
 * 其余需认证。JWT 签名密钥从环境变量 {@code JWT_SECRET} 读取。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    /** 构造 HMAC-SHA256 签名密钥。 */
    @Bean
    public SecretKey jwtSecretKey(
            @Value("${JWT_SECRET:default-dev-secret-key-for-local-development-only}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /** 构造 JWT 令牌提供者。 */
    @Bean
    public JwtTokenProvider jwtTokenProvider(SecretKey jwtSecretKey) {
        return new JwtTokenProvider(jwtSecretKey);
    }

    /** 构造 JWT 认证过滤器（含 Redis 降级指标；TokenCacheService 不可用时降级为纯 JWT 签名校验）。 */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            ObjectProvider<TokenCacheService> tokenCacheServiceProvider,
            AgentMetrics agentMetrics) {
        return new JwtAuthenticationFilter(
                jwtTokenProvider, tokenCacheServiceProvider.getIfAvailable(), agentMetrics);
    }

    /** 构造 TraceId 过滤器。 */
    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    /** CORS 配置（允许本地 Vite dev server 及同域生产）。 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${trip.cors.allowed-origins:http://localhost:5173,http://localhost:4173}") String originsRaw) {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : originsRaw.split(",")) {
            config.addAllowedOrigin(origin.trim());
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "X-Trace-Id"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** 构造安全过滤链（公开路径/角色/JWT 过滤器/CORS）。 */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtAuthFilter,
                                           TraceIdFilter traceIdFilter,
                                           @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/trip-plan/tags").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
