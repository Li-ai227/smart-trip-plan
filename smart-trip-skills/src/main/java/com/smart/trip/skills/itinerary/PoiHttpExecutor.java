package com.smart.trip.skills.itinerary;

/**
 * POI 周边搜索 HTTP 执行端口（C-008 T-1）。
 *
 * <p>与 {@code MapHttpExecutor}（agent-route）同构，声明在 skills 层避免跨模块类型依赖。
 * 真实实现由外部注入（{@code MapHttpClient} 或等效实现）。</p>
 */
@FunctionalInterface
public interface PoiHttpExecutor {

    /**
     * 执行 HTTP GET 并返回响应体。
     *
     * @param url 完整 URL
     * @return 响应体文本
     * @throws Exception 网络/超时/非 2xx 等异常
     */
    String execute(String url) throws Exception;
}
