package com.smart.trip.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CollectionUtils} 集合 null 安全与不可变拷贝测试（C-004 决策 D-6 / 编码规范 §5）。
 */
class CollectionUtilsTest {

    @Nested
    @DisplayName("List 安全拷贝")
    class ListCopy {

        @Test
        @DisplayName("null 归一为空列表")
        void nullToEmpty() {
            assertTrue(CollectionUtils.safeCopy((List<String>) null).isEmpty());
        }

        @Test
        @DisplayName("拷贝隔离源变更，且结果不可变")
        void copiesAndImmutable() {
            List<String> src = new ArrayList<>(List.of("a"));
            List<String> copy = CollectionUtils.safeCopy(src);
            src.add("b");
            assertEquals(1, copy.size());
            assertThrows(UnsupportedOperationException.class, () -> copy.add("c"));
        }
    }

    @Nested
    @DisplayName("Map 安全拷贝（C-005 A2A payload 复用 / D-6）")
    class MapCopy {

        @Test
        @DisplayName("null 归一为空 Map")
        void nullToEmpty() {
            assertTrue(CollectionUtils.safeCopy((Map<String, Object>) null).isEmpty());
        }

        @Test
        @DisplayName("拷贝隔离源变更，且结果不可变")
        void copiesAndImmutable() {
            Map<String, Object> src = new HashMap<>();
            src.put("a", 1);
            Map<String, Object> copy = CollectionUtils.safeCopy(src);
            src.put("b", 2);
            assertEquals(1, copy.size());
            assertThrows(UnsupportedOperationException.class, () -> copy.put("c", 3));
        }

        @Test
        @DisplayName("允许 null 值（payload result 可空，区别于 Map.copyOf）")
        void allowsNullValues() {
            Map<String, Object> src = new HashMap<>();
            src.put("result", null);
            Map<String, Object> copy = CollectionUtils.safeCopy(src);
            assertTrue(copy.containsKey("result"));
        }
    }
}
