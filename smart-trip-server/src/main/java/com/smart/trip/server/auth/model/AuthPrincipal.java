package com.smart.trip.server.auth.model;

/**
 * 认证主体，携带 userId/username/role，供 Facade 层提取当前用户信息。
 *
 * <p>由 {@code JwtAuthenticationFilter} 写入 SecurityContext，
 * Controller/Facade 通过 {@code SecurityContextHolder} 读取。</p>
 *
 * @param userId   用户主键
 * @param username 登录用户名
 * @param role     用户角色
 */
public record AuthPrincipal(long userId, String username, String role) {
}
