package com.smart.trip.agent.route.mcp;

import com.smart.trip.common.model.GeoLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AmapTrafficHttp} 确定性单测（C-007 增量 B）。
 */
class AmapTrafficHttpTest {

    private static final GeoLocation FROM = new GeoLocation(104.065735, 30.659462, "宽窄巷子", "成都");
    private static final GeoLocation TO = new GeoLocation(104.143254, 30.731768, "大熊猫基地", "成都");

    @Test
    void congested_traffic_returns_factor_above_one() throws Exception {
        String response = """
                {"status":"1","route":{"paths":[{"distance":"10000","duration":"1800",
                "steps":[{"tmcs":[{"status":"拥堵","distance":"3000"},{"status":"畅通","distance":"7000"}]}]}]}}""";
        AmapTrafficHttp traffic = new AmapTrafficHttp(url -> response, "test-ak");

        double factor = traffic.trafficFactor(FROM, TO);

        assertEquals(1.3, factor, 0.01);
    }

    @Test
    void smooth_traffic_returns_one() throws Exception {
        String response = """
                {"status":"1","route":{"paths":[{"distance":"10000","duration":"1800",
                "steps":[{"tmcs":[{"status":"畅通","distance":"10000"}]}]}]}}""";
        AmapTrafficHttp traffic = new AmapTrafficHttp(url -> response, "test-ak");

        double factor = traffic.trafficFactor(FROM, TO);

        assertEquals(1.0, factor, 0.01);
    }

    @Test
    void no_tmc_data_returns_one() throws Exception {
        String response = """
                {"status":"1","route":{"paths":[{"distance":"10000","duration":"1800","steps":[]}]}}""";
        AmapTrafficHttp traffic = new AmapTrafficHttp(url -> response, "test-ak");

        double factor = traffic.trafficFactor(FROM, TO);

        assertEquals(1.0, factor, 0.01);
    }

    @Test
    void no_paths_returns_one() throws Exception {
        String response = "{\"status\":\"1\",\"route\":{\"paths\":[]}}";
        AmapTrafficHttp traffic = new AmapTrafficHttp(url -> response, "test-ak");

        double factor = traffic.trafficFactor(FROM, TO);

        assertEquals(1.0, factor, 0.01);
    }

    @Test
    void status_not_one_throws() {
        AmapTrafficHttp traffic = new AmapTrafficHttp(
                url -> "{\"status\":\"0\",\"infocode\":\"10001\"}", "test-ak");

        assertThrows(MapApiException.class, () -> traffic.trafficFactor(FROM, TO));
    }

    @Test
    void malformed_json_throws() {
        AmapTrafficHttp traffic = new AmapTrafficHttp(url -> "broken", "test-ak");

        assertThrows(MapApiException.class, () -> traffic.trafficFactor(FROM, TO));
    }

    @Test
    void blank_ak_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new AmapTrafficHttp(url -> "", ""));
    }
}
