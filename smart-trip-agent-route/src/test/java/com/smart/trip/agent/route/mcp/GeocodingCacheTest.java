package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GeocodingCache} 确定性单测（C-007 增量 B）。
 */
class GeocodingCacheTest {

    private static final GeoLocation CHENGDU = new GeoLocation(104.065735, 30.659462, "成都", "成都");
    private static final GeoLocation BEIJING = new GeoLocation(116.407395, 39.904211, "北京", "北京");

    @Test
    void miss_returns_empty() {
        AtomicLong clock = new AtomicLong(0);
        GeocodingCache cache = new GeocodingCache(60_000, clock::get);

        Optional<GeoLocation> result = cache.get("宽窄巷子", "成都");

        assertTrue(result.isEmpty());
    }

    @Test
    void put_then_get_returns_cached() {
        AtomicLong clock = new AtomicLong(1000);
        GeocodingCache cache = new GeocodingCache(60_000, clock::get);

        cache.put("宽窄巷子", "成都", CHENGDU);
        Optional<GeoLocation> result = cache.get("宽窄巷子", "成都");

        assertTrue(result.isPresent());
        assertEquals(CHENGDU, result.get());
    }

    @Test
    void ttl_expired_returns_empty() {
        AtomicLong clock = new AtomicLong(1000);
        GeocodingCache cache = new GeocodingCache(60_000, clock::get);

        cache.put("宽窄巷子", "成都", CHENGDU);

        clock.set(62_000);
        Optional<GeoLocation> result = cache.get("宽窄巷子", "成都");

        assertTrue(result.isEmpty());
    }

    @Test
    void different_keys_isolated() {
        AtomicLong clock = new AtomicLong(0);
        GeocodingCache cache = new GeocodingCache(60_000, clock::get);

        cache.put("宽窄巷子", "成都", CHENGDU);
        cache.put("故宫", "北京", BEIJING);

        assertEquals(CHENGDU, cache.get("宽窄巷子", "成都").orElse(null));
        assertEquals(BEIJING, cache.get("故宫", "北京").orElse(null));
        assertTrue(cache.get("宽窄巷子", "北京").isEmpty());
    }

    @Test
    void null_city_treated_as_empty_string() {
        AtomicLong clock = new AtomicLong(0);
        GeocodingCache cache = new GeocodingCache(60_000, clock::get);

        cache.put("景点A", null, CHENGDU);
        Optional<GeoLocation> result = cache.get("景点A", null);

        assertTrue(result.isPresent());
        assertEquals(CHENGDU, result.get());
    }
}
