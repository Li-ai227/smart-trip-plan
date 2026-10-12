package com.smart.trip.server.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

/**
 * 用户实体（C-011 AC-5：trip_user 表映射）。
 *
 * <p>server 内部实体，不下沉 common。密码哈希仅用于内部校验，
 * 禁止出现在 REST 响应或日志中（AC-4）。</p>
 *
 * @param id           用户主键
 * @param username     登录用户名
 * @param passwordHash BCrypt 密码哈希
 * @param nickname     昵称
 * @param role         用户角色
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 */
public record User(
        long id,
        String username,
        @JsonIgnore String passwordHash,
        String nickname,
        UserRole role,
        Instant createdAt,
        Instant updatedAt) {
}
