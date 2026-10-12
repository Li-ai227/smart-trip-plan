package com.smart.trip.agent.route.mcp;

/**
 * 地图 API 错误（C-007 增量 B）：非 2xx HTTP 响应或厂商业务错误码。
 */
public final class MapApiException extends Exception {

    private final int statusCode;

    /**
     * @param statusCode HTTP 状态码或厂商业务错误码
     * @param message    错误描述（禁止包含 AK 等敏感信息）
     */
    public MapApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * @param statusCode HTTP 状态码或厂商业务错误码
     * @param message    错误描述（禁止包含 AK 等敏感信息）
     * @param cause      原始异常（保留堆栈跟踪）
     */
    public MapApiException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * @return HTTP 状态码或厂商业务错误码
     */
    public int statusCode() {
        return statusCode;
    }
}
