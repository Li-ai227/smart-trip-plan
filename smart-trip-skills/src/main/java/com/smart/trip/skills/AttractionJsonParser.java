package com.smart.trip.skills;

import com.fasterxml.jackson.core.type.TypeReference;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.util.JsonUtils;
import java.util.List;
import java.util.Map;

/**
 * 共享景点 JSON 解析工具，处理两种上游格式：
 * 裸数组 [{...}] 和 candidates 包装对象 {"candidates":[...]}
 */
public final class AttractionJsonParser {

    private static final TypeReference<List<Attraction>> ATTRACTIONS_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private AttractionJsonParser() { }

    /**
     * 解析景点 JSON，支持两种格式：
     * <ul>
     *   <li>裸数组：{@code [{...}, {...}]}</li>
     *   <li>candidates 包装：{@code {"candidates":[{...}], "fallback":false}}</li>
     * </ul>
     * 解析失败时返回空列表，不抛异常。
     *
     * @param json 景点 JSON 字符串
     * @return 解析后的景点列表，或空列表
     */
    public static List<Attraction> parse(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("{")) {
            try {
                Map<String, Object> wrapper = JsonUtils.fromJson(trimmed, MAP_TYPE);
                Object candidates = wrapper.get("candidates");
                if (candidates != null) {
                    String normalized = normalizeLocationStrings(JsonUtils.toJson(candidates));
                    return JsonUtils.fromJson(normalized, ATTRACTIONS_TYPE);
                }
            } catch (Exception ignored) { }
        }
        try {
            String normalized = normalizeLocationStrings(trimmed);
            return JsonUtils.fromJson(normalized, ATTRACTIONS_TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 预处理 JSON：将字符串形式的 {@code "location"} 字段包装为 {@code {"address": "..."}}
     * 格式，使 Jackson 能反序列化为 {@link com.smart.trip.common.model.GeoLocation}。
     *
     * <p>背景：native 路下 Supervisor LLM 拼给 route-planner 的候选景点 JSON 中，
     * {@code location} 通常被简化为纯地址字符串（如 {@code "北京市密云区"}），
     * 而 {@link com.smart.trip.common.model.Attraction} 的 {@code location} 字段类型为
     * {@link com.smart.trip.common.model.GeoLocation}（含 longitude/latitude/address/city），
     * Jackson 无法将字符串反序列化为对象，导致 {@code AttractionJsonParser} 返回空列表。
     * 此预处理确保 LLM 生成的劣化 JSON 也可被解析。</p>
     *
     * <p>只替换值为纯字符串的 location（非对象、非 null、非数字），
     * 已为正确 GeoLocation 对象格式的 location 不受影响。</p>
     */
    static String normalizeLocationStrings(String jsonStr) {
        if (jsonStr == null) return null;
        // ① "location": "string" → {"address": "string"}
        String result = jsonStr.replaceAll(
                "\"location\"\\s*:\\s*\"([^\"]*)\"",
                "\"location\":{\"address\":\"$1\"}");
        // ② LLM 可能传 {lat, lng} → 转为 {latitude, longitude}
        //    用负向前瞻避免误匹配已存在的 "latitude"
        result = result.replaceAll("\"lat\"(?!itude)\\s*:", "\"latitude\":");
        result = result.replaceAll("\"lng\"\\s*:", "\"longitude\":");
        return result;
    }
}
