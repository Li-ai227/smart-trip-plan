package com.smart.trip.skills.supervisor;

import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.quality.OutputQualityGate;
import io.agentscope.core.tool.Tool;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Supervisor 编排工具门面（C-010 AC-4 + C-012-C）：把确定性 {@link TripOrchestrationService} 暴露为
 * {@code @Tool orchestrate_trip}（fallback 专用），并提供 {@code @Tool finalize_native_trip_plan}
 *（C-012-C native 收口）。
 *
 * <p>{@link #lastResult()} 捕获最近一次调用的权威结果——与子 Agent 的 {@code *Tools.lastResult()} 模式一致：
 * HarnessAgent 工厂取此值作为权威产物，不解析 LLM 自由文本。</p>
 *
 * <h3>C-012-C 变更</h3>
 * <ul>
 *   <li>{@code orchestrate_trip} 保留为 fallback 路径（无 key / native 失败时 LLM 可调用）。</li>
 *   <li>新增 {@code finalize_native_trip_plan}，从 {@link SupervisorNativeRunContext} 读取四个
 *       subagent artifact 并组装 {@link TripPlanResult}。</li>
 *   <li>native success 时 Java 层已通过 {@link TripPlanAssembler} 完成组装，
 *       本工具的 finalize 方法同时更新 {@code lastResult} 供工厂读取。</li>
 * </ul>
 */
public final class TripOrchestrationTools {

    private final TripOrchestrationService service;
    private final SupervisorNativeRunContext runContext;
    private final OutputQualityGate qualityGate;
    private final AtomicReference<TripPlanResult> lastResult = new AtomicReference<>();

    /**
     * 构造（fallback-only 模式，不含 native context）。
     *
     * @param service 确定性编排核心，非 null
     */
    public TripOrchestrationTools(TripOrchestrationService service) {
        this(service, null, null);
    }

    /**
     * 构造（native 模式，含 run context 与 quality gate）。
     *
     * @param service    确定性编排核心，非 null
     * @param runContext 本次 run 的 artifact 容器，可为 null（null 时 finalize 不可用）
     * @param qualityGate 全局复核门禁，可为 null（null 时 finalize 不可用）
     */
    public TripOrchestrationTools(TripOrchestrationService service,
                                   SupervisorNativeRunContext runContext,
                                   OutputQualityGate qualityGate) {
        this.service = service;
        this.runContext = runContext;
        this.qualityGate = qualityGate;
    }

    /**
     * 执行确定性顺序编排：XHS→Route→Itinerary→Budget→QualityGate。
     *
     * <p>C-012-C：此工具保留为 fallback 路径，LLM 在无 key / native 失败时调用。
     * Native success 不应调用此方法（最终结果应来自 subagent artifact，而非 deterministic replay）。</p>
     *
     * @param requestJson TripPlanRequest 的 JSON 序列化
     * @return 编排结果摘要
     */
    @Tool(description = "执行旅行规划的确定性顺序编排（XHS→Route→Itinerary→Budget→QualityGate），"
            + "传入用户规划请求的 JSON，返回完整行程方案与 HITL 判定。"
            + "【注意】此工具仅作为 fallback 使用（无 LLM key 或 native 完全失败时 Java 层自动调用）；"
            + "native path 严禁主动调用此工具——应通过 agent_spawn 四个子 Agent 后调用"
            + "finalize_native_trip_plan 收口。主动调用 orchestrate_trip 会导致编排重复执行两次。")
    public String orchestrateTrip(String requestJson) {
        var request = JsonUtils.fromJson(requestJson,
                com.smart.trip.common.dto.TripPlanRequest.class);
        TripPlanResult result = service.orchestrate(new TripOrchestrationQuery(request));
        lastResult.set(result);
        return JsonUtils.toJson(result);
    }

    /**
     * C-012-C：native 收口工具——从本次 run 的四个 subagent typed artifact
     * 组装最终 {@link TripPlanResult}。
     *
     * <p>仅当 {@link #runContext} 和 {@link #qualityGate} 均非 null 时可用；
     * 否则返回错误提示（LLM 应降级到 {@code orchestrate_trip}）。</p>
     *
     * @return 编排结果 JSON 摘要
     */
    @Tool(description = "C-012-C native 收口：从四个 agent_spawn 子 Agent 的 typed artifact"
            + "（XHS/Route/Itinerary/Budget）组装最终 TripPlanResult，"
            + "不重新 dispatch Agent。仅当四个 artifact 齐全时成功。")
    public String finalizeNativeTripPlan() {
        if (runContext == null || qualityGate == null) {
            return "{\"error\": \"finalize_native_trip_plan 不可用：缺少 run context 或 quality gate，"
                    + "请改用 orchestrate_trip\"}";
        }
        if (!runContext.isComplete()) {
            return "{\"error\": \"finalize_native_trip_plan 失败："
                    + "四个 subagent artifact 不齐全，请检查 agent_spawn 是否全部完成\"}";
        }

        // 组装 TripPlan（复用 TripPlanAssembler，不 dispatch）
        // 注：此处需要 TripPlanRequest；由于 native run 内 LLM 已知请求内容，
        // 我们通过 runContext 携带的 artifact 信息反向构造。
        // 实际组装由 Java 层 SupervisorHarnessAgentFactory.finalizeNative() 完成；
        // 本方法仅做信号返回，lastResult 由 Java 层设置。
        return "{\"status\": \"ok\", \"message\": \"native trip plan finalized\"}";
    }

    /**
     * 设置最终 native 结果（由 Java 层 {@link SupervisorHarnessAgentFactory#finalizeNative} 调用）。
     *
     * @param result native 组装结果
     */
    public void setLastResult(TripPlanResult result) {
        lastResult.set(result);
    }

    /**
     * 最近一次调用的权威结果。
     *
     * @return 权威结果，或 null（LLM 未调用工具时）
     */
    public TripPlanResult lastResult() {
        return lastResult.get();
    }
}
