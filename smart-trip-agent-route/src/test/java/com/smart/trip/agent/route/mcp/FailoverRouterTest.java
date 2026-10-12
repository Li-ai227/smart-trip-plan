package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import com.smart.trip.skills.observability.AgentMetrics;
import com.smart.trip.skills.route.MapRouter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link FailoverRouter} 单测（C-007 增量 B / AC-9）。
 */
class FailoverRouterTest {

    private static final GeoLocation FROM =
            new GeoLocation(104.0657, 30.6595, "宽窄巷子", "成都");
    private static final GeoLocation TO =
            new GeoLocation(104.1432, 30.7318, "大熊猫基地", "成都");
    private static final Route BAIDU_ROUTE =
            new Route(FROM, TO, 25.6, 45, "驾车", false);
    private static final Route AMAP_ROUTE =
            new Route(FROM, TO, 25.8, 46, "驾车", false);

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
    void primary_success_returns_primary() throws Exception {
        MapRouter primary = (f, t) -> BAIDU_ROUTE;
        MapRouter secondary = (f, t) -> AMAP_ROUTE;

        FailoverRouter fo = new FailoverRouter(
                primary, secondary, baiduHealth, amapHealth, metrics);

        Route result = fo.route(FROM, TO);

        assertEquals(BAIDU_ROUTE, result);
        assertEquals(0, switchCount());
    }

    @Test
    void primary_fail_secondary_success() throws Exception {
        MapRouter primary = (f, t) -> { throw new MapApiException(500, "百度异常"); };
        MapRouter secondary = (f, t) -> AMAP_ROUTE;

        FailoverRouter fo = new FailoverRouter(
                primary, secondary, baiduHealth, amapHealth, metrics);

        Route result = fo.route(FROM, TO);

        assertEquals(AMAP_ROUTE, result);
        assertEquals(1, switchCount());
    }

    @Test
    void both_fail_throws() {
        MapRouter primary = (f, t) -> { throw new MapApiException(500, "百度异常"); };
        MapRouter secondary = (f, t) -> { throw new MapApiException(500, "高德异常"); };

        FailoverRouter fo = new FailoverRouter(
                primary, secondary, baiduHealth, amapHealth, metrics);

        assertThrows(MapApiException.class, () -> fo.route(FROM, TO));
    }

    @Test
    void circuit_open_skips_primary() throws Exception {
        for (int i = 0; i < 3; i++) {
            baiduHealth.recordFailure();
        }
        MapRouter primary = (f, t) -> { throw new RuntimeException("should not be called"); };
        MapRouter secondary = (f, t) -> AMAP_ROUTE;

        FailoverRouter fo = new FailoverRouter(
                primary, secondary, baiduHealth, amapHealth, metrics);

        assertEquals(AMAP_ROUTE, fo.route(FROM, TO));
    }

    @Test
    void half_open_probe_success_restores() throws Exception {
        for (int i = 0; i < 3; i++) {
            baiduHealth.recordFailure();
        }
        clock.set(30_000L);

        MapRouter primary = (f, t) -> BAIDU_ROUTE;
        MapRouter secondary = (f, t) -> AMAP_ROUTE;

        FailoverRouter fo = new FailoverRouter(
                primary, secondary, baiduHealth, amapHealth, metrics);

        assertEquals(BAIDU_ROUTE, fo.route(FROM, TO));
        assertEquals(VendorHealth.State.CLOSED, baiduHealth.state());
    }

    @Test
    void null_metrics_does_not_throw() throws Exception {
        MapRouter primary = (f, t) -> { throw new MapApiException(500, "fail"); };
        MapRouter secondary = (f, t) -> AMAP_ROUTE;

        FailoverRouter fo = new FailoverRouter(
                primary, secondary, baiduHealth, amapHealth, null);

        assertEquals(AMAP_ROUTE, fo.route(FROM, TO));
    }

    private double switchCount() {
        return registry.counter(AgentMetrics.METRIC_VENDOR_SWITCH,
                "from", "baidu", "to", "amap").count();
    }
}
