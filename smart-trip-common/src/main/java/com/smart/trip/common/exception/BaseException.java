package com.smart.trip.common.exception;

/**
 * 项目统一业务异常基类（编码规范 §3：业务异常继承项目 {@code BaseException} 体系，禁止滥用裸 RuntimeException）。
 *
 * <p>每个业务异常都携带语义化 {@link ErrorCode}，便于日志检索与对外错误响应映射（接口协议.md §5）。
 * 异常信息应包含上下文（哪个 Agent / task / 关键参数），但禁止泄露密钥或用户隐私（编码规范 §7）。</p>
 */
public class BaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 语义化错误码，决定对外 {@code code} 与 HTTP 映射。 */
    private final transient ErrorCode errorCode;

    /**
     * 构造业务异常。
     *
     * @param errorCode 语义化错误码，不可为 null
     * @param message   可读错误信息（禁止包含密钥/隐私）
     */
    public BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造业务异常并保留根因。
     *
     * @param errorCode 语义化错误码，不可为 null
     * @param message   可读错误信息（禁止包含密钥/隐私）
     * @param cause     根因异常，便于链路排查
     */
    public BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 返回语义化错误码。
     *
     * @return 错误码枚举
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 返回对外/日志使用的错误码字符串（等于 {@link ErrorCode} 常量名）。
     *
     * @return 错误码字符串，如 {@code INVALID_REQUEST}
     */
    public String getCode() {
        return errorCode.name();
    }
}
