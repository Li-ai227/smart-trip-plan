package com.smart.trip.agent.route.mcp;

/**
 * HTTP 执行端口（C-007 增量 B / AC-8）：隔离地图适配器的网络依赖。
 *
 * <p>真实实现经 {@link MapHttpClient} 执行 HTTP GET；测试以 lambda 桩返回预设 JSON，
 * 保证适配逻辑离线确定性覆盖（编码规范 §8.2）。</p>
 */
@FunctionalInterface
public interface MapHttpExecutor {

    /**
     * 发送 HTTP GET 并返回原始响应体。
     *
     * @param url 完整请求 URL（含查询参数），非空
     * @return 原始响应体文本
     * @throws Exception 网络故障/超时/非 2xx 响应
     */
    String execute(String url) throws Exception;
}
