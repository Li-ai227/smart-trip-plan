package com.smart.trip.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.server.auth.service.TokenCacheService;
import com.smart.trip.server.plan.repository.PlanDraftRepository;
import com.smart.trip.server.plan.service.PlanCacheService;
import com.smart.trip.server.plan.service.PlanIdSequenceService;
import com.smart.trip.skills.xhs.RedisXHSNoteCache;
import com.smart.trip.skills.xhs.XHSNoteCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 依赖服务自动装配（C-013 联调修复）。
 *
 * <p>在 {@link DataRedisAutoConfiguration} 之后运行，保证 {@link StringRedisTemplate} 已注册。
 * 测试 profile 通过 {@code TestAuthConfig} 提供 Mock 替身（{@code @ConditionalOnMissingBean}
 * 优先，AutoConfiguration 不覆盖）。</p>
 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisServicesAutoConfiguration {

    /** JWT 令牌 Redis 缓存服务（仅在 Redis 可用时注册）。 */
    @Bean
    @ConditionalOnMissingBean
    public TokenCacheService tokenCacheService(StringRedisTemplate redisTemplate) {
        return new TokenCacheService(redisTemplate);
    }

    /** 规划进度 Redis 缓存服务（仅在 Redis 可用时注册）。T-14: 注入 PlanDraftRepository 实现 MySQL 双层写入。 */
    @Bean
    @ConditionalOnMissingBean
    public PlanCacheService planCacheService(StringRedisTemplate redisTemplate,
                                              java.util.Optional<PlanDraftRepository> planDraftRepo) {
        return new PlanCacheService(redisTemplate, planDraftRepo.orElse(null));
    }

    /** planId 序列号服务：Redis 优先自增，跨日自动重置（C-017 T-9，仅在 Redis 可用时注册）。 */
    @Bean
    @ConditionalOnMissingBean
    public PlanIdSequenceService planIdSequenceService(StringRedisTemplate redisTemplate) {
        return new PlanIdSequenceService(redisTemplate);
    }

    /** XHS 笔记缓存 Redis 适配（C-015 AC-8，替换 emptyXhsCache 空桩）。 */
    @Bean
    @ConditionalOnMissingBean(XHSNoteCache.class)
    public XHSNoteCache xhsNoteCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        var valueOps = redisTemplate.opsForValue();
        RedisXHSNoteCache.RedisValueOps ops = new RedisXHSNoteCache.RedisValueOps() {
            @Override
            public void set(String key, String value, java.time.Duration timeout) {
                valueOps.set(key, value, timeout);
            }

            @Override
            public String get(String key) {
                return valueOps.get(key);
            }
        };
        return new RedisXHSNoteCache(ops, objectMapper);
    }
}
