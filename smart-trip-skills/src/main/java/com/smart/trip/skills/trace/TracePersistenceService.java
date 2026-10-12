package com.smart.trip.skills.trace;

/**
 * 追踪持久化服务接口（C-010 trace 方案）。
 *
 * <p>定义子 Agent 委派、LLM 调用、编排汇总三类审计记录的异步写入契约。
 * 接口放 smart-trip-skills，实现放 smart-trip-agent-supervisor（MySQL）。
 * SupervisorAgent 和 TripOrchestrationService 仅依赖此接口，不直接依赖 MySQL 实现。</p>
 *
 * <p>所有方法均为异步（fire-and-forget）：写入失败仅记录 WARN 日志 + metrics 计数，
 * 不阻塞、不抛异常到主链路。</p>
 */
public interface TracePersistenceService {

    /**
     * 异步写入一条子 Agent 委派记录。
     *
     * @param record 委派记录，非 null
     */
    void saveDelegation(TraceDelegationRecord record);

    /**
     * 异步写入一条 LLM 调用记录。
     *
     * @param record LLM 调用记录，非 null
     */
    void saveLlmCall(TraceLlmCallRecord record);

    /**
     * 异步写入编排汇总记录。
     *
     * @param summary 编排汇总，非 null
     */
    void saveOrchestrationSummary(TraceOrchestrationSummary summary);
}
