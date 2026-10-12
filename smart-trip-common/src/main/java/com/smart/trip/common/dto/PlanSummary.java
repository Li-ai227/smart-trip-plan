package com.smart.trip.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 方案摘要（历史列表用）—— 轻量级，独立于完整 TripPlanDetail JSON。
 *
 * <p>存储在 {@code trip:plan:{planId}:summary}，TTL 30 天（超过方案详情 24h TTL，
 * 使历史列表在详情过期后仍可显示摘要信息）。</p>
 *
 * @param planId      方案 ID（格式 P-yyyyMMdd-NNN）
 * @param destination 目的地
 * @param startDate   起始日期
 * @param days        天数
 * @param budget      总预算（元）
 * @param totalCost   实际花费（方案完成后填入，未完成为 null）
 * @param headcount   人数
 * @param travelStyle 旅行风格（可空）
 * @param status      方案状态（PlanStatus name，lowercase）
 * @param createdAt   创建时间戳（epoch millis）
 */
public record PlanSummary(
        String planId,
        String destination,
        LocalDate startDate,
        int days,
        BigDecimal budget,
        BigDecimal totalCost,
        int headcount,
        String travelStyle,
        String status,
        long createdAt) {
}
