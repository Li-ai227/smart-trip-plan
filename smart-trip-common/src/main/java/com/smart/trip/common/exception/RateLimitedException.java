package com.smart.trip.common.exception;

/**
 * 限频异常，固定错误码 {@link ErrorCode#RATE_LIMITED}（接口协议.md §1.1，HTTP 429）。
 *
 * <p>用于外部 API 限频命中（小红书 ≤10/min、地图 ≤30/min、通义 ≤60/min）后的统一上抛。</p>
 */
public class RateLimitedException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造限频异常。
     *
     * @param message 可读错误信息，应指明命中限频的外部 API 与窗口
     */
    public RateLimitedException(String message) {
        super(ErrorCode.RATE_LIMITED, message);
    }

    /**
     * 构造限频异常并保留根因。
     *
     * @param message 可读错误信息
     * @param cause   根因异常
     */
    public RateLimitedException(String message, Throwable cause) {
        super(ErrorCode.RATE_LIMITED, message, cause);
    }
}
