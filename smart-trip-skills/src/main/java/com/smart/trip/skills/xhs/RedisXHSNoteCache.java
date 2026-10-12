package com.smart.trip.skills.xhs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.constant.CacheConstants;
import com.smart.trip.common.model.XHSNote;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 小红书笔记缓存 Redis 适配（C-015 AC-8 / 数据模型.md §4）。
 *
 * <p>以目的地为索引存储笔记列表 JSON，键格式 {@code trip:xhs:dest:{destination}}，
 * TTL 24h（ADR-006 短期状态层）。写入/读取失败仅记录告警，不阻塞主链路。</p>
 */
public final class RedisXHSNoteCache implements XHSNoteCache {

    private static final System.Logger LOG = System.getLogger(RedisXHSNoteCache.class.getName());
    private static final String KEY_PREFIX = CacheConstants.KEY_PREFIX + "xhs:dest:";
    private static final TypeReference<List<XHSNote>> LIST_TYPE = new TypeReference<>() { };

    private final RedisValueOps redisOps;
    private final ObjectMapper objectMapper;

    /**
     * @param redisOps     Redis 值操作端口
     * @param objectMapper JSON 序列化
     */
    public RedisXHSNoteCache(RedisValueOps redisOps, ObjectMapper objectMapper) {
        this.redisOps = redisOps;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<List<XHSNote>> get(String destination) {
        try {
            String json = redisOps.get(key(destination));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, LIST_TYPE));
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "XHS 笔记缓存读取失败: destination={0}, error={1}",
                    destination, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String destination, List<XHSNote> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(notes);
            redisOps.set(key(destination), json, CacheConstants.DEFAULT_TTL);
        } catch (JsonProcessingException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "XHS 笔记缓存写入失败: destination={0}, error={1}",
                    destination, ex.getMessage());
        }
    }

    private static String key(String destination) {
        return KEY_PREFIX + destination;
    }

    /**
     * Redis 值操作端口（便于测试替身，生产侧用 StringRedisTemplate 适配）。
     */
    public interface RedisValueOps {

        /**
         * 写入带 TTL 的字符串值。
         *
         * @param key     Redis Key
         * @param value   值
         * @param timeout 过期时间
         */
        void set(String key, String value, Duration timeout);

        /**
         * 读取字符串值。
         *
         * @param key Redis Key
         * @return 值，不存在返回 null
         */
        String get(String key);
    }

}
