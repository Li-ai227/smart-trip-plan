package com.smart.trip.skills.xhs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.XHSNote;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RedisXHSNoteCache} 单测（C-015 T-5 / TDD Red→Green）。
 */
class RedisXHSNoteCacheTest {

    private StubRedisOps redisOps;
    private RedisXHSNoteCache cache;

    @BeforeEach
    void setUp() {
        redisOps = new StubRedisOps();
        cache = new RedisXHSNoteCache(redisOps, new ObjectMapper().findAndRegisterModules());
    }

    private static XHSNote note(String id, String title) {
        return new XHSNote(id, title, "内容", List.of("美食"), 100,
                List.of(new AttractionMention("西湖", SentimentType.POSITIVE)));
    }

    @Test
    @DisplayName("put 后 get 返回相同笔记")
    void putThenGet_returnsNotes() {
        List<XHSNote> notes = List.of(note("n1", "杭州攻略"), note("n2", "西湖游记"));
        cache.put("杭州", notes);

        Optional<List<XHSNote>> result = cache.get("杭州");
        assertTrue(result.isPresent());
        assertEquals(2, result.get().size());
        assertEquals("杭州攻略", result.get().get(0).title());
    }

    @Test
    @DisplayName("未缓存的目的地返回 empty")
    void get_uncachedDestination_returnsEmpty() {
        Optional<List<XHSNote>> result = cache.get("未知城市");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("put 设置 24h TTL")
    void put_sets24hTtl() {
        cache.put("上海", List.of(note("n3", "上海攻略")));
        assertEquals(Duration.ofHours(24), redisOps.ttls.values().iterator().next());
    }

    @Test
    @DisplayName("空列表 put 不写入 Redis")
    void put_emptyList_noWrite() {
        cache.put("空城", List.of());
        assertTrue(redisOps.store.isEmpty());
    }

    /**
     * 内存 Redis 替身：模拟 StringRedisTemplate 的 ValueOperations。
     */
    static class StubRedisOps implements RedisXHSNoteCache.RedisValueOps {

        final Map<String, String> store = new HashMap<>();
        final Map<String, Duration> ttls = new HashMap<>();

        @Override
        public void set(String key, String value, Duration timeout) {
            store.put(key, value);
            ttls.put(key, timeout);
        }

        @Override
        public String get(String key) {
            return store.get(key);
        }
    }
}
