package com.smart.trip.agent.route.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 真实 HTTP GET 客户端（C-007 增量 B）：包装 {@link HttpClient}，施加超时 30s。
 *
 * <p>线程安全，可作为共享单例。该类涉及真实网络，单测不可离线覆盖，
 * 所有业务逻辑通过 {@link MapHttpExecutor} 桩覆盖，JaCoCo 排除。</p>
 */
public final class MapHttpClient implements MapHttpExecutor {

    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    private final HttpClient client;
    private final Duration timeout;

    /**
     * 默认超时 30s。
     */
    public MapHttpClient() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * @param timeoutMillis 请求超时（毫秒）
     */
    public MapHttpClient(long timeoutMillis) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .build();
        this.timeout = Duration.ofMillis(timeoutMillis);
    }

    @Override
    public String execute(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MapApiException(response.statusCode(),
                    "HTTP " + response.statusCode());
        }
        return response.body();
    }
}
