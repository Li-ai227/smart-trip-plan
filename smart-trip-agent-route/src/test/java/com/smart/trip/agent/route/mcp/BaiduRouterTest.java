package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BaiduRouter} 确定性单测（C-007 增量 B）。
 */
class BaiduRouterTest {

    private static final GeoLocation FROM = new GeoLocation(104.065735, 30.659462, "宽窄巷子", "成都");
    private static final GeoLocation TO = new GeoLocation(104.143254, 30.731768, "大熊猫基地", "成都");

    private static final String VALID_RESPONSE = """
            {"status":0,"result":{"routes":[{"distance":15200,"duration":1800}]}}""";

    @Test
    void valid_response_parsed_to_route() throws Exception {
        BaiduRouter router = new BaiduRouter(url -> VALID_RESPONSE, "test-ak");

        Route result = router.route(FROM, TO);

        assertEquals(15.2, result.distanceKm(), 0.01);
        assertEquals(30, result.durationMin());
        assertEquals("驾车", result.transportMode());
        assertFalse(result.fallback());
        assertEquals(FROM, result.from());
        assertEquals(TO, result.to());
    }

    @Test
    void status_non_zero_throws() {
        BaiduRouter router = new BaiduRouter(
                url -> "{\"status\":302,\"message\":\"auth fail\"}", "test-ak");

        MapApiException ex = assertThrows(MapApiException.class,
                () -> router.route(FROM, TO));
        assertEquals(302, ex.statusCode());
    }

    @Test
    void empty_routes_throws() {
        BaiduRouter router = new BaiduRouter(
                url -> "{\"status\":0,\"result\":{\"routes\":[]}}", "test-ak");

        assertThrows(MapApiException.class, () -> router.route(FROM, TO));
    }

    @Test
    void malformed_json_throws() {
        BaiduRouter router = new BaiduRouter(url -> "not json", "test-ak");

        assertThrows(MapApiException.class, () -> router.route(FROM, TO));
    }

    @Test
    void duration_rounded_up() throws Exception {
        String response = "{\"status\":0,\"result\":{\"routes\":[{\"distance\":5000,\"duration\":125}]}}";
        BaiduRouter router = new BaiduRouter(url -> response, "test-ak");

        Route result = router.route(FROM, TO);

        assertEquals(3, result.durationMin());
    }

    @Test
    void blank_ak_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaiduRouter(url -> "", " "));
    }
}
