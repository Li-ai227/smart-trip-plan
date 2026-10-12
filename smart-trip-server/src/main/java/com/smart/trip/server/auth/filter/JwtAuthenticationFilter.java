package com.smart.trip.server.auth.filter;

import com.smart.trip.server.auth.model.AuthPrincipal;
import com.smart.trip.server.auth.service.JwtTokenProvider;
import com.smart.trip.server.auth.service.TokenCacheService;
import com.smart.trip.skills.observability.AgentMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器（C-011 AC-2/AC-3）。
 *
 * <p>每次请求校验 JWT 签名 + Redis 中 token 存在性（双重验证），
 * Redis 中不存在即视为已吊销。Redis 不可用时降级为仅验证 JWT 签名（不阻断服务）。</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final System.Logger LOG =
            System.getLogger(JwtAuthenticationFilter.class.getName());
    private static final String BEARER_PREFIX = "Bearer ";

    /** 无需认证的公开路径。 */
    static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/trip-plan/tags",
            "/actuator/health",
            "/actuator/metrics",
            "/actuator/info",
            "/actuator/prometheus");

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenCacheService tokenCacheService;
    private final AgentMetrics agentMetrics;

    /**
     * 构造认证过滤器，注入令牌提供者和缓存服务（无指标埋点）。
     *
     * @param jwtTokenProvider  JWT 令牌提供者
     * @param tokenCacheService Redis 令牌缓存
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   TokenCacheService tokenCacheService) {
        this(jwtTokenProvider, tokenCacheService, null);
    }

    /**
     * 构造认证过滤器，注入令牌提供者、缓存服务和指标埋点。
     *
     * @param jwtTokenProvider  JWT 令牌提供者
     * @param tokenCacheService Redis 令牌缓存
     * @param agentMetrics      指标埋点（auth.redis.fallback），可为 null
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   TokenCacheService tokenCacheService,
                                   AgentMetrics agentMetrics) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenCacheService = tokenCacheService;
        this.agentMetrics = agentMetrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            sendUnauthorized(response, "缺少有效的 Authorization header");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        if (!jwtTokenProvider.isValid(token)) {
            sendUnauthorized(response, "无效或过期的令牌");
            return;
        }

        long userId = jwtTokenProvider.getUserId(token);

        if (!isTokenInRedis(userId, token)) {
            sendUnauthorized(response, "令牌已吊销");
            return;
        }

        String role = jwtTokenProvider.getRole(token);
        String username = jwtTokenProvider.getUsername(token);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var auth = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(userId, username, role), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private boolean isTokenInRedis(long userId, String token) {
        if (tokenCacheService == null) {
            return true;
        }
        try {
            return tokenCacheService.isAccessTokenValid(userId, token);
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "Redis 不可用，降级为仅 JWT 签名校验: {0}", ex.getMessage());
            if (agentMetrics != null) {
                agentMetrics.incrementAuthRedisFallback();
            }
            return true;
        }
    }

    private static boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private static void sendUnauthorized(HttpServletResponse response,
                                         String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
    }
}
