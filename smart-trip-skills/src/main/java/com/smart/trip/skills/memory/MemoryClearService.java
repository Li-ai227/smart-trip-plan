package com.smart.trip.skills.memory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 记忆清除服务（C-015 AC-10）。
 *
 * <p>通过 HTTP DELETE 调用 ReMe Server 清除指定 workspace 的全部记忆，
 * 支撑按 userId 维度合规删除用户数据。
 * 清除失败仅记录告警，不抛异常。</p>
 */
public final class MemoryClearService {

    private static final System.Logger LOG =
            System.getLogger(MemoryClearService.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int STATUS_NO_CONTENT = 204;
    private static final int STATUS_OK = 200;

    private final HttpClient httpClient;
    private final String remeBaseUrl;

    /**
     * @param remeBaseUrl ReMe Server 基础 URL（如 {@code http://127.0.0.1:8002}）
     */
    public MemoryClearService(String remeBaseUrl) {
        this(HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build(), remeBaseUrl);
    }

    /**
     * @param httpClient  可注入 HTTP 客户端（测试用）
     * @param remeBaseUrl ReMe Server 基础 URL
     */
    MemoryClearService(HttpClient httpClient, String remeBaseUrl) {
        this.httpClient = httpClient;
        this.remeBaseUrl = remeBaseUrl;
    }

    /**
     * 清除指定 workspace 的全部记忆。
     *
     * <p>调用 ReMe Server {@code DELETE /workspace/{workspaceId}} 端点。
     * 调用失败仅记录告警，不抛异常。</p>
     *
     * @param workspaceId workspace 标识（如 userId 字符串）
     * @return true 清除成功；false 清除失败或 ReMe 不可用
     */
    public boolean clearWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return false;
        }
        try {
            String url = remeBaseUrl + "/workspace/" + workspaceId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .DELETE()
                    .build();
            HttpResponse<Void> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code == STATUS_NO_CONTENT || code == STATUS_OK) {
                LOG.log(System.Logger.Level.INFO,
                        "记忆清除成功: workspace={0}", workspaceId);
                return true;
            }
            LOG.log(System.Logger.Level.WARNING,
                    "记忆清除返回异常状态: workspace={0}, status={1}",
                    workspaceId, code);
            return false;
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "记忆清除失败（降级忽略）: workspace={0}, error={1}",
                    workspaceId, ex.getMessage());
            return false;
        }
    }
}
