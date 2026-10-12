package com.smart.trip.skills.memory;

import com.smart.trip.common.dto.TripPlanRequest;
import io.agentscope.core.memory.reme.ReMeAddRequest;
import io.agentscope.core.memory.reme.ReMeClient;
import io.agentscope.core.memory.reme.ReMeMessage;
import io.agentscope.core.memory.reme.ReMeTrajectory;
import java.util.List;

/**
 * 记忆写入服务（C-015 AC-3）。
 *
 * <p>通过 {@link ReMeClient} 调用 ReMe Server {@code /summary_personal_memory} 端点，
 * 支撑三类记忆写入：</p>
 * <ol>
 *   <li>用户偏好（travelStyle + preferences → workspace = userId）</li>
 *   <li>行程摘要（confirmed 方案 → workspace = userId）</li>
 *   <li>景点知识（XHS 分析产出 → workspace = attraction_kb）</li>
 * </ol>
 *
 * <p>写入失败仅记录告警，不阻塞主链路（AC-6 降级约束）。</p>
 */
public final class MemoryWriteService {

    private static final System.Logger LOG = System.getLogger(MemoryWriteService.class.getName());

    /** 景点知识共享 workspace。 */
    static final String WORKSPACE_ATTRACTION_KB = "attraction_kb";

    private final ReMeClient remeClient;

    /**
     * @param remeClient ReMe 客户端，非 null
     */
    public MemoryWriteService(ReMeClient remeClient) {
        this.remeClient = remeClient;
    }

    /**
     * 写入用户偏好记忆（AC-3②）。
     *
     * @param userId  用户 ID（workspace 隔离维度）
     * @param request 规划请求（提取 travelStyle + preferences）
     */
    public void writeUserProfile(String userId, TripPlanRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.travelStyle() != null) {
            sb.append("旅行风格: ").append(request.travelStyle().name());
        }
        if (request.preferences() != null && !request.preferences().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append("偏好: ").append(String.join(", ", request.preferences()));
        }
        if (sb.isEmpty()) {
            return;
        }
        addMemorySafely(userId, sb.toString());
    }

    /**
     * 写入行程摘要记忆（AC-3①）。
     *
     * @param userId      用户 ID（workspace 隔离维度）
     * @param planId      方案 ID（用于幂等标识）
     * @param summaryText 行程摘要文本
     */
    public void writeHistoryPlan(String userId, String planId, String summaryText) {
        if (summaryText == null || summaryText.isBlank()) {
            return;
        }
        String content = "[planId=" + planId + "] " + summaryText;
        addMemorySafely(userId, content);
    }

    /**
     * 写入景点知识记忆（AC-3③）。
     *
     * @param attractionTexts 景点描述文本列表
     */
    public void writeAttractionKnowledge(List<String> attractionTexts) {
        if (attractionTexts == null || attractionTexts.isEmpty()) {
            return;
        }
        String content = String.join("\n", attractionTexts);
        addMemorySafely(WORKSPACE_ATTRACTION_KB, content);
    }

    private void addMemorySafely(String workspaceId, String content) {
        try {
            ReMeTrajectory trajectory = ReMeTrajectory.builder()
                    .messages(List.of(new ReMeMessage("user", content)))
                    .build();
            ReMeAddRequest request = ReMeAddRequest.builder()
                    .workspaceId(workspaceId)
                    .trajectories(List.of(trajectory))
                    .build();
            remeClient.add(request).block();
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "记忆写入失败（降级忽略）: workspace={0}, error={1}",
                    workspaceId, ex.getMessage());
        }
    }
}
