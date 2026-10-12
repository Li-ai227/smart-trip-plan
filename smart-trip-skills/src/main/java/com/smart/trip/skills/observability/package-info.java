/**
 * 可观测性贯穿（C-005 T-7）。
 *
 * <p>{@code TraceContext} 贯穿 {@code traceId/planId/agentId/taskType/msgId/fallback} 到日志，
 * {@code AgentMetrics} 暴露接口协议.md §6 关键指标埋点位（调用延迟/错误/限频命中）。</p>
 */
package com.smart.trip.skills.observability;
