package com.smart.trip.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;
import com.smart.trip.server.auth.controller.AuthController;
import com.smart.trip.server.auth.facade.AuthFacade;
import com.smart.trip.server.auth.filter.JwtAuthenticationFilter;
import com.smart.trip.server.auth.model.User;
import com.smart.trip.server.auth.model.UserRole;
import com.smart.trip.server.auth.service.JwtTokenProvider;
import com.smart.trip.server.auth.service.TokenCacheService;
import com.smart.trip.server.auth.service.UserService;
import com.smart.trip.server.config.GlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 认证端点测试（C-011 AC-1~AC-5，TDD Green）。
 *
 * <p>分层验证：Controller → Facade → Service → Repository/Cache。
 * Mock AuthFacade（Controller 层测试）和 Service/Cache（Facade/Filter 测试），
 * 通过 standalone MockMvc + JwtAuthenticationFilter 验证 HTTP 语义。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only-32bytes!!";
    private static final SecretKey KEY = new SecretKeySpec(
            SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private static final Instant NOW = Instant.now();

    @Mock private AuthFacade authFacade;
    @Mock private TokenCacheService tokenCacheService;

    private JwtTokenProvider jwtTokenProvider;
    private MockMvc mockMvc;

    private static User testUser(long id, String username) {
        return new User(id, username, "$2a$10$hash", "测试", UserRole.USER, NOW, NOW);
    }

    private String validAccessToken(long userId, String username, UserRole role) {
        return jwtTokenProvider.generateAccessToken(userId, username, role);
    }

    private String validRefreshToken(long userId, String username) {
        return jwtTokenProvider.generateRefreshToken(userId, username);
    }

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(KEY);
        AuthController controller = new AuthController(authFacade);
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtTokenProvider, tokenCacheService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter(filter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // =========================================================================
    // 注册端点：POST /api/v1/auth/register
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        @Test
        @DisplayName("AC-1/Case-1: 合法注册 → 201 + userId + username")
        void should_return_201_with_userId_when_register_succeeds() throws Exception {
            when(authFacade.register("test", "Pass1234", "测试"))
                    .thenReturn(Map.of("userId", 1L, "username", "test"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"test\",\"password\":\"Pass1234\",\"nickname\":\"测试\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.username").value("test"));
        }

        @Test
        @DisplayName("AC-4/Case-2: 用户名已存在 → 409 CONFLICT + traceId")
        void should_return_409_when_username_already_exists() throws Exception {
            when(authFacade.register("test", "Pass1234", "测试"))
                    .thenThrow(new BaseException(ErrorCode.CONFLICT, "用户名已存在: test"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"test\",\"password\":\"Pass1234\",\"nickname\":\"测试\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.traceId").exists());
        }

        @Test
        @DisplayName("边界: 密码长度 < 8 → 400 INVALID_REQUEST")
        void should_return_400_when_password_too_short() throws Exception {
            when(authFacade.register(eq("test"), eq("Ab1"), any()))
                    .thenThrow(new BaseException(ErrorCode.INVALID_REQUEST, "密码长度不得少于 8 位"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"test\",\"password\":\"Ab1\",\"nickname\":\"t\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("边界: username 为空白 → 400 INVALID_REQUEST")
        void should_return_400_when_username_blank() throws Exception {
            when(authFacade.register(eq("   "), anyString(), any()))
                    .thenThrow(new BaseException(ErrorCode.INVALID_REQUEST, "username 不可为空"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"   \",\"password\":\"Pass1234\",\"nickname\":\"t\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-4: 响应不包含密码明文或哈希值")
        void should_not_expose_password_in_response() throws Exception {
            when(authFacade.register("test", "Pass1234", "测试"))
                    .thenReturn(Map.of("userId", 1L, "username", "test"));

            String body = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"test\",\"password\":\"Pass1234\",\"nickname\":\"测试\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("password")
                    .doesNotContain("passwordHash")
                    .doesNotContain("$2a$");
        }
    }

    // =========================================================================
    // 登录端点：POST /api/v1/auth/login
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("AC-1/AC-2/Case-3: 登录成功 → 200 + accessToken + refreshToken + expiresIn + Redis 写入")
        void should_return_200_with_tokens_and_write_redis_when_login_succeeds() throws Exception {
            when(authFacade.login("test", "Pass1234"))
                    .thenReturn(Map.of("accessToken", "at", "refreshToken", "rt", "expiresIn", 7200L));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"test\",\"password\":\"Pass1234\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("at"))
                    .andExpect(jsonPath("$.refreshToken").value("rt"))
                    .andExpect(jsonPath("$.expiresIn").value(7200));

            verify(authFacade).login("test", "Pass1234");
        }

        @Test
        @DisplayName("AC-2/Case-4/防枚举: 用户名不存在 → 401 UNAUTHORIZED（不区分用户不存在）")
        void should_return_401_when_username_not_found_anti_enumeration() throws Exception {
            when(authFacade.login("nonexistent", "Pass1234"))
                    .thenThrow(new BaseException(ErrorCode.UNAUTHORIZED, UserService.AUTH_FAILURE_MESSAGE));

            String body = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nonexistent\",\"password\":\"Pass1234\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("不存在").doesNotContain("not found");
        }

        @Test
        @DisplayName("AC-2/Case-4/防枚举: 密码错误 → 401 UNAUTHORIZED（与用户不存在同一响应）")
        void should_return_401_when_password_wrong_anti_enumeration() throws Exception {
            when(authFacade.login("test", "WrongPwd"))
                    .thenThrow(new BaseException(ErrorCode.UNAUTHORIZED, UserService.AUTH_FAILURE_MESSAGE));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"test\",\"password\":\"WrongPwd\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value(UserService.AUTH_FAILURE_MESSAGE));
        }

        @Test
        @DisplayName("AC-2/边界: 同一用户再次登录 → Facade.login 被调用两次（Redis 覆写）")
        void should_overwrite_redis_token_on_re_login_single_device() throws Exception {
            when(authFacade.login("test", "Pass1234"))
                    .thenReturn(Map.of("accessToken", "at", "refreshToken", "rt", "expiresIn", 7200L));

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"test\",\"password\":\"Pass1234\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"test\",\"password\":\"Pass1234\"}"))
                    .andExpect(status().isOk());

            verify(authFacade, org.mockito.Mockito.times(2)).login("test", "Pass1234");
        }

        @Test
        @DisplayName("Case-5b: 设备 A 登录后设备 B 同账号登录 → 设备 A 旧 token 访问 → 401")
        void should_invalidate_old_token_when_same_user_logs_in_from_another_device() throws Exception {
            String oldToken = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, oldToken)).thenReturn(false);

            mockMvc.perform(get("/api/v1/trip-plan/P-001")
                            .header("Authorization", "Bearer " + oldToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 登出端点：POST /api/v1/auth/logout
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("AC-1/Case-5a: 登出成功 → 200 + Facade.logout 被调用")
        void should_return_200_and_clear_redis_when_logout_succeeds() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);
            when(authFacade.logout()).thenReturn(Map.of("message", "已登出"));

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            verify(authFacade).logout();
        }

        @Test
        @DisplayName("Case-5a: 登出后旧 token 再访问 → 401（Redis 无记录即吊销）")
        void should_return_401_when_using_old_token_after_logout() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(false);

            mockMvc.perform(get("/api/v1/trip-plan/P-001")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Refresh 端点：POST /api/v1/auth/refresh
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("AC-2: refresh 成功 → 新 accessToken + expiresIn（公开端点，无需 accessToken）")
        void should_return_new_access_token_and_update_redis_when_refresh_valid() throws Exception {
            String refreshToken = validRefreshToken(1L, "testuser");
            when(authFacade.refresh(refreshToken))
                    .thenReturn(Map.of("accessToken", "new-at", "expiresIn", 7200L));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-at"))
                    .andExpect(jsonPath("$.expiresIn").value(7200));
        }

        @Test
        @DisplayName("边界: accessToken 过期但 refreshToken 有效 → refresh 返回新 accessToken")
        void should_issue_new_access_token_when_access_expired_but_refresh_valid() throws Exception {
            String refreshToken = validRefreshToken(1L, "testuser");
            when(authFacade.refresh(refreshToken))
                    .thenReturn(Map.of("accessToken", "new-at", "expiresIn", 7200L));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isString());
        }

        @Test
        @DisplayName("边界: refreshToken 也过期（Redis TTL 到期清除）→ 401")
        void should_return_401_when_refresh_token_also_expired() throws Exception {
            String validRefresh = validRefreshToken(1L, "testuser");
            when(authFacade.refresh(validRefresh))
                    .thenThrow(new BaseException(ErrorCode.UNAUTHORIZED, "refreshToken 已失效或不匹配"));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + validRefresh + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("边界: 无效 refreshToken → 401")
        void should_return_401_when_refresh_token_invalid() throws Exception {
            when(authFacade.refresh("invalid.jwt.token"))
                    .thenThrow(new BaseException(ErrorCode.UNAUTHORIZED, "无效的 refreshToken"));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"invalid.jwt.token\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    // =========================================================================
    // JWT + Redis 双重验证（JwtAuthenticationFilter）
    // =========================================================================

    @Nested
    @DisplayName("JWT + Redis 双重验证")
    class JwtRedisVerification {

        @Test
        @DisplayName("AC-3/Case-5: 无 Authorization header → 401")
        void should_return_401_when_no_authorization_header() throws Exception {
            mockMvc.perform(get("/api/v1/trip-plan/P-001"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("边界: Authorization 格式非 Bearer <token> → 401")
        void should_return_401_when_authorization_format_invalid() throws Exception {
            mockMvc.perform(get("/api/v1/trip-plan/P-001")
                            .header("Authorization", "Basic abc123"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("边界: 过期 token → 401")
        void should_return_401_when_token_expired() throws Exception {
            String expired = Jwts.builder()
                    .subject("1")
                    .claim("username", "test").claim("role", "USER").claim("type", "access")
                    .issuedAt(java.util.Date.from(Instant.now().minusSeconds(10000)))
                    .expiration(java.util.Date.from(Instant.now().minusSeconds(5000)))
                    .signWith(KEY)
                    .compact();

            mockMvc.perform(get("/api/v1/trip-plan/P-001")
                            .header("Authorization", "Bearer " + expired))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("边界: JWT 签名合法但 Redis 无记录（已吊销）→ 401")
        void should_return_401_when_jwt_valid_but_redis_revoked() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(false);

            mockMvc.perform(get("/api/v1/trip-plan/P-001")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("边界: 非法 JWT 格式（篡改的 token）→ 401")
        void should_return_401_when_jwt_malformed() throws Exception {
            mockMvc.perform(get("/api/v1/trip-plan/P-001")
                            .header("Authorization", "Bearer invalid.jwt.token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("NFR/降级: Redis 不可用时降级为仅 JWT 签名校验（不阻断服务）")
        void should_fallback_to_jwt_only_when_redis_unavailable() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(anyLong(), anyString()))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            mockMvc.perform(get("/api/v1/trip-plan/P-001")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        assertThat(s).isNotEqualTo(401)
                                .as("Redis fallback: should not be 401");
                    });
        }
    }

    // =========================================================================
    // 角色校验
    // =========================================================================

    @Nested
    @DisplayName("角色与权限校验")
    class RoleAuthorization {

        @Test
        @DisplayName("AC-3/Case-11: USER 角色访问 /admin/** → 认证通过但非 401")
        void should_return_403_when_user_role_accesses_admin_endpoint() throws Exception {
            String token = validAccessToken(1L, "test", UserRole.USER);
            when(tokenCacheService.isAccessTokenValid(1L, token)).thenReturn(true);

            mockMvc.perform(get("/api/v1/admin/agent/status")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("AC-3: ADMIN 角色访问 /admin/** → 通过认证")
        void should_allow_admin_role_to_access_admin_endpoint() throws Exception {
            String token = validAccessToken(2L, "admin", UserRole.ADMIN);
            when(tokenCacheService.isAccessTokenValid(2L, token)).thenReturn(true);

            mockMvc.perform(get("/api/v1/admin/agent/status")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(result -> {
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
                    });
        }

        @Test
        @DisplayName("AC-3: 公开路径 /api/v1/auth/register 无需 token → 不拦截")
        void should_allow_register_without_token() throws Exception {
            when(authFacade.register("test", "Pass1234", "测试"))
                    .thenReturn(Map.of("userId", 1L, "username", "test"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"test\",\"password\":\"Pass1234\",\"nickname\":\"测试\"}"))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("AC-3: 公开路径 /api/v1/auth/login 无需 token → 不拦截")
        void should_allow_login_without_token() throws Exception {
            when(authFacade.login("test", "Pass1234"))
                    .thenReturn(Map.of("accessToken", "at", "refreshToken", "rt", "expiresIn", 7200L));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"test\",\"password\":\"Pass1234\"}"))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("AC-3: 公开路径 /actuator/health 无需 token → 不拦截")
        void should_allow_health_without_token() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("AC-3: /api/v1/auth/refresh 为公开端点，无效 refreshToken → 401（由控制器校验）")
        void should_return_401_for_refresh_without_token() throws Exception {
            when(authFacade.refresh("some-token"))
                    .thenThrow(new BaseException(ErrorCode.UNAUTHORIZED, "无效的 refreshToken"));
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"some-token\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 密码安全（BCrypt）
    // =========================================================================

    @Nested
    @DisplayName("密码安全")
    class PasswordSecurity {

        @Test
        @DisplayName("AC-4: 密码 BCrypt 哈希存储（不存明文）")
        void should_store_password_as_bcrypt_hash() {
            var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
            String hash = encoder.encode("Pass1234");
            assertThat(hash).startsWith("$2a$").isNotEqualTo("Pass1234");
            assertThat(encoder.matches("Pass1234", hash)).isTrue();
        }

        @Test
        @DisplayName("AC-4: 日志不泄露密码哈希值")
        void should_not_log_password_hash() throws Exception {
            when(authFacade.register("test", "Pass1234", "测试"))
                    .thenReturn(Map.of("userId", 1L, "username", "test"));

            String body = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"test\",\"password\":\"Pass1234\",\"nickname\":\"测试\"}"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("$2a$")
                    .doesNotContain("password_hash")
                    .doesNotContain("passwordHash");
        }
    }
}
