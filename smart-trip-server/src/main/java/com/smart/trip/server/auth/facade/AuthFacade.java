package com.smart.trip.server.auth.facade;

import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.server.auth.model.AuthPrincipal;
import com.smart.trip.server.auth.model.User;
import com.smart.trip.server.auth.service.JwtTokenProvider;
import com.smart.trip.server.auth.service.TokenCacheService;
import com.smart.trip.server.auth.service.UserService;
import com.smart.trip.skills.observability.AgentMetrics;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 认证门面（C-011 AC-1/AC-2）。
 *
 * <p>编排 UserService、JwtTokenProvider、TokenCacheService 完成注册/登录/登出/刷新流程。
 * Controller 只依赖本 Facade，不直接操作 Service 或 Repository。</p>
 */
public class AuthFacade {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenCacheService tokenCacheService;
    private final AgentMetrics agentMetrics;

    /**
     * 构造认证门面。
     *
     * @param userService       用户服务
     * @param jwtTokenProvider  JWT 令牌提供者
     * @param tokenCacheService Redis 令牌缓存
     * @param agentMetrics      指标埋点（auth.failure.count / auth.revoke.count）
     */
    public AuthFacade(UserService userService,
                      JwtTokenProvider jwtTokenProvider,
                      TokenCacheService tokenCacheService,
                      AgentMetrics agentMetrics) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenCacheService = tokenCacheService;
        this.agentMetrics = agentMetrics;
    }

    /**
     * 注册新用户，返回 userId 和 username。
     *
     * @param username 用户名
     * @param password 密码
     * @param nickname 昵称
     * @return 包含 userId 和 username 的结果
     */
    public Map<String, Object> register(String username, String password, String nickname) {
        User user = userService.register(username, password, nickname);
        return Map.of("userId", user.id(), "username", user.username());
    }

    /**
     * 用户登录：认证 → 签发双 token → 写入 Redis。
     *
     * @param username 用户名
     * @param password 密码
     * @return 包含 accessToken、refreshToken、expiresIn 的结果
     */
    public Map<String, Object> login(String username, String password) {
        User user;
        try {
            user = userService.authenticate(username, password);
        } catch (BaseException ex) {
            agentMetrics.incrementAuthFailure();
            throw ex;
        }
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.id(), user.username(), user.role());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.id(), user.username());
        tokenCacheService.storeTokens(user.id(), accessToken, refreshToken);
        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "expiresIn", jwtTokenProvider.getExpiresInSeconds());
    }

    /**
     * 登出当前用户：删除 Redis 中双 token。
     *
     * @return 登出确认消息
     */
    public Map<String, String> logout() {
        long userId = currentUserId();
        tokenCacheService.removeTokens(userId);
        agentMetrics.incrementAuthRevoke();
        return Map.of("message", "已登出");
    }

    /**
     * 刷新 accessToken：验证 refreshToken → 签发新 accessToken → 更新 Redis。
     *
     * @param refreshToken 刷新令牌
     * @return 包含新 accessToken 和 expiresIn 的结果
     */
    public Map<String, Object> refresh(String refreshToken) {
        if (!jwtTokenProvider.isValid(refreshToken)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "无效的 refreshToken");
        }
        String type = jwtTokenProvider.getTokenType(refreshToken);
        if (!JwtTokenProvider.TYPE_REFRESH.equals(type)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "令牌类型不正确");
        }
        long userId = jwtTokenProvider.getUserId(refreshToken);
        boolean storedValid = tokenCacheService.getRefreshToken(userId)
                .map(refreshToken::equals).orElse(false);
        if (!storedValid) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "refreshToken 已失效或不匹配");
        }
        User user = userService.findByUsername(jwtTokenProvider.getUsername(refreshToken))
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED, "用户不存在"));
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.id(), user.username(), user.role());
        tokenCacheService.updateAccessToken(userId, newAccessToken);
        return Map.of(
                "accessToken", newAccessToken,
                "expiresIn", jwtTokenProvider.getExpiresInSeconds());
    }

    /**
     * 从 SecurityContext 提取当前认证用户 ID。
     *
     * @return 当前用户 ID
     * @throws BaseException UNAUTHORIZED 如果未认证
     */
    static long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        return p.userId();
    }
}
