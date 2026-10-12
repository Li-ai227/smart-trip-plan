package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AmapRouterHttp} 确定性单测（C-007 增量 B）。
 */
class AmapRouterHttpTest {

    private static final GeoLocation FROM = new GeoLocation(104.065735, 30.659462, "宽窄巷子", "成都");
    private static final GeoLocation TO = new GeoLocation(104.143254, 30.731768, "大熊猫基地", "成都");

    @Test
    void valid_response_parsed() throws Exception {
        String response = """
                {"status":"1","route":{"paths":[{"distance":"10000","duration":"1800"}]}}""";
        AmapRouterHttp router = new AmapRouterHttp(url -> response, "test-ak");

        Route result = router.route(FROM, TO);

        assertEquals(10.0, result.distanceKm(), 0.1);
        assertEquals(30, result.durationMin());
        assertEquals("驾车", result.transportMode());
    }

    @Test
    void status_not_one_throws() {
        AmapRouterHttp router = new AmapRouterHttp(
                url -> "{\"status\":\"0\",\"infocode\":\"10001\"}", "test-ak");

        assertThrows(MapApiException.class, () -> router.route(FROM, TO));
    }

    @Test
    void empty_paths_returns_zero_distance() throws Exception {
        String response = "{\"status\":\"1\",\"route\":{\"paths\":[]}}";
        AmapRouterHttp router = new AmapRouterHttp(url -> response, "test-ak");

        Route result = router.route(FROM, TO);

        assertEquals(0, result.distanceKm());
        assertEquals(0, result.durationMin());
    }

    @Test
    void malformed_response_throws() {
        AmapRouterHttp router = new AmapRouterHttp(url -> "broken json", "test-ak");

        assertThrows(MapApiException.class, () -> router.route(FROM, TO));
    }

    @Test
    void duration_rounds_up() throws Exception {
        String response = """
                {"status":"1","route":{"paths":[{"distance":"10000","duration":"1801"}]}}""";
        AmapRouterHttp router = new AmapRouterHttp(url -> response, "test-ak");

        Route result = router.route(FROM, TO);

        // 1801 秒 = 30.016 分钟 → ceil = 31
        assertEquals(31, result.durationMin());
    }

    @Test
    void blank_ak_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new AmapRouterHttp(url -> "", ""));
    }
}
