package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.MapTraffic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link FailoverTraffic} 单测（C-007 增量 B / AC-9）。
 */
class FailoverTrafficTest {

    private static final GeoLocation FROM =
            new GeoLocation(104.0657, 30.6595, "宽窄巷子", "成都");
    private static final GeoLocation TO =
            new GeoLocation(104.1432, 30.7318, "大熊猫基地", "成都");

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
    void primary_success_returns_primary_factor() throws Exception {
        MapTraffic primary = (f, t) -> 1.3;
        MapTraffic secondary = (f, t) -> 1.5;

        FailoverTraffic fo = new FailoverTraffic(
                primary, secondary, baiduHealth, amapHealth, metrics);

        assertEquals(1.3, fo.trafficFactor(FROM, TO), 0.01);
        assertEquals(0, switchCount());
    }

    @Test
    void primary_fail_secondary_success() throws Exception {
        MapTraffic primary = (f, t) -> { throw new MapApiException(500, "百度异常"); };
        MapTraffic secondary = (f, t) -> 1.5;

        FailoverTraffic fo = new FailoverTraffic(
                primary, secondary, baiduHealth, amapHealth, metrics);

        assertEquals(1.5, fo.trafficFactor(FROM, TO), 0.01);
        assertEquals(1, switchCount());
    }

    @Test
    void both_fail_throws() {
        MapTraffic primary = (f, t) -> { throw new MapApiException(500, "百度"); };
        MapTraffic secondary = (f, t) -> { throw new MapApiException(500, "高德"); };

        FailoverTraffic fo = new FailoverTraffic(
                primary, secondary, baiduHealth, amapHealth, metrics);

        assertThrows(MapApiException.class, () -> fo.trafficFactor(FROM, TO));
    }

    @Test
    void circuit_open_skips_primary() throws Exception {
        for (int i = 0; i < 3; i++) {
            baiduHealth.recordFailure();
        }
        MapTraffic primary = (f, t) -> { throw new RuntimeException("should not be called"); };
        MapTraffic secondary = (f, t) -> 1.0;

        FailoverTraffic fo = new FailoverTraffic(
                primary, secondary, baiduHealth, amapHealth, metrics);

        assertEquals(1.0, fo.trafficFactor(FROM, TO), 0.01);
    }

    @Test
    void half_open_probe_success_restores() throws Exception {
        for (int i = 0; i < 3; i++) {
            baiduHealth.recordFailure();
        }
        clock.set(30_000L);

        MapTraffic primary = (f, t) -> 1.2;
        MapTraffic secondary = (f, t) -> 1.5;

        FailoverTraffic fo = new FailoverTraffic(
                primary, secondary, baiduHealth, amapHealth, metrics);

        assertEquals(1.2, fo.trafficFactor(FROM, TO), 0.01);
        assertEquals(VendorHealth.State.CLOSED, baiduHealth.state());
    }

    @Test
    void null_metrics_does_not_throw() throws Exception {
        MapTraffic primary = (f, t) -> { throw new MapApiException(500, "fail"); };
        MapTraffic secondary = (f, t) -> 1.5;

        FailoverTraffic fo = new FailoverTraffic(
                primary, secondary, baiduHealth, amapHealth, null);

        assertEquals(1.5, fo.trafficFactor(FROM, TO), 0.01);
    }

    private double switchCount() {
        return registry.counter(AgentMetrics.METRIC_VENDOR_SWITCH,
                "from", "baidu", "to", "amap").count();
    }
}
