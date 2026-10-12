package com.smart.trip.skills.xhs;

/**
 * HTTP 调用端口（C-006 增量 B / AC-8）：隔离 {@link XHSOpenApiNoteSource} 的网络依赖。
 *
 * <p>真实实现执行 HTTP 请求到小红书官方授权 API；测试以桩实现返回预设响应，
 * 保证适配逻辑离线确定性覆盖（编码规范 §8.2）。</p>
 */
@FunctionalInterface
public interface HttpClient {

    /**
     * 发送请求并返回原始响应体。
     *
     * @param requestUrl 请求 URL（含查询参数），非空
     * @return 原始响应体文本
     * @throws Exception 当网络故障/超时/授权失败
     */
    String execute(String requestUrl) throws Exception;
}
