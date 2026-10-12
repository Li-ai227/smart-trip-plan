package com.smart.trip.common.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集合 null 安全与不可变拷贝工具（C-004 决策 D-6 / 编码规范 §5：返回集合用空集合而非 null）。
 *
 * <p>各领域模型 record 与 A2A {@code Msg} 信封的紧凑构造器复用本工具，统一把可空集合归一为不可变拷贝，
 * 避免在多处散写相同的 null 判断（编码规范 §10 / PMD CPD）。</p>
 */
public final class CollectionUtils {

    private CollectionUtils() {
    }

    /**
     * 返回源列表的不可变拷贝；{@code null} 归一为空列表。
     *
     * @param source 源列表，可为 null
     * @param <T>    元素类型
     * @return 不可变拷贝；源为 null 时返回不可变空列表
     */
    public static <T> List<T> safeCopy(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    /**
     * 返回源 Map 的不可变拷贝；{@code null} 归一为空 Map。
     *
     * <p>区别于 {@link Map#copyOf}：保留插入顺序（A2A payload 序列化确定性）且允许 {@code null} 值
     * （如 {@code TASK_RESULT} 的 {@code result} 可空，C-005 决策 D-6）。</p>
     *
     * @param source 源 Map，可为 null
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 不可变拷贝；源为 null 或空时返回不可变空 Map
     */
    public static <K, V> Map<K, V> safeCopy(Map<K, V> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
