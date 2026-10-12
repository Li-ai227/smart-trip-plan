package com.smart.trip.skills.governance;

import com.smart.trip.common.exception.BaseException;
import com.smart.trip.common.exception.ErrorCode;

/**
 * 调用链深度超限异常（C-005 AC-4 / 边界 #2）。
 *
 * <p>当 A2A 委派链深度达到上限（{@link CallDepthGuard#MAX_DEPTH}）仍尝试更深委派时抛出，
 * 防死锁/死循环。归入项目异常体系并采用 {@link ErrorCode#SERVICE_UNAVAILABLE}，对外可识别
 * （接口协议.md §1.1 → HTTP 503），区别于静默超时。</p>
 */
public class CallChainDepthExceededException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造调用链深度超限异常。
     *
     * @param message 可读错误信息，应指明当前深度与上限
     */
    public CallChainDepthExceededException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }
}
