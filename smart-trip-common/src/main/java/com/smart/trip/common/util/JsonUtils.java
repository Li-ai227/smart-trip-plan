package com.smart.trip.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 统一 JSON 序列化工具（数据模型.md §4：值序列化用 Jackson；C-004 AC-5/AC-6）。
 *
 * <p>统一约定（接口协议.md §5 / §2.3）：
 * 时间字段 ISO-8601（非时间戳数组）、未知字段向后兼容忽略（子 Agent 忽略新增字段而非失败）、
 * 浮点数反序列化优先 {@code BigDecimal} 以保金额精度与标度。{@link ObjectMapper} 配置后线程安全，
 * 全局复用单例。</p>
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = buildMapper();

    private JsonUtils() {
    }

    private static ObjectMapper buildMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // 时间序列化为 ISO-8601 字符串，而非 epoch 时间戳（接口协议.md §5）
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 向后兼容：忽略未知字段，子 Agent 不因新增字段失败（接口协议.md §2.3）
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // 浮点优先解析为 BigDecimal，保金额精度与标度（如 1620.00）
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     * @throws IllegalStateException 当对象无法序列化时
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败: " + value.getClass().getName(), e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T>  目标类型参数
     * @return 反序列化对象
     * @throws IllegalArgumentException 当 JSON 非法或与目标类型不匹配时
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 反序列化失败, type=" + type.getName(), e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为带泛型的复杂类型（如 {@code List<TripDay>}）。
     *
     * @param json    JSON 字符串
     * @param typeRef 目标类型引用
     * @param <T>     目标类型参数
     * @return 反序列化对象
     * @throws IllegalArgumentException 当 JSON 非法或与目标类型不匹配时
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 反序列化失败, type=" + typeRef.getType(), e);
        }
    }
}
