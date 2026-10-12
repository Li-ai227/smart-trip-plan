package com.smart.trip.skills.supervisor;

import java.util.List;

/**
 * C-012-C：子 Agent @Tool 实例工厂。
 *
 * <p>每次 {@link SupervisorHarnessAgentFactory#run} 调用时创建全新的工具实例列表，
 * 确保各次 run 的 artifact（XHS / Route / Itinerary / Budget）互不串扰。</p>
 *
 * <p>工厂只需照单创建工具实例，注入顺序应为：
 * {@code XHSAnalysisTools, RoutePlanningTools, ItineraryDesignTools, BudgetCalculationTools}，
 * 索引与 {@link SupervisorNativeRunContext} 写入一一对应。</p>
 */
@FunctionalInterface
public interface SubAgentToolFactory {

    /**
     * 为一次 run 创建全新的子 Agent @Tool 实例列表。
     *
     * @param runContext 本次 run 的 artifact 容器，工具实例应将结果写入此容器
     * @return 新创建的工具实例列表（长度=4，顺序固定）
     */
    List<Object> createTools(SupervisorNativeRunContext runContext);
}
