package com.smart.trip.skills.xhs;

import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.util.CollectionUtils;
import java.util.List;

/**
 * 小红书笔记分析请求（C-006 AC-1/AC-3）。
 *
 * <p>对齐 {@code TASK_ASSIGN(taskType=XHS_ANALYSIS)} 的 {@code context} 载荷
 * （接口协议.md §3）：{@code destination} 必填，其余为偏好性可选项。未知/新增字段由
 * Agent 层在装配本对象前忽略（向后兼容，C-005 BaseAgent 约定）。</p>
 *
 * @param destination 目的地（非空白）
 * @param preferences 偏好标签（如「美食」「小众」），可空（归一为不可变空列表）
 * @param headcount   出行人数（{@code <=0} 视为未指定）
 * @param style       出行风格，可空
 */
public record XHSAnalysisQuery(
        String destination,
        List<String> preferences,
        int headcount,
        TravelStyle style) {

    /**
     * 紧凑构造器：校验目的地非空白（防御式编程，编码规范 §5），偏好做 null 安全不可变拷贝。
     */
    public XHSAnalysisQuery {
        if (destination == null || destination.isBlank()) {
            throw new InvalidRequestException("XHSAnalysisQuery destination 不可为空白");
        }
        preferences = CollectionUtils.safeCopy(preferences);
    }

    /**
     * 便捷构造：仅目的地 + 偏好。
     *
     * @param destination 目的地，非空白
     * @param preferences 偏好标签，可空
     * @return 分析请求（headcount 未指定、style 为 null）
     */
    public static XHSAnalysisQuery of(String destination, List<String> preferences) {
        return new XHSAnalysisQuery(destination, preferences, 0, null);
    }
}
