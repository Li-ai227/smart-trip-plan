package com.smart.trip.common.exception;

/**
 * 统一业务错误码（接口协议.md §1.1 / §5）。
 *
 * <p>枚举常量名即对外返回的 {@code code} 字符串（错误响应结构 {@code {code, message, traceId}}），
 * 由 {@link BaseException#getCode()} 暴露，保证日志与对外响应口径一致。</p>
 */
public enum ErrorCode {

    /** 参数缺失/非法（HTTP 400），如 days ≤ 0。 */
    INVALID_REQUEST,

    /** 触发限频（HTTP 429）。 */
    RATE_LIMITED,

    /** 下游 Agent/外部 API 不可用（HTTP 503）。 */
    SERVICE_UNAVAILABLE,

    /** 未认证/令牌无效（HTTP 401）。 */
    UNAUTHORIZED,

    /** 已认证但无权限（HTTP 403）。 */
    FORBIDDEN,

    /** 资源冲突，如用户名已存在（HTTP 409）。 */
    CONFLICT,

    /** 资源不存在（HTTP 404）。 */
    NOT_FOUND,

    /** 方案当前状态不可导出（HTTP 400），仅 review/confirmed 可导出（C-014 AC-1/AC-3）。 */
    PLAN_NOT_EXPORTABLE
}
