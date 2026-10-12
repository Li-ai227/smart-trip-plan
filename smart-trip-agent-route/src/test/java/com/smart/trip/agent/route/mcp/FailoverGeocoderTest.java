package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.MapGeocoder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link FailoverGeocoder} 单测（C-007 增量 B / AC-9）。
 */
class FailoverGeocoderTest {

    private static final GeoLocation LOC_BAIDU =
            new GeoLocation(104.0657, 30.6595, "宽窄巷子", "成都");
    private static final GeoLocation LOC_AMAP =
            new GeoLocation(104.0658, 30.6596, "宽窄巷子", "成都");

    private final AtomicLong clock = new AtomicLong(0L);
    private SimpleMeterRegistry registry;
    private AgentMetrics metrics;
    private VendorHealth baiduHealth;
    private VendorHealth amapHealth;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AgentMetrics(registry);
        baiduHealth = new VendorHealth(VendorId.BAIDU, 3, 30_000L, clock::get);
        amapHealth = new VendorHealth(VendorId.AMAP, 3, 30_000L, clock::get);
    }

    @Test
    void primary_success_returns_primary_result() throws Exception {
        MapGeocoder primary = (addr, city) -> LOC_BAIDU;
        MapGeocoder secondary = (addr, city) -> LOC_AMAP;

        FailoverGeocoder fo = new FailoverGeocoder(
                primary, secondary, baiduHealth, amapHealth, metrics);

        GeoLocation result = fo.geocode("宽窄巷子", "成都");

        assertEquals(LOC_BAIDU, result);
        assertEquals(0, switchCount());
    }

    @Test
    void primary_fail_secondary_success_returns_secondary_and_records_metric() throws Exception {
        MapGeocoder primary = (addr, city) -> { throw new MapApiException(500, "百度异常"); };
        MapGeocoder secondary = (addr, city) -> LOC_AMAP;

        FailoverGeocoder fo = new FailoverGeocoder(
                primary, secondary, baiduHealth, amapHealth, metrics);

        GeoLocation result = fo.geocode("宽窄巷子", "成都");

        assertEquals(LOC_AMAP, result);
        assertEquals(1, switchCount());
    }

    @Test
    void both_fail_throws_exception() {
        MapGeocoder primary = (addr, city) -> { throw new MapApiException(500, "百度异常"); };
        MapGeocoder secondary = (addr, city) -> { throw new MapApiException(500, "高德异常"); };

        FailoverGeocoder fo = new FailoverGeocoder(
                primary, secondary, baiduHealth, amapHealth, metrics);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> fo.geocode("宽窄巷子", "成都"));
        assertEquals(500, ex.statusCode());
    }

    @Test
    void circuit_open_skips_primary_directly_uses_secondary() throws Exception {
        for (int i = 0; i < 3; i++) {
            baiduHealth.recordFailure();
        }
        MapGeocoder primary = (addr, city) -> { throw new AssertionError("should not be called"); };
        MapGeocoder secondary = (addr, city) -> LOC_AMAP;

        FailoverGeocoder fo = new FailoverGeocoder(
                primary, secondary, baiduHealth, amapHealth, metrics);

        GeoLocation result = fo.geocode("宽窄巷子", "成都");

        assertEquals(LOC_AMAP, result);
    }

    @Test
    void half_open_probe_success_restores_primary() throws Exception {
        for (int i = 0; i < 3; i++) {
            baiduHealth.recordFailure();
        }
        clock.set(30_000L);

        MapGeocoder primary = (addr, city) -> LOC_BAIDU;
        MapGeocoder secondary = (addr, city) -> LOC_AMAP;

        FailoverGeocoder fo = new FailoverGeocoder(
                primary, secondary, baiduHealth, amapHealth, metrics);

        GeoLocation result = fo.geocode("宽窄巷子", "成都");

        assertEquals(LOC_BAIDU, result);
        assertEquals(VendorHealth.State.CLOSED, baiduHealth.state());
    }

    @Test
    void null_metrics_does_not_throw() throws Exception {
        MapGeocoder primary = (addr, city) -> { throw new MapApiException(500, "fail"); };
        MapGeocoder secondary = (addr, city) -> LOC_AMAP;

        FailoverGeocoder fo = new FailoverGeocoder(
                primary, secondary, baiduHealth, amapHealth, null);

        GeoLocation result = fo.geocode("宽窄巷子", "成都");

        assertEquals(LOC_AMAP, result);
    }

    private double switchCount() {
        return registry.counter(AgentMetrics.METRIC_VENDOR_SWITCH,
                "from", "baidu", "to", "amap").count();
    }

    /**
     * AssertionError placeholder for lambda throwing.
     */
    private static class AssertionError extends RuntimeException {
        AssertionError(String msg) {
            super(msg);
        }
    }
}
