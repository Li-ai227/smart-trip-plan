package com.smart.trip.skills.supervisor;

import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import java.util.concurrent.atomic.AtomicReference;

/**
 * C-012-C：Supervisor native path 的 run-scoped artifact 容器。
 *
 * <p>每次 {@link SupervisorHarnessAgentFactory#run} 创建独立实例，
 * 四个子 Agent 的 {@code @Tool} 调用结果写入此容器，由 native finalizer 汇总为
 * {@link TripPlanResult}。避免跨请求共享单例 {@code lastResult} 导致并发串扰。</p>
 *
 * <p>所有字段初始为 {@code null}；每个 setter 写入后立即对调用者可见（单线程 run 模型）。
 * 每次 setter 调用同时触发进度通知（若已配置 {@link ProgressListener}），
 * 使前端轮询可感知子 Agent 的逐步完成进度。</p>
 */
public final class SupervisorNativeRunContext {

    /** 编排进度监听器：子 Agent 完成时回调，供前端轮询进度。 */
    @FunctionalInterface
    public interface ProgressListener {
        /** 推一条进度节点。 @param planId 方案 ID @param step 进度标识（如 planning/assembling/budgeting） */
        void onStep(String planId, String step);
    }

    /** 子 Agent 完成后的进度标识。前端 STEP 映射: analyzing=0, planning=1, assembling=2, budgeting=3。 */
    static final String PROGRESS_PLANNING = "planning";
    static final String PROGRESS_ASSEMBLING = "assembling";
    static final String PROGRESS_BUDGETING = "budgeting";

    private final AtomicReference<XHSAnalysisResult> xhsResult = new AtomicReference<>();
    private final AtomicReference<RoutePlanResult> routeResult = new AtomicReference<>();
    private final AtomicReference<ItineraryDesignResult> itineraryResult = new AtomicReference<>();
    private final AtomicReference<BudgetCalculationResult> budgetResult = new AtomicReference<>();

    private final AtomicReference<String> planId = new AtomicReference<>();
    private final AtomicReference<ProgressListener> progressListener = new AtomicReference<>();

    /** 设置方案 ID（由工厂在 run() 开始时传入）。 */
    void setPlanId(String id) {
        this.planId.set(id);
    }

    /** 设置进度监听器（由工厂在 run() 开始时传入，可为 null 跳过推送）。 */
    void setProgressListener(ProgressListener listener) {
        this.progressListener.set(listener);
    }

    /** @return 小红书分析产物，或 null（尚未写入） */
    public XHSAnalysisResult xhsResult() {
        return xhsResult.get();
    }

    /** @param result 小红书分析产物 */
    public void setXhsResult(XHSAnalysisResult result) {
        this.xhsResult.set(result);
        notifyProgress(PROGRESS_PLANNING);
    }

    /** @return 路线规划产物，或 null（尚未写入） */
    public RoutePlanResult routeResult() {
        return routeResult.get();
    }

    /** @param result 路线规划产物 */
    public void setRouteResult(RoutePlanResult result) {
        this.routeResult.set(result);
        notifyProgress(PROGRESS_ASSEMBLING);
    }

    /** @return 行程编排产物，或 null（尚未写入） */
    public ItineraryDesignResult itineraryResult() {
        return itineraryResult.get();
    }

    /** @param result 行程编排产物 */
    public void setItineraryResult(ItineraryDesignResult result) {
        this.itineraryResult.set(result);
        notifyProgress(PROGRESS_BUDGETING);
    }

    /** @return 预算核算产物，或 null（尚未写入） */
    public BudgetCalculationResult budgetResult() {
        return budgetResult.get();
    }

    /** @param result 预算核算产物 */
    public void setBudgetResult(BudgetCalculationResult result) {
        this.budgetResult.set(result);
    }

    /**
     * 四个子 Agent artifact 是否全部非空（即 native path 可成功汇总）。
     *
     * @return true 当且仅当四个 artifact 均非 null
     */
    public boolean isComplete() {
        return xhsResult.get() != null
                && routeResult.get() != null
                && itineraryResult.get() != null
                && budgetResult.get() != null;
    }

    private void notifyProgress(String step) {
        ProgressListener l = progressListener.get();
        String pid = planId.get();
        if (l != null && pid != null) {
            l.onStep(pid, step);
        }
    }
}
