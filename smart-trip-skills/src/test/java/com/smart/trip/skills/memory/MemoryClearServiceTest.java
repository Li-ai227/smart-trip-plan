package com.smart.trip.skills.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link MemoryClearService} 单测（C-015 T-8 / AC-10 数据清除）。
 */
class MemoryClearServiceTest {

    private static final String BASE_URL = "http://127.0.0.1:8002";

    private StubHttpClient httpClient;
    private MemoryClearService clearService;

    @BeforeEach
    void setUp() {
        httpClient = new StubHttpClient();
        clearService = new MemoryClearService(httpClient, BASE_URL);
    }

    @Nested
    @DisplayName("正常清除（Happy Path）")
    class HappyPath {

        @Test
        @DisplayName("ReMe 返回 200 → 清除成功")
        void should_returnTrue_when_serverReturns200() {
            httpClient.statusCode = 200;

            boolean result = clearService.clearWorkspace("user-001");

            assertTrue(result);
        }

        @Test
        @DisplayName("ReMe 返回 204 → 清除成功")
        void should_returnTrue_when_serverReturns204() {
            httpClient.statusCode = 204;

            boolean result = clearService.clearWorkspace("user-001");

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("服务端异常")
    class ServerError {

        @Test
        @DisplayName("ReMe 返回 500 → 降级返回 false，不抛异常")
        void should_returnFalse_when_serverReturns500() {
            httpClient.statusCode = 500;

            boolean result = clearService.clearWorkspace("user-001");

            assertFalse(result);
        }

        @Test
        @DisplayName("ReMe 返回 404 → 降级返回 false")
        void should_returnFalse_when_serverReturns404() {
            httpClient.statusCode = 404;

            boolean result = clearService.clearWorkspace("user-001");

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("网络故障")
    class NetworkFailure {

        @Test
        @DisplayName("HttpClient 抛 IOException → 降级返回 false，不抛异常")
        void should_returnFalse_when_httpClientThrowsIOException() {
            httpClient.throwOnSend = new IOException("Connection refused");

            boolean result = clearService.clearWorkspace("user-001");

            assertFalse(result);
        }

        @Test
        @DisplayName("HttpClient 抛 InterruptedException → 降级返回 false")
        void should_returnFalse_when_httpClientThrowsInterruptedException() {
            httpClient.throwOnSend = new InterruptedException("Interrupted");

            boolean result = clearService.clearWorkspace("user-001");

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("参数校验")
    class ParameterValidation {

        @Test
        @DisplayName("workspaceId 为 null → 直接返回 false，不发 HTTP 请求")
        void should_returnFalse_when_workspaceIdIsNull() {
            boolean result = clearService.clearWorkspace(null);

            assertFalse(result);
            assertTrue(httpClient.sentRequests.isEmpty());
        }

        @Test
        @DisplayName("workspaceId 为空白字符串 → 直接返回 false，不发 HTTP 请求")
        void should_returnFalse_when_workspaceIdIsBlank() {
            boolean result = clearService.clearWorkspace("   ");

            assertFalse(result);
            assertTrue(httpClient.sentRequests.isEmpty());
        }
    }

    @Nested
    @DisplayName("请求构造验证")
    class RequestConstruction {

        @Test
        @DisplayName("请求 URL 包含 workspaceId 路径段 + DELETE 方法")
        void should_includeWorkspaceIdInUrl() {
            httpClient.statusCode = 200;

            clearService.clearWorkspace("user-abc-123");

            assertEquals(1, httpClient.sentRequests.size());
            HttpRequest captured = httpClient.sentRequests.get(0);
            assertEquals(BASE_URL + "/workspace/user-abc-123",
                    captured.uri().toString());
            assertEquals("DELETE", captured.method());
        }

        @Test
        @DisplayName("请求设置了超时时间")
        void should_haveTimeoutSet_when_requestIsBuilt() {
            httpClient.statusCode = 200;

            clearService.clearWorkspace("user-001");

            assertEquals(1, httpClient.sentRequests.size());
            assertTrue(httpClient.sentRequests.get(0).timeout().isPresent());
        }
    }

    /**
     * 可控 HttpClient 替身：记录请求、可配置响应状态码和异常。
     */
    static class StubHttpClient extends HttpClient {

        int statusCode = 200;
        Exception throwOnSend;
        final List<HttpRequest> sentRequests = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request,
                                         HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            sentRequests.add(request);
            if (throwOnSend instanceof IOException io) {
                throw io;
            }
            if (throwOnSend instanceof InterruptedException ie) {
                throw ie;
            }
            if (throwOnSend != null) {
                throw new RuntimeException(throwOnSend);
            }
            return (HttpResponse<T>) new StubResponse(statusCode);
        }

        @Override
        public java.util.Optional<java.net.CookieHandler> cookieHandler() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<java.time.Duration> connectTimeout() {
            return java.util.Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public java.util.Optional<java.net.ProxySelector> proxy() {
            return java.util.Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() {
            return new javax.net.ssl.SSLParameters();
        }

        @Override
        public java.util.Optional<java.net.Authenticator> authenticator() {
            return java.util.Optional.empty();
        }

        @Override
        public Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public java.util.Optional<java.util.concurrent.Executor> executor() {
            return java.util.Optional.empty();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * 可控 HttpResponse 替身。
     */
    static class StubResponse implements HttpResponse<Void> {

        private final int code;

        StubResponse(int code) {
            this.code = code;
        }

        @Override
        public int statusCode() {
            return code;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public java.util.Optional<HttpResponse<Void>> previousResponse() {
            return java.util.Optional.empty();
        }

        @Override
        public java.net.http.HttpHeaders headers() {
            return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        }

        @Override
        public Void body() {
            return null;
        }

        @Override
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
            return java.util.Optional.empty();
        }

        @Override
        public java.net.URI uri() {
            return null;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
