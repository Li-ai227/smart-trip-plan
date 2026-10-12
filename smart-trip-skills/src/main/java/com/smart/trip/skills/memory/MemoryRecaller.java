package com.smart.trip.skills.memory;

import com.smart.trip.common.dto.MemoryContext;

/**
 * 记忆召回端口（C-015 T-6）。
 *
 * <p>函数式接口，用于将记忆召回能力注入编排层。
 * 生产实现为 {@link MemoryRecallService}，测试可注入 lambda 桩。</p>
 */
@FunctionalInterface
public interface MemoryRecaller {

    /**
     * 按用户维度召回记忆上下文。
     *
     * @param userId      用户 ID
     * @param destination 目的地
     * @param travelStyle 旅行风格
     * @return 记忆上下文，不应为 null
     */
    MemoryContext recall(String userId, String destination, String travelStyle);
}
