package com.smart.trip.server.auth.service;

import com.smart.trip.server.auth.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT 令牌签发与验证（C-011 AC-2）。
 *
 * <p>accessToken 有效期 2h，refreshToken 有效期 7d。
 * 签名密钥从环境变量 {@code JWT_SECRET} 读取，禁止硬编码。</p>
 */
public class JwtTokenProvider {

    /** accessToken 有效期。 */
    static final Duration ACCESS_TOKEN_VALIDITY = Duration.ofHours(2);

    /** refreshToken 有效期。 */
    static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(7);

    /** Claims key: 角色。 */
    static final String CLAIM_ROLE = "role";

    /** Claims key: 用户名。 */
    static final String CLAIM_USERNAME = "username";

    /** Claims key: 令牌类型。 */
    static final String CLAIM_TYPE = "type";

    /** 令牌类型: access。 */
    public static final String TYPE_ACCESS = "access";

    /** 令牌类型: refresh。 */
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;

    /**
     * 构造令牌提供者，注入 HMAC 签名密钥。
     *
     * @param key HMAC-SHA256 密钥，非 null
     */
    public JwtTokenProvider(SecretKey key) {
        if (key == null) {
            throw new IllegalArgumentException("JWT signing key 不可为 null");
        }
        this.key = key;
    }

    /**
     * 签发 accessToken，有效期 2 小时。
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     角色
     * @return JWT 字符串
     */
    public String generateAccessToken(long userId, String username, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TOKEN_VALIDITY)))
                .signWith(key)
                .compact();
    }

    /**
     * 签发 refreshToken，有效期 7 天。
     *
     * @param userId   用户 ID
     * @param username 用户名（refresh 流程需要据此查询用户信息）
     * @return JWT 字符串
     */
    public String generateRefreshToken(long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(REFRESH_TOKEN_VALIDITY)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验证令牌，返回 Claims。
     *
     * @param token JWT 字符串
     * @return 解析后的 Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 判断令牌签名和有效期是否合法。
     *
     * @param token JWT 字符串
     * @return true 表示合法
     */
    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从令牌提取 userId（subject）。
     *
     * @param token JWT 字符串
     * @return userId
     */
    public long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    /**
     * 从令牌提取用户名。
     *
     * @param token JWT 字符串
     * @return username
     */
    public String getUsername(String token) {
        return parseToken(token).get(CLAIM_USERNAME, String.class);
    }

    /**
     * 从令牌提取角色。
     *
     * @param token JWT 字符串
     * @return 角色字符串
     */
    public String getRole(String token) {
        return parseToken(token).get(CLAIM_ROLE, String.class);
    }

    /**
     * 从令牌提取类型（access / refresh）。
     *
     * @param token JWT 字符串
     * @return 令牌类型
     */
    public String getTokenType(String token) {
        return parseToken(token).get(CLAIM_TYPE, String.class);
    }

    /**
     * 返回 accessToken 有效期（秒）。
     *
     * @return 有效期秒数
     */
    public long getExpiresInSeconds() {
        return ACCESS_TOKEN_VALIDITY.toSeconds();
    }
}
