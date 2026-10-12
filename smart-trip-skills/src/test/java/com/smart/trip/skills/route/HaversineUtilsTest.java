package com.smart.trip.skills.route;

import com.smart.trip.common.model.GeoLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HaversineUtils} 精度回归测试（C-007 T-1/T-5）。
 */
class HaversineUtilsTest {

    private static final GeoLocation BEIJING = new GeoLocation(116.4074, 39.9042, "北京", "北京");
    private static final GeoLocation SHANGHAI = new GeoLocation(121.4737, 31.2304, "上海", "上海");
    private static final GeoLocation CHENGDU = new GeoLocation(104.0665, 30.5728, "成都", "成都");
    private static final GeoLocation CHONGQING = new GeoLocation(106.5516, 29.5630, "重庆", "重庆");

    @Test
    void beijing_shanghai_approximately1060km() {
        double km = HaversineUtils.distanceKm(BEIJING, SHANGHAI);
        assertEquals(1068.0, km, 20.0);
    }

    @Test
    void chengdu_chongqing_approximately270km() {
        double km = HaversineUtils.distanceKm(CHENGDU, CHONGQING);
        assertEquals(270.0, km, 20.0);
    }

    @Test
    void samePoint_zeroDistance() {
        double km = HaversineUtils.distanceKm(BEIJING, BEIJING);
        assertEquals(0.0, km, 0.001);
    }

    @Test
    void estimateDuration_positive() {
        int minutes = HaversineUtils.estimateDurationMin(CHENGDU, CHONGQING);
        assertTrue(minutes > 0);
    }

    @Test
    void estimateDuration_samePoint_zero() {
        int minutes = HaversineUtils.estimateDurationMin(CHENGDU, CHENGDU);
        assertEquals(0, minutes);
    }
}
