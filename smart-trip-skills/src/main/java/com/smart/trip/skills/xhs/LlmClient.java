package com.smart.trip.skills.xhs;

/**
 * LLM 调用端口（C-006 增量 B / AC-10）：隔离 {@link LlmXHSNoteParser} 对 LLM 的依赖。
 *
 * <p>真实实现调用 DeepSeek/DashScope 等 LLM API 完成实体抽取；测试以桩实现返回可控 JSON，
 * 保证核心抽取/链接逻辑可离线确定性覆盖（编码规范 §8.2 可测试性设计）。</p>
 */
@FunctionalInterface
public interface LlmClient {

    /**
     * 向 LLM 发送提示并返回原始文本响应。
     *
     * @param prompt 提示文本，非空
     * @return LLM 原始响应文本
     * @throws Exception 当调用超时/故障（驱动统一治理的重试/降级）
     */
    String complete(String prompt) throws Exception;
}
