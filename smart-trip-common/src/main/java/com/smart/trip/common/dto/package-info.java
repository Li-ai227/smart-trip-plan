/**
 * 公共数据传输对象（C-004）。
 *
 * <p>{@code TripPlanRequest} 承载对外 REST 请求体（接口协议.md §1.1），字段加 {@code jakarta.validation}
 * 声明注解并提供显式 {@code validate()}（决策 D-2）。DTO 为不可变 record。</p>
 */
package com.smart.trip.common.dto;
