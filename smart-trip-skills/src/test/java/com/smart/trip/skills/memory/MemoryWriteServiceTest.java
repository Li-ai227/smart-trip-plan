package com.smart.trip.skills.memory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.TravelStyle;
import io.agentscope.core.memory.reme.ReMeAddRequest;
import io.agentscope.core.memory.reme.ReMeAddResponse;
import io.agentscope.core.memory.reme.ReMeClient;
import io.agentscope.core.memory.reme.ReMeSearchRequest;
import io.agentscope.core.memory.reme.ReMeSearchResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * {@link MemoryWriteService} 单测（C-015 T-3 / TDD Red→Green）。
 */
class MemoryWriteServiceTest {

    private RecordingReMeClient remeClient;
    private MemoryWriteService writeService;

    @BeforeEach
    void setUp() {
        remeClient = new RecordingReMeClient();
        writeService = new MemoryWriteService(remeClient);
    }

    private static TripPlanRequest request() {
        return new TripPlanRequest("杭州", LocalDate.of(2026, 7, 1), 3,
                new BigDecimal("5000"), 2, TravelStyle.FAMILY,
                List.of("西湖", "美食"));
    }

    @Nested
    @DisplayName("用户偏好写入（AC-3②）")
    class UserProfile {

        @Test
        @DisplayName("写入 travelStyle + preferences 到用户 workspace")
        void writesUserProfileToUserWorkspace() {
            writeService.writeUserProfile("user-001", request());

            assertEquals(1, remeClient.addRequests.size());
            ReMeAddRequest req = remeClient.addRequests.get(0);
            assertEquals("user-001", req.getWorkspaceId());
            assertTrue(req.getTrajectories().size() > 0);
        }

        @Test
        @DisplayName("无风格且偏好为空时跳过写入")
        void should_skip_write_when_no_style_and_empty_preferences() {
            TripPlanRequest noProfileReq = new TripPlanRequest(
                    "杭州", LocalDate.of(2026, 7, 1), 3,
                    new BigDecimal("5000"), 2, null, List.of());

            writeService.writeUserProfile("user-001", noProfileReq);

            assertTrue(remeClient.addRequests.isEmpty());
        }
    }

    @Nested
    @DisplayName("行程摘要写入（AC-3①）")
    class HistoryPlan {

        @Test
        @DisplayName("confirmed 后写入行程摘要到用户 workspace")
        void writesHistoryPlanToUserWorkspace() {
            writeService.writeHistoryPlan("user-001", "P-20260701-001",
                    "杭州 3 天家庭游，总费用 4500 元，游览西湖、灵隐寺");

            assertEquals(1, remeClient.addRequests.size());
            ReMeAddRequest req = remeClient.addRequests.get(0);
            assertEquals("user-001", req.getWorkspaceId());
        }

        @Test
        @DisplayName("摘要为空白时跳过写入")
        void should_skip_write_when_summary_blank() {
            writeService.writeHistoryPlan("user-001", "P-001", "   ");

            assertTrue(remeClient.addRequests.isEmpty());
        }

        @Test
        @DisplayName("写入内容包含 planId 标识")
        void should_include_planId_in_content() {
            writeService.writeHistoryPlan("user-001", "P-001", "杭州三日游");

            assertFalse(remeClient.addRequests.isEmpty());
            ReMeAddRequest req = remeClient.addRequests.get(0);
            String content = req.getTrajectories().get(0)
                    .getMessages().get(0).getContent();
            assertTrue(content.contains("[planId=P-001]"));
        }
    }

    @Nested
    @DisplayName("景点知识写入（AC-3③）")
    class AttractionKb {

        @Test
        @DisplayName("XHS 分析产出写入 attraction_kb workspace")
        void writesAttractionKbToSharedWorkspace() {
            writeService.writeAttractionKnowledge(
                    List.of("西湖：5A 景区，免费，建议游玩 3 小时"));

            assertEquals(1, remeClient.addRequests.size());
            ReMeAddRequest req = remeClient.addRequests.get(0);
            assertEquals("attraction_kb", req.getWorkspaceId());
        }
    }

    @Nested
    @DisplayName("幂等性（边界-4）")
    class Idempotency {

        @Test
        @DisplayName("重复写入同一 planId 使用相同 workspaceId")
        void should_send_same_workspace_for_repeated_writes() {
            writeService.writeHistoryPlan("user-001", "P-001", "杭州三日游");
            writeService.writeHistoryPlan("user-001", "P-001", "杭州三日游");

            assertEquals(2, remeClient.addRequests.size());
            assertEquals(remeClient.addRequests.get(0).getWorkspaceId(),
                    remeClient.addRequests.get(1).getWorkspaceId());
        }
    }

    @Nested
    @DisplayName("降级与异常隔离")
    class Degradation {

        @Test
        @DisplayName("ReMeClient 异常不传播，仅记录告警")
        void remeException_doesNotPropagate() {
            remeClient.failOnNextAdd = true;
            assertDoesNotThrow(() ->
                    writeService.writeUserProfile("user-001", request()));
        }

        @Test
        @DisplayName("空 attractions 列表不发送请求")
        void emptyAttractions_noRequest() {
            writeService.writeAttractionKnowledge(List.of());
            assertTrue(remeClient.addRequests.isEmpty());
        }

        @Test
        @DisplayName("行程摘要写入异常不传播")
        void should_not_propagate_remeException_on_historyPlan_write() {
            remeClient.failOnNextAdd = true;
            assertDoesNotThrow(() ->
                    writeService.writeHistoryPlan("user-001", "P-001", "杭州三日游"));
        }

        @Test
        @DisplayName("景点知识写入异常不传播")
        void should_not_propagate_remeException_on_attractionKb_write() {
            remeClient.failOnNextAdd = true;
            assertDoesNotThrow(() ->
                    writeService.writeAttractionKnowledge(
                            List.of("西湖：5A 景区，免费")));
        }
    }

    /**
     * 记录式 ReMeClient 替身：记录所有 add 请求，可模拟失败。
     */
    static class RecordingReMeClient extends ReMeClient {

        final List<ReMeAddRequest> addRequests = new ArrayList<>();
        volatile boolean failOnNextAdd = false;

        RecordingReMeClient() {
            super("http://localhost:9999");
        }

        @Override
        public Mono<ReMeAddResponse> add(ReMeAddRequest request) {
            if (failOnNextAdd) {
                failOnNextAdd = false;
                return Mono.error(new RuntimeException("ReMe 模拟故障"));
            }
            addRequests.add(request);
            ReMeAddResponse resp = new ReMeAddResponse();
            resp.setSuccess(true);
            return Mono.just(resp);
        }

        @Override
        public Mono<ReMeSearchResponse> search(ReMeSearchRequest request) {
            return Mono.just(new ReMeSearchResponse());
        }
    }
}
