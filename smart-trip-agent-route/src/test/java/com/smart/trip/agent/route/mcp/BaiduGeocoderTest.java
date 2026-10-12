package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BaiduGeocoder} 确定性单测（C-007 增量 B）。
 */
class BaiduGeocoderTest {

    private static final String VALID_RESPONSE = """
            {"status":0,"result":{"location":{"lng":104.065735,"lat":30.659462},"precise":1,"confidence":80}}""";

    @Test
    void valid_response_parsed_to_geolocation() throws Exception {
        BaiduGeocoder geocoder = new BaiduGeocoder(url -> VALID_RESPONSE, "test-ak", null);

        GeoLocation result = geocoder.geocode("宽窄巷子", "成都");

        assertEquals(104.065735, result.longitude(), 0.0001);
        assertEquals(30.659462, result.latitude(), 0.0001);
        assertEquals("宽窄巷子", result.address());
        assertEquals("成都", result.city());
    }

    @Test
    void status_non_zero_throws() {
        BaiduGeocoder geocoder = new BaiduGeocoder(
                url -> "{\"status\":1,\"message\":\"参数错误\"}", "test-ak", null);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> geocoder.geocode("invalid", "成都"));
        assertEquals(1, ex.statusCode());
    }

    @Test
    void malformed_json_throws() {
        BaiduGeocoder geocoder = new BaiduGeocoder(url -> "not json", "test-ak", null);

        assertThrows(MapApiException.class,
                () -> geocoder.geocode("宽窄巷子", "成都"));
    }

    @Test
    void blank_ak_throws_illegal_argument() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaiduGeocoder(url -> "", "", null));
    }

    @Test
    void null_ak_throws_illegal_argument() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaiduGeocoder(url -> "", null, null));
    }

    @Test
    void connection_failure_propagated() {
        BaiduGeocoder geocoder = new BaiduGeocoder(
                url -> { throw new java.io.IOException("connection refused"); },
                "test-ak", null);

        assertThrows(java.io.IOException.class,
                () -> geocoder.geocode("宽窄巷子", "成都"));
    }

    @Test
    void cache_hit_skips_http() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicLong clock = new AtomicLong(0);
        GeocodingCache cache = new GeocodingCache(60_000, clock::get);

        BaiduGeocoder geocoder = new BaiduGeocoder(url -> {
            callCount.incrementAndGet();
            return VALID_RESPONSE;
        }, "test-ak", cache);

        geocoder.geocode("宽窄巷子", "成都");
        geocoder.geocode("宽窄巷子", "成都");

        assertEquals(1, callCount.get());
    }

    @Test
    void url_contains_ak_and_address() throws Exception {
        String[] capturedUrl = {null};
        BaiduGeocoder geocoder = new BaiduGeocoder(url -> {
            capturedUrl[0] = url;
            return VALID_RESPONSE;
        }, "my-secret-ak", null);

        geocoder.geocode("宽窄巷子", "成都");

        assertTrue(capturedUrl[0].contains("ak=my-secret-ak"));
        assertTrue(capturedUrl[0].contains("address="));
        assertTrue(capturedUrl[0].contains("city="));
    }

    @Test
    void null_city_does_not_fail() throws Exception {
        BaiduGeocoder geocoder = new BaiduGeocoder(url -> VALID_RESPONSE, "test-ak", null);

        GeoLocation result = geocoder.geocode("宽窄巷子", null);

        assertEquals(104.065735, result.longitude(), 0.0001);
    }
}
