package com.smart.trip.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.GeoLocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link JsonUtils} 序列化契约测试（C-004 AC-5/AC-6）。
 *
 * <p>校验 ISO-8601 时间（{@code 接口协议.md} §5）、未知字段向后兼容忽略（{@code 接口协议.md} §2.3
 * 子 Agent 忽略未知字段）、非法 JSON fail-fast，以及序列化往返字段一致（round-trip）。</p>
 */
class JsonUtilsTest {

    @Nested
    @DisplayName("时间 ISO-8601 与 round-trip")
    class TimeAndRoundTrip {

        @Test
        void localDateSerializedAsIso8601() {
            TripPlanRequest r = new TripPlanRequest("云南", LocalDate.of(2026, 7, 1), 5,
                    new BigDecimal("8000"), 2, TravelStyle.COUPLE, List.of("古镇", "美食"));
            String json = JsonUtils.toJson(r);
            assertTrue(json.contains("2026-07-01"), "LocalDate 应序列化为 ISO-8601 而非时间戳数组");
            assertEquals(r, JsonUtils.fromJson(json, TripPlanRequest.class));
        }
    }

    @Nested
    @DisplayName("向后兼容与防御")
    class Robustness {

        @Test
        void ignoresUnknownProperties() {
            String json = "{\"longitude\":120.1,\"latitude\":30.2,\"address\":\"西湖\","
                    + "\"city\":\"杭州\",\"futureField\":\"ignored\"}";
            GeoLocation g = JsonUtils.fromJson(json, GeoLocation.class);
            assertEquals("杭州", g.city());
        }

        @Test
        void invalidJsonThrowsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtils.fromJson("{not-json", GeoLocation.class));
        }

        @Test
        @DisplayName("不可序列化对象 fail-fast 为 IllegalStateException")
        void unserializableValueThrowsIllegalState() {
            // 空 Bean 无可写属性，Jackson 默认 FAIL_ON_EMPTY_BEANS → 应被包装为 IllegalStateException 而非裸泄漏
            assertThrows(IllegalStateException.class, () -> JsonUtils.toJson(new Object()));
        }
    }

    @Nested
    @DisplayName("泛型集合 round-trip（TypeReference 重载）")
    class GenericCollection {

        /**
         * 覆盖 {@link JsonUtils#fromJson(String, TypeReference)} 重载：数据模型.md §4 中
         * {@code List<TripDay>} 等集合按 Hash 字段序列化时依赖该路径，泛型擦除下须保元素类型与字段。
         */
        @Test
        void listRoundTripPreservesElements() {
            List<GeoLocation> src = List.of(
                    new GeoLocation(120.13, 30.25, "西湖", "杭州"),
                    new GeoLocation(116.40, 39.90, "故宫", "北京"));

            List<GeoLocation> back =
                    JsonUtils.fromJson(JsonUtils.toJson(src), new TypeReference<List<GeoLocation>>() {});

            assertEquals(src, back);
            assertEquals("北京", back.get(1).city());
        }

        @Test
        @DisplayName("非法 JSON 在 TypeReference 路径同样 fail-fast")
        void invalidJsonThrowsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> JsonUtils.fromJson("{not-json", new TypeReference<List<GeoLocation>>() {}));
        }
    }
}
