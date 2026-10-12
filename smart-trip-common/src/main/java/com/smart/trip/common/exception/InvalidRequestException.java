package com.smart.trip.common.exception;

/**
 * 参数缺失/非法异常，固定错误码 {@link ErrorCode#INVALID_REQUEST}（接口协议.md §1.1，HTTP 400）。
 *
 * <p>用于 DTO 显式自校验（如 {@code days ≤ 0}、{@code budget ≤ 0}）与 ID 生成的范围越界。</p>
 */
public class InvalidRequestException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造非法请求异常。
     *
     * @param message 可读错误信息，应指明非法字段与原因
     */
    public InvalidRequestException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }

    /**
     * 构造非法请求异常并保留根因。
     *
     * @param message 可读错误信息
     * @param cause   根因异常
     */
    public InvalidRequestException(String message, Throwable cause) {
        super(ErrorCode.INVALID_REQUEST, message, cause);
    }
}
