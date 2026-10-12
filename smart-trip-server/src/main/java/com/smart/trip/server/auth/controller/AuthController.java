package com.smart.trip.server.auth.controller;

import com.smart.trip.server.auth.facade.AuthFacade;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证端点（C-011 AC-1/AC-2）。
 *
 * <p>薄 Controller：仅做 HTTP 语义映射，全部业务编排委托给 {@link AuthFacade}。
 * 不直接依赖 Service、Repository 或 Cache。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthFacade authFacade;

    /**
     * 构造认证控制器，仅注入 Facade。
     *
     * @param authFacade 认证门面
     */
    public AuthController(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    /**
     * 注册新用户。
     *
     * @param req 注册请求体
     * @return 201 + userId + username
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authFacade.register(req.username(), req.password(), req.nickname()));
    }

    /**
     * 用户登录。
     *
     * @param req 登录请求体
     * @return 200 + accessToken + refreshToken + expiresIn
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(authFacade.login(req.username(), req.password()));
    }

    /**
     * 用户登出（需携带有效 token）。
     *
     * @return 200 + 登出确认
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(authFacade.logout());
    }

    /**
     * 刷新 accessToken（需携带有效 token）。
     *
     * @param req 刷新请求体
     * @return 200 + 新 accessToken + expiresIn
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authFacade.refresh(req.refreshToken()));
    }

    /**
     * 注册请求体。
     *
     * @param username 用户名
     * @param password 密码
     * @param nickname 昵称
     */
    public record RegisterRequest(String username, String password, String nickname) {}

    /**
     * 登录请求体。
     *
     * @param username 用户名
     * @param password 密码
     */
    public record LoginRequest(String username, String password) {}

    /**
     * 刷新请求体。
     *
     * @param refreshToken 刷新令牌
     */
    public record RefreshRequest(String refreshToken) {}
}
