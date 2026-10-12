package com.smart.trip.server.auth.service;

import com.smart.trip.common.constant.CacheConstants;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * JWT 令牌 Redis 缓存服务（C-011 AC-2）。
 *
 * <p>Key 规范：{@code auth:access:{userId}} → accessToken（TTL 2h），
 * {@code auth:refresh:{userId}} → refreshToken（TTL 7d）。
 * 登录覆写、登出删除、refresh 更新 access key。</p>
 */
public class TokenCacheService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造缓存服务，注入 Redis 模板。
     *
     * @param redisTemplate Spring Data Redis 字符串模板
     */
    public TokenCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 存储登录令牌对（access + refresh），各自设置 TTL。
     *
     * @param userId       用户 ID
     * @param accessToken  访问令牌
     * @param refreshToken 刷新令牌
     */
    public void storeTokens(long userId, String accessToken, String refreshToken) {
        String ak = accessKey(userId);
        String rk = refreshKey(userId);
        redisTemplate.opsForValue().set(ak, accessToken, CacheConstants.ACCESS_TOKEN_TTL);
        redisTemplate.opsForValue().set(rk, refreshToken, CacheConstants.REFRESH_TOKEN_TTL);
    }

    /**
     * 刷新后更新 accessToken 缓存。
     *
     * @param userId      用户 ID
     * @param accessToken 新访问令牌
     */
    public void updateAccessToken(long userId, String accessToken) {
        redisTemplate.opsForValue().set(accessKey(userId), accessToken, CacheConstants.ACCESS_TOKEN_TTL);
    }

    /**
     * 获取缓存中的 accessToken，不存在返回空。
     *
     * @param userId 用户 ID
     * @return accessToken，或 empty
     */
    public Optional<String> getAccessToken(long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(accessKey(userId)));
    }

    /**
     * 获取缓存中的 refreshToken，不存在返回空。
     *
     * @param userId 用户 ID
     * @return refreshToken，或 empty
     */
    public Optional<String> getRefreshToken(long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(refreshKey(userId)));
    }

    /**
     * 删除指定用户的全部令牌缓存（登出）。
     *
     * @param userId 用户 ID
     */
    public void removeTokens(long userId) {
        redisTemplate.delete(accessKey(userId));
        redisTemplate.delete(refreshKey(userId));
    }

    /**
     * 校验传入的 token 是否与缓存中的 accessToken 一致。
     *
     * @param userId 用户 ID
     * @param token  待校验令牌
     * @return true 表示一致
     */
    public boolean isAccessTokenValid(long userId, String token) {
        return getAccessToken(userId).map(token::equals).orElse(false);
    }

    /**
     * 构造 accessToken 缓存 key。
     *
     * @param userId 用户 ID
     * @return Redis key
     */
    static String accessKey(long userId) {
        return CacheConstants.DOMAIN_AUTH_ACCESS + CacheConstants.SEPARATOR + userId;
    }

    /**
     * 构造 refreshToken 缓存 key。
     *
     * @param userId 用户 ID
     * @return Redis key
     */
    static String refreshKey(long userId) {
        return CacheConstants.DOMAIN_AUTH_REFRESH + CacheConstants.SEPARATOR + userId;
    }
}
