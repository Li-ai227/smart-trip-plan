package com.smart.trip.server.auth.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.server.auth.model.AuthPrincipal;
import com.smart.trip.server.auth.model.User;
import com.smart.trip.server.auth.model.UserRole;
import com.smart.trip.server.auth.service.JwtTokenProvider;
import com.smart.trip.server.auth.service.TokenCacheService;
import com.smart.trip.server.auth.service.UserService;
import com.smart.trip.skills.observability.AgentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * AuthFacade 单元测试（C-011 AC-1/AC-2/AC-3/AC-4）。
 *
 * <p>直接实例化 AuthFacade，Mock 所有依赖，不启动 Spring 上下文。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthFacadeTest {

    @Mock UserService userService;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock TokenCacheService tokenCacheService;

    SimpleMeterRegistry meterRegistry;
    AgentMetrics agentMetrics;
    AuthFacade facade;

    private static final long USER_ID = 42L;
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "Pass1234";
    private static final String NICKNAME = "测试";
    private static final String ACCESS_TOKEN = "access.jwt.token";
    private static final String REFRESH_TOKEN = "refresh.jwt.token";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        agentMetrics = new AgentMetrics(meterRegistry);
        facade = new AuthFacade(userService, jwtTokenProvider, tokenCacheService, agentMetrics);
        SecurityContextHolder.clearContext();
    }

    // ─── register ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("注册成功返回 userId + username")
        void should_return_userId_and_username() {
            User user = sampleUser();
            when(userService.register(USERNAME, PASSWORD, NICKNAME)).thenReturn(user);

            Map<String, Object> result = facade.register(USERNAME, PASSWORD, NICKNAME);

            assertThat(result).containsEntry("userId", USER_ID)
                               .containsEntry("username", USERNAME);
        }

        @Test
        @DisplayName("用户名重复时抛出 CONFLICT")
        void should_propagate_conflict_exception() {
            when(userService.register(anyString(), anyString(), anyString()))
                    .thenThrow(new BaseException(ErrorCode.CONFLICT, "用户名已存在"));

            assertThatThrownBy(() -> facade.register(USERNAME, PASSWORD, NICKNAME))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONFLICT);
        }
    }

    // ─── login ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("登录成功返回 tokens + expiresIn，写 Redis")
        void should_return_tokens_and_store_in_redis() {
            User user = sampleUser();
            when(userService.authenticate(USERNAME, PASSWORD)).thenReturn(user);
            when(jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, user.role()))
                    .thenReturn(ACCESS_TOKEN);
            when(jwtTokenProvider.generateRefreshToken(USER_ID, USERNAME)).thenReturn(REFRESH_TOKEN);
            when(jwtTokenProvider.getExpiresInSeconds()).thenReturn(7200L);

            Map<String, Object> result = facade.login(USERNAME, PASSWORD);

            assertThat(result).containsEntry("accessToken", ACCESS_TOKEN)
                               .containsEntry("refreshToken", REFRESH_TOKEN)
                               .containsEntry("expiresIn", 7200L);
            verify(tokenCacheService).storeTokens(USER_ID, ACCESS_TOKEN, REFRESH_TOKEN);
        }

        @Test
        @DisplayName("认证失败时 auth.failure.count 递增，重新抛出异常")
        void should_increment_metric_and_rethrow_on_auth_failure() {
            when(userService.authenticate(anyString(), anyString()))
                    .thenThrow(new BaseException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));

            assertThatThrownBy(() -> facade.login(USERNAME, "wrong"))
                    .isInstanceOf(BaseException.class);
            assertThat(meterRegistry.counter("auth.failure.count").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("登录失败不写 Redis")
        void should_not_store_tokens_on_failure() {
            when(userService.authenticate(anyString(), anyString()))
                    .thenThrow(new BaseException(ErrorCode.UNAUTHORIZED, "错误"));

            try { facade.login(USERNAME, "bad"); } catch (BaseException ignored) { }

            verify(tokenCacheService, never()).storeTokens(anyLong(), anyString(), anyString());
        }
    }

    // ─── logout ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout")
    class LogoutTests {

        @Test
        @DisplayName("登出成功删除 Redis 令牌并递增 auth.revoke.count")
        void should_remove_tokens_and_increment_revoke_metric() {
            setSecurityContext(USER_ID, USERNAME, "USER");
            doNothing().when(tokenCacheService).removeTokens(USER_ID);

            Map<String, String> result = facade.logout();

            assertThat(result).containsEntry("message", "已登出");
            verify(tokenCacheService).removeTokens(USER_ID);
            assertThat(meterRegistry.counter("auth.revoke.count").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("未认证时抛出 UNAUTHORIZED")
        void should_throw_unauthorized_when_no_context() {
            assertThatThrownBy(() -> facade.logout())
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }
    }

    // ─── refresh ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refresh")
    class RefreshTests {

        @Test
        @DisplayName("刷新成功返回新 accessToken，更新 Redis")
        void should_return_new_access_token_and_update_redis() {
            User user = sampleUser();
            when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getTokenType(REFRESH_TOKEN))
                    .thenReturn(JwtTokenProvider.TYPE_REFRESH);
            when(jwtTokenProvider.getUserId(REFRESH_TOKEN)).thenReturn(USER_ID);
            when(tokenCacheService.getRefreshToken(USER_ID))
                    .thenReturn(Optional.of(REFRESH_TOKEN));
            when(jwtTokenProvider.getUsername(REFRESH_TOKEN)).thenReturn(USERNAME);
            when(userService.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, user.role()))
                    .thenReturn("new.access.token");
            when(jwtTokenProvider.getExpiresInSeconds()).thenReturn(7200L);

            Map<String, Object> result = facade.refresh(REFRESH_TOKEN);

            assertThat(result).containsEntry("accessToken", "new.access.token")
                               .containsEntry("expiresIn", 7200L);
            verify(tokenCacheService).updateAccessToken(USER_ID, "new.access.token");
        }

        @Test
        @DisplayName("JWT 无效时抛出 UNAUTHORIZED")
        void should_throw_when_token_invalid() {
            when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(false);

            assertThatThrownBy(() -> facade.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("令牌类型不是 refresh 时抛出 UNAUTHORIZED")
        void should_throw_when_token_type_mismatch() {
            when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getTokenType(REFRESH_TOKEN)).thenReturn("access");

            assertThatThrownBy(() -> facade.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Redis 中不存在 refreshToken 时抛出 UNAUTHORIZED")
        void should_throw_when_token_not_in_redis() {
            when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getTokenType(REFRESH_TOKEN))
                    .thenReturn(JwtTokenProvider.TYPE_REFRESH);
            when(jwtTokenProvider.getUserId(REFRESH_TOKEN)).thenReturn(USER_ID);
            when(tokenCacheService.getRefreshToken(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> facade.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Redis token 不匹配时抛出 UNAUTHORIZED")
        void should_throw_when_token_mismatch() {
            when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getTokenType(REFRESH_TOKEN))
                    .thenReturn(JwtTokenProvider.TYPE_REFRESH);
            when(jwtTokenProvider.getUserId(REFRESH_TOKEN)).thenReturn(USER_ID);
            when(tokenCacheService.getRefreshToken(USER_ID))
                    .thenReturn(Optional.of("different.token"));

            assertThatThrownBy(() -> facade.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("用户不存在时抛出 UNAUTHORIZED")
        void should_throw_when_user_not_found() {
            when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(true);
            when(jwtTokenProvider.getTokenType(REFRESH_TOKEN))
                    .thenReturn(JwtTokenProvider.TYPE_REFRESH);
            when(jwtTokenProvider.getUserId(REFRESH_TOKEN)).thenReturn(USER_ID);
            when(tokenCacheService.getRefreshToken(USER_ID))
                    .thenReturn(Optional.of(REFRESH_TOKEN));
            when(jwtTokenProvider.getUsername(REFRESH_TOKEN)).thenReturn(USERNAME);
            when(userService.findByUsername(USERNAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> facade.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BaseException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static User sampleUser() {
        return new User(USER_ID, USERNAME, "$2a$10$hash", NICKNAME,
                UserRole.USER, Instant.now(), Instant.now());
    }

    private static void setSecurityContext(long userId, String username, String role) {
        AuthPrincipal principal = new AuthPrincipal(userId, username, role);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
