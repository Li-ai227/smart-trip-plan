/**
 * 统一异常体系（C-004 AC-4）。
 *
 * <p>{@link com.smart.trip.common.exception.BaseException} 为业务异常基类，子类携带
 * {@link com.smart.trip.common.exception.ErrorCode}，对齐 {@code .harness/wiki/接口协议.md} §1.1 错误码
 * （{@code INVALID_REQUEST / RATE_LIMITED / SERVICE_UNAVAILABLE}）。</p>
 */
package com.smart.trip.common.exception;
