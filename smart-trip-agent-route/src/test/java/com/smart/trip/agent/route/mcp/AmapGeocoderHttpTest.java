package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AmapGeocoderHttp} 确定性单测（C-007 增量 B）。
 */
class AmapGeocoderHttpTest {

    private static final String VALID_RESPONSE = """
            {"status":"1","geocodes":[{"location":"104.065735,30.659462","level":"兴趣点"}],"count":"1"}""";

    @Test
    void valid_response_parsed() throws Exception {
        AmapGeocoderHttp geocoder = new AmapGeocoderHttp(url -> VALID_RESPONSE, "test-ak", null);

        GeoLocation result = geocoder.geocode("宽窄巷子", "成都");

        assertEquals(104.065735, result.longitude(), 0.0001);
        assertEquals(30.659462, result.latitude(), 0.0001);
        assertEquals("宽窄巷子", result.address());
    }

    @Test
    void status_not_one_throws() {
        AmapGeocoderHttp geocoder = new AmapGeocoderHttp(
                url -> "{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\",\"infocode\":\"10001\"}", "test-ak", null);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> geocoder.geocode("invalid", "成都"));
        assertEquals(10001, ex.statusCode());
    }

    @Test
    void empty_geocodes_throws() {
        AmapGeocoderHttp geocoder = new AmapGeocoderHttp(
                url -> "{\"status\":\"1\",\"geocodes\":[]}", "test-ak", null);

        assertThrows(MapApiException.class, () -> geocoder.geocode("unknown", "成都"));
    }

    @Test
    void malformed_location_throws() {
        AmapGeocoderHttp geocoder = new AmapGeocoderHttp(
                url -> "{\"status\":\"1\",\"geocodes\":[{\"location\":\"bad\"}]}", "test-ak", null);

        assertThrows(MapApiException.class, () -> geocoder.geocode("test", "成都"));
    }

    @Test
    void cache_hit_skips_http() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicLong clock = new AtomicLong(0);
        GeocodingCache cache = new GeocodingCache(60_000, clock::get);

        AmapGeocoderHttp geocoder = new AmapGeocoderHttp(url -> {
            callCount.incrementAndGet();
            return VALID_RESPONSE;
        }, "test-ak", cache);

        geocoder.geocode("宽窄巷子", "成都");
        geocoder.geocode("宽窄巷子", "成都");

        assertEquals(1, callCount.get());
    }

    @Test
    void url_uses_key_param() throws Exception {
        String[] capturedUrl = {null};
        AmapGeocoderHttp geocoder = new AmapGeocoderHttp(url -> {
            capturedUrl[0] = url;
            return VALID_RESPONSE;
        }, "my-amap-ak", null);

        geocoder.geocode("宽窄巷子", "成都");

        assertEquals(true, capturedUrl[0].contains("key=my-amap-ak"));
    }

    @Test
    void blank_ak_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new AmapGeocoderHttp(url -> "", "", null));
    }
}
