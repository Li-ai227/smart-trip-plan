package com.smart.trip.skills.memory;

import com.smart.trip.common.dto.MemoryContext;
import com.smart.trip.skills.observability.AgentMetrics;
import io.agentscope.core.memory.reme.ReMeClient;
import io.agentscope.core.memory.reme.ReMeSearchRequest;
import io.agentscope.core.memory.reme.ReMeSearchResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆召回服务（C-015 AC-4 / AC-6 / AC-9 可观测性）。
 *
 * <p>通过 {@link ReMeClient} 调用 ReMe Server {@code /retrieve_personal_memory} 端点，
 * 按目的地/风格/预算相似度召回用户历史与偏好，封装为 {@link MemoryContext}。
 * 调用失败降级为 {@link MemoryContext#FALLBACK}（{@code memoryFallback=true}），
 * 不阻塞主链路。</p>
 *
 * <p>可选注入 {@link AgentMetrics} 以记录 {@code memory.recall.latency}、
 * {@code memory.recall.hit}、{@code memory.fallback.count} 指标。</p>
 */
public final class MemoryRecallService implements MemoryRecaller {

    private static final System.Logger LOG = System.getLogger(MemoryRecallService.class.getName());
    private static final int DEFAULT_TOP_K = 5;

    private final ReMeClient remeClient;
    private final AgentMetrics metrics;

    /**
     * @param remeClient ReMe 客户端，非 null
     */
    public MemoryRecallService(ReMeClient remeClient) {
        this(remeClient, null);
    }

    /**
     * @param remeClient ReMe 客户端，非 null
     * @param metrics    指标埋点（可为 null，null 时跳过指标记录）
     */
    public MemoryRecallService(ReMeClient remeClient, AgentMetrics metrics) {
        this.remeClient = remeClient;
        this.metrics = metrics;
    }

    /**
     * 召回用户记忆上下文。
     *
     * @param userId      用户 ID（workspace 隔离维度），null 时返回空
     * @param destination 目的地（召回 query 关键词）
     * @param travelStyle 旅行风格（召回 query 补充）
     * @return 记忆上下文，失败时 {@code memoryFallback=true}
     */
    public MemoryContext recall(String userId, String destination, String travelStyle) {
        if (userId == null || userId.isBlank()) {
            return MemoryContext.EMPTY;
        }
        long startNanos = System.nanoTime();
        try {
            List<String> profiles = searchSafely(userId,
                    "用户偏好 旅行风格 " + nvl(travelStyle));
            List<String> history = searchSafely(userId,
                    "历史行程 " + nvl(destination));
            List<String> attractions = searchSafely(
                    MemoryWriteService.WORKSPACE_ATTRACTION_KB,
                    "景点推荐 " + nvl(destination));

            boolean hasMemory = !profiles.isEmpty() || !history.isEmpty() || !attractions.isEmpty();
            recordMetrics(userId, destination, startNanos, hasMemory, false);
            return new MemoryContext(profiles, history, attractions, false);
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "记忆召回失败，降级无记忆: userId={0}, error={1}",
                    userId, ex.getMessage());
            recordMetrics(userId, destination, startNanos, false, true);
            return MemoryContext.FALLBACK;
        }
    }

    private void recordMetrics(String userId, String destination,
                                long startNanos, boolean hasMemory, boolean fallback) {
        if (metrics == null) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        metrics.recordMemoryRecallLatency(userId, destination, elapsedMillis);
        metrics.incrementMemoryRecallHit(hasMemory);
        if (fallback) {
            metrics.incrementMemoryFallback();
        }
    }

    private List<String> searchSafely(String workspaceId, String query) {
        ReMeSearchRequest request = ReMeSearchRequest.builder()
                .workspaceId(workspaceId)
                .query(query)
                .topK(DEFAULT_TOP_K)
                .build();
        ReMeSearchResponse response = remeClient.search(request).block();
        if (response == null || response.getAnswer() == null || response.getAnswer().isBlank()) {
            return List.of();
        }
        List<String> memories = response.getMemories();
        if (memories != null && !memories.isEmpty()) {
            return List.copyOf(memories);
        }
        return new ArrayList<>(List.of(response.getAnswer()));
    }

    private static String nvl(String value) {
        return value != null ? value : "";
    }
}
