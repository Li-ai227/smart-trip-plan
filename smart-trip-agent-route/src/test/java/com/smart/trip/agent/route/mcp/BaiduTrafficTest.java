package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BaiduTraffic} 确定性单测（C-007 增量 B）。
 */
class BaiduTrafficTest {

    private static final GeoLocation FROM = new GeoLocation(104.065735, 30.659462, "宽窄巷子", "成都");
    private static final GeoLocation TO = new GeoLocation(104.143254, 30.731768, "大熊猫基地", "成都");

    @Test
    void normal_traffic_returns_factor() throws Exception {
        String response = """
                {"status":0,"result":{"routes":[{"duration":1800,"traffic_condition_duration":2700}]}}""";
        BaiduTraffic traffic = new BaiduTraffic(url -> response, "test-ak");

        double factor = traffic.trafficFactor(FROM, TO);

        assertEquals(1.5, factor, 0.01);
    }

    @Test
    void smooth_traffic_returns_one() throws Exception {
        String response = """
                {"status":0,"result":{"routes":[{"duration":1800,"traffic_condition_duration":1800}]}}""";
        BaiduTraffic traffic = new BaiduTraffic(url -> response, "test-ak");

        double factor = traffic.trafficFactor(FROM, TO);

        assertEquals(1.0, factor, 0.01);
    }

    @Test
    void missing_traffic_duration_returns_one() throws Exception {
        String response = "{\"status\":0,\"result\":{\"routes\":[{\"duration\":1800}]}}";
        BaiduTraffic traffic = new BaiduTraffic(url -> response, "test-ak");

        double factor = traffic.trafficFactor(FROM, TO);

        assertEquals(1.0, factor, 0.01);
    }

    @Test
    void no_routes_returns_one() throws Exception {
        String response = "{\"status\":0,\"result\":{\"routes\":[]}}";
        BaiduTraffic traffic = new BaiduTraffic(url -> response, "test-ak");

        double factor = traffic.trafficFactor(FROM, TO);

        assertEquals(1.0, factor, 0.01);
    }

    @Test
    void status_non_zero_throws() {
        BaiduTraffic traffic = new BaiduTraffic(
                url -> "{\"status\":1}", "test-ak");

        assertThrows(MapApiException.class, () -> traffic.trafficFactor(FROM, TO));
    }

    @Test
    void malformed_json_throws() {
        BaiduTraffic traffic = new BaiduTraffic(url -> "broken", "test-ak");

        assertThrows(MapApiException.class, () -> traffic.trafficFactor(FROM, TO));
    }

    @Test
    void blank_ak_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new BaiduTraffic(url -> "", ""));
    }
}
