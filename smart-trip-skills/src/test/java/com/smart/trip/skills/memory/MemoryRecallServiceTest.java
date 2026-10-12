package com.smart.trip.skills.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.MemoryContext;
import com.smart.trip.skills.observability.AgentMetrics;
import io.agentscope.core.memory.reme.ReMeAddRequest;
import io.agentscope.core.memory.reme.ReMeAddResponse;
import io.agentscope.core.memory.reme.ReMeClient;
import io.agentscope.core.memory.reme.ReMeSearchRequest;
import io.agentscope.core.memory.reme.ReMeSearchResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * {@link MemoryRecallService} 单测（C-015 T-4 / TDD Red→Green）。
 */
class MemoryRecallServiceTest {

    private StubReMeClient remeClient;
    private MemoryRecallService recallService;

    @BeforeEach
    void setUp() {
        remeClient = new StubReMeClient();
        recallService = new MemoryRecallService(remeClient);
    }

    @Nested
    @DisplayName("Happy Path：召回成功")
    class HappyPath {

        @Test
        @DisplayName("召回用户偏好 + 历史行程 → MemoryContext 非空、非降级")
        void recallsUserMemories() {
            remeClient.profileAnswer = "偏好海鲜、家庭游";
            remeClient.historyAnswer = "2026年杭州3天家庭游，总费用4500元";

            MemoryContext ctx = recallService.recall("user-001", "杭州", "FAMILY");

            assertFalse(ctx.isEmpty());
            assertFalse(ctx.memoryFallback());
            assertFalse(ctx.userProfiles().isEmpty());
            assertFalse(ctx.historyPlans().isEmpty());
        }

        @Test
        @DisplayName("召回景点知识 → attractionKb 非空")
        void recallsAttractionKb() {
            remeClient.attractionAnswer = "西湖 5A 景区，免费开放";

            MemoryContext ctx = recallService.recall("user-001", "杭州", "FAMILY");

            assertFalse(ctx.attractionKb().isEmpty());
        }
    }

    @Nested
    @DisplayName("首次用户 / 无历史")
    class FirstTimeUser {

        @Test
        @DisplayName("新用户无召回结果 → MemoryContext.isEmpty()=true、非降级")
        void newUser_emptyContext_noFallback() {
            MemoryContext ctx = recallService.recall("new-user", "杭州", "SOLO");

            assertTrue(ctx.isEmpty());
            assertFalse(ctx.memoryFallback());
        }
    }

    @Nested
    @DisplayName("降级路径（AC-6）")
    class Degradation {

        @Test
        @DisplayName("ReMeClient 异常 → memoryFallback=true、空记忆")
        void remeException_fallbackTrue() {
            remeClient.failOnSearch = true;

            MemoryContext ctx = recallService.recall("user-001", "杭州", "FAMILY");

            assertTrue(ctx.memoryFallback());
            assertTrue(ctx.isEmpty());
        }
    }

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("userId 为 null → 返回 EMPTY 而非异常")
        void nullUserId_returnsEmpty() {
            MemoryContext ctx = recallService.recall(null, "杭州", "FAMILY");
            assertTrue(ctx.isEmpty());
            assertFalse(ctx.memoryFallback());
        }
    }

    @Nested
    @DisplayName("可观测性指标（AC-9）")
    class Metrics {

        private SimpleMeterRegistry meterRegistry;
        private MemoryRecallService metricsRecallService;

        @BeforeEach
        void setUp() {
            meterRegistry = new SimpleMeterRegistry();
            AgentMetrics agentMetrics = new AgentMetrics(meterRegistry);
            metricsRecallService = new MemoryRecallService(remeClient, agentMetrics);
        }

        @Test
        @DisplayName("召回成功 → 记录 memory.recall.latency 延迟指标")
        void should_record_latency_when_recall_succeeds() {
            remeClient.profileAnswer = "偏好海鲜";

            metricsRecallService.recall("user-001", "杭州", "FAMILY");

            Timer timer = meterRegistry.find("memory.recall.latency").timer();
            assertTrue(timer != null && timer.count() == 1);
        }

        @Test
        @DisplayName("召回命中记忆 → memory.recall.hit result=hit 计数=1")
        void should_record_hit_when_memory_found() {
            remeClient.profileAnswer = "偏好海鲜";

            metricsRecallService.recall("user-001", "杭州", "FAMILY");

            Counter hitCounter = meterRegistry.find("memory.recall.hit")
                    .tag("result", "hit").counter();
            assertTrue(hitCounter != null && hitCounter.count() == 1.0);
        }

        @Test
        @DisplayName("新用户无记忆 → memory.recall.hit result=miss 计数=1")
        void should_record_miss_when_no_memory() {
            metricsRecallService.recall("new-user", "杭州", "SOLO");

            Counter missCounter = meterRegistry.find("memory.recall.hit")
                    .tag("result", "miss").counter();
            assertTrue(missCounter != null && missCounter.count() == 1.0);
        }

        @Test
        @DisplayName("ReMe 异常 → memory.fallback.count 计数=1")
        void should_record_fallback_when_reme_fails() {
            remeClient.failOnSearch = true;

            metricsRecallService.recall("user-001", "杭州", "FAMILY");

            Counter fallbackCounter = meterRegistry.find("memory.fallback.count").counter();
            assertTrue(fallbackCounter != null && fallbackCounter.count() == 1.0);
        }
    }

    @Nested
    @DisplayName("多用户隔离")
    class UserIsolation {

        @Test
        @DisplayName("不同用户召回 → workspaceId 与 userId 一致，实现 workspace 隔离")
        void should_isolate_memory_by_userId() {
            remeClient.profileAnswer = "偏好海鲜";

            recallService.recall("user-A", "杭州", "FAMILY");
            recallService.recall("user-B", "上海", "SOLO");

            List<String> userAWorkspaces = remeClient.searchRequests.stream()
                    .filter(r -> !"attraction_kb".equals(r.getWorkspaceId()))
                    .filter(r -> "user-A".equals(r.getWorkspaceId()))
                    .map(ReMeSearchRequest::getWorkspaceId)
                    .toList();
            List<String> userBWorkspaces = remeClient.searchRequests.stream()
                    .filter(r -> !"attraction_kb".equals(r.getWorkspaceId()))
                    .filter(r -> "user-B".equals(r.getWorkspaceId()))
                    .map(ReMeSearchRequest::getWorkspaceId)
                    .toList();

            assertFalse(userAWorkspaces.isEmpty(), "user-A 应有 workspace=user-A 的搜索请求");
            assertFalse(userBWorkspaces.isEmpty(), "user-B 应有 workspace=user-B 的搜索请求");
            assertTrue(userAWorkspaces.stream().allMatch("user-A"::equals));
            assertTrue(userBWorkspaces.stream().allMatch("user-B"::equals));
        }
    }

    @Nested
    @DisplayName("参数边界")
    class ParameterBoundary {

        @Test
        @DisplayName("userId 为空白字符串 → 返回 EMPTY 而非降级")
        void should_return_empty_when_userId_blank() {
            MemoryContext ctx = recallService.recall("   ", "杭州", "FAMILY");

            assertTrue(ctx.isEmpty());
            assertFalse(ctx.memoryFallback());
        }

        @Test
        @DisplayName("destination 为 null → 不抛异常")
        void should_handle_null_destination_gracefully() {
            assertDoesNotThrow(() -> recallService.recall("user-001", null, "FAMILY"));
        }

        @Test
        @DisplayName("travelStyle 为 null → 不抛异常")
        void should_handle_null_travelStyle_gracefully() {
            assertDoesNotThrow(() -> recallService.recall("user-001", "杭州", null));
        }
    }

    /**
     * 可控 ReMeClient 替身。
     */
    static class StubReMeClient extends ReMeClient {

        String profileAnswer;
        String historyAnswer;
        String attractionAnswer;
        volatile boolean failOnSearch = false;
        final List<ReMeSearchRequest> searchRequests = new ArrayList<>();

        StubReMeClient() {
            super("http://localhost:9999");
        }

        @Override
        public Mono<ReMeSearchResponse> search(ReMeSearchRequest request) {
            searchRequests.add(request);
            if (failOnSearch) {
                return Mono.error(new RuntimeException("ReMe 模拟故障"));
            }
            ReMeSearchResponse resp = new ReMeSearchResponse();
            String wid = request.getWorkspaceId();
            if ("attraction_kb".equals(wid) && attractionAnswer != null) {
                resp.setAnswer(attractionAnswer);
                resp.setSuccess(true);
            } else if (wid != null && !wid.equals("attraction_kb")) {
                String query = request.getQuery();
                if (query != null && query.contains("偏好") && profileAnswer != null) {
                    resp.setAnswer(profileAnswer);
                    resp.setSuccess(true);
                } else if (query != null && query.contains("行程") && historyAnswer != null) {
                    resp.setAnswer(historyAnswer);
                    resp.setSuccess(true);
                } else {
                    resp.setSuccess(true);
                }
            } else {
                resp.setSuccess(true);
            }
            return Mono.just(resp);
        }

        @Override
        public Mono<ReMeAddResponse> add(ReMeAddRequest request) {
            return Mono.just(new ReMeAddResponse());
        }
    }
}
