package com.smart.trip.common.exception;

/**
 * 下游不可用异常，固定错误码 {@link ErrorCode#SERVICE_UNAVAILABLE}（接口协议.md §1.1，HTTP 503）。
 *
 * <p>用于下游 Agent / 外部 API 在重试与降级均不可用后的统一上抛；对外响应需带 {@code traceId}。</p>
 */
public class DownstreamUnavailableException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造下游不可用异常。
     *
     * @param message 可读错误信息，应指明不可用的下游标识
     */
    public DownstreamUnavailableException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }

    /**
     * 构造下游不可用异常并保留根因。
     *
     * @param message 可读错误信息
     * @param cause   根因异常
     */
    public DownstreamUnavailableException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }
}
