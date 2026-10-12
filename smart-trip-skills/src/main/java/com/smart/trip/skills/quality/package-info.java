/**
 * 全局一致性统一复核层（C-005 T-6）。
 *
 * <p>{@code OutputQualityGate} 对装配后的 TripPlan 统一复核全局不变量（天数一致、景点不重复、
 * 每日午晚餐、预算红线、评分阈值），违规标记 {@code requiresHITL} 并给出原因而不丢弃数据
 * （业务模型.md §4.1）。</p>
 */
package com.smart.trip.skills.quality;
