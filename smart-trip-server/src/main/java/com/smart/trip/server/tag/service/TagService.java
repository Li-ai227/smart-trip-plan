package com.smart.trip.server.tag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.trip.common.constant.CacheKeys;
import com.smart.trip.common.dto.TagResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 标签服务（C-014）。
 *
 * <p>负责从 knowledge-base.json 提取标签、统计频次、排序并缓存至 Redis。</p>
 *
 * @author smart-trip-plan
 */
@Service
public class TagService {

    private static final System.Logger LOG = System.getLogger(TagService.class.getName());

    private static final String KNOWLEDGE_BASE_PATH = "attractions/knowledge-base.json";
    private static final int TOP_N = 30;
    private static final String TAGS_CACHE_KEY = CacheKeys.tagsAllKey();

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 构造函数注入依赖。
     *
     * @param objectMapper JSON 解析器
     * @param redisTemplate Redis 操作模板
     */
    public TagService(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取所有标签列表（Top 30，按频次降序）。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>尝试从 Redis 获取缓存</li>
     *   <li>如果未命中，从 knowledge-base.json 提取并统计</li>
     *   <li>排序取 Top N</li>
     *   <li>写入 Redis（永久不过期）</li>
     *   <li>返回结果</li>
     * </ol>
     *
     * @return 标签响应，包含最多 30 个标签
     */
    public TagResponse getAllTags() {
        // 1. 尝试从 Redis 获取
        List<String> cachedTags = getCachedTags();
        if (cachedTags != null && !cachedTags.isEmpty()) {
            LOG.log(System.Logger.Level.DEBUG, "Cache hit for tags, size: {}", cachedTags.size());
            return TagResponse.of(cachedTags);
        }

        LOG.log(System.Logger.Level.DEBUG, "Cache miss for tags, extracting from knowledge-base.json");

        // 2. 从 JSON 文件提取
        List<String> tags = extractAndSortTags();

        // 3. 缓存结果
        if (!tags.isEmpty()) {
            saveToCache(tags);
        }

        return TagResponse.of(tags);
    }

    /**
     * 从 Redis 获取缓存的标签列表。
     *
     * @return 缓存的标签列表，如果未命中则返回 null
     */
    private List<String> getCachedTags() {
        try {
            String json = redisTemplate.opsForValue().get(TAGS_CACHE_KEY);
            if (json != null) {
                return objectMapper.readValue(json, ArrayList.class);
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Failed to read tags from Redis cache", e);
        }
        return new ArrayList<>();
    }

    /**
     * 将标签列表缓存至 Redis（永久不过期）。
     *
     * @param tags 标签列表
     */
    private void saveToCache(List<String> tags) {
        try {
            String json = objectMapper.writeValueAsString(tags);
            redisTemplate.opsForValue().set(TAGS_CACHE_KEY, json);
            LOG.log(System.Logger.Level.INFO, "Saved {} tags to Redis cache", tags.size());
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "Failed to write tags to Redis cache", e);
        }
    }

    /**
     * 从 knowledge-base.json 提取所有标签并按频次降序排序。
     *
     * @return 排序后的标签列表（Top 30）
     */
    private List<String> extractAndSortTags() {
        try {
            // 读取 JSON 文件
            Map<String, Integer> tagFrequency = new TreeMap<>();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            getClass().getClassLoader().getResourceAsStream(KNOWLEDGE_BASE_PATH),
                            StandardCharsets.UTF_8))) {

                if (reader == null) {
                    LOG.log(System.Logger.Level.WARNING, "knowledge-base.json not found in classpath");
                    return new ArrayList<>();
                }

                String content = reader.lines().collect(Collectors.joining("\n"));
                Map<String, Object> data = objectMapper.readValue(content, Map.class);

                // 遍历所有城市
                List<Map<String, Object>> cities = (List<Map<String, Object>>) data.get("cities");
                if (cities == null) {
                    LOG.log(System.Logger.Level.WARNING, "No 'cities' field in knowledge-base.json");
                    return new ArrayList<>();
                }

                // 遍历所有景点，提取标签
                for (Map<String, Object> city : cities) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> attractions =
                            (List<Map<String, Object>>) city.get("attractions");

                    if (attractions == null) {
                        continue;
                    }

                    for (Map<String, Object> attraction : attractions) {
                        @SuppressWarnings("unchecked")
                        List<String> tags = (List<String>) attraction.get("tags");

                        if (tags != null) {
                            for (String tag : tags) {
                                tagFrequency.merge(tag, 1, Integer::sum);
                            }
                        }
                    }
                }
            }

            // 按频次降序排序，取 Top N
            return tagFrequency.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(TOP_N)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "Failed to extract tags from knowledge-base.json", e);
            return new ArrayList<>();
        }
    }
}
