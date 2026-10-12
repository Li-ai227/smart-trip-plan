package com.smart.trip.skills.xhs;

import com.smart.trip.common.model.XHSNote;
import com.smart.trip.common.util.JsonUtils;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 小红书官方授权 API 真实适配（C-006 增量 B / AC-8）。
 *
 * <p>经 {@link HttpClient} 调用小红书官方开放平台 API 检索旅游笔记，返回原始笔记
 * （{@code mentions} 为空，由 {@link XHSNoteParser} 后续解析结构化）。实现可因
 * 超时/网络故障/授权失败抛异常，由 {@link com.smart.trip.skills.governance.GovernedExternalCaller}
 * 统一施加限频（{@code xhs ≤10/min}）/重试/降级。</p>
 *
 * <p><b>合规约束（非目标红线）</b>：仅走官方授权 API，<b>不含</b>模拟登录/登录态/Cookie
 * 等规避手段（评审 + 静态检查关注）。API Key 从构造参数传入（由上层从环境变量/配置中心
 * 读取，禁硬编码）。</p>
 */
public final class XHSOpenApiNoteSource implements XHSNoteSource {

    private static final String BASE_URL = "https://open.xiaohongshu.com/api/v1/notes/search";

    private final HttpClient httpClient;
    private final String apiKey;

    /**
     * @param httpClient HTTP 调用端口（真实实现执行网络请求），非 null
     * @param apiKey     小红书官方授权 API 密钥（从环境变量/配置中心读取），非空白
     * @throws IllegalArgumentException 当密钥为 null 或空白（快速失败）
     */
    public XHSOpenApiNoteSource(HttpClient httpClient, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("XHS API Key 不可为空白（禁硬编码，从环境变量读取）");
        }
        this.httpClient = httpClient;
        this.apiKey = apiKey;
    }

    @Override
    public List<XHSNote> search(XHSAnalysisQuery query) throws Exception {
        String url = buildUrl(query);
        String response = httpClient.execute(url);
        return parseResponse(response);
    }

    private String buildUrl(XHSAnalysisQuery query) {
        String dest = URLEncoder.encode(query.destination(), StandardCharsets.UTF_8);
        String prefs = URLEncoder.encode(String.join(",", query.preferences()), StandardCharsets.UTF_8);
        return BASE_URL + "?keyword=" + dest
                + "&tags=" + prefs
                + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static List<XHSNote> parseResponse(String response) {
        List<Map<String, Object>> items;
        try {
            items = JsonUtils.fromJson(response, List.class);
        } catch (Exception ex) {
            return List.of();
        }
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<XHSNote> notes = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String noteId = asString(item.get("noteId"));
            String title = asString(item.get("title"));
            String content = asString(item.get("content"));
            int likeCount = asInt(item.get("likeCount"));
            List<String> tags = asTags(item.get("tags"));
            notes.add(new XHSNote(noteId, title, content, tags, likeCount, List.of()));
        }
        return notes;
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asTags(Object value) {
        if (value instanceof List<?> list) {
            List<String> tags = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    tags.add(item.toString());
                }
            }
            return tags;
        }
        return List.of();
    }
}
