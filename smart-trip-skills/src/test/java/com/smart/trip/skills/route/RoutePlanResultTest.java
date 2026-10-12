package com.smart.trip.skills.route;

import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Route;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RoutePlanResult} 构造与 null 安全测试（C-007 T-1）。
 */
class RoutePlanResultTest {

    private static final GeoLocation A = new GeoLocation(104.0665, 30.5728, "A", "成都");
    private static final GeoLocation B = new GeoLocation(104.0700, 30.5800, "B", "成都");
    private static final Route NORMAL = new Route(A, B, 5.0, 15, "驾车", false);

    @Test
    void nullLists_normalizedToEmpty() {
        var result = new RoutePlanResult(null, null, null, false, null, null);
        assertNotNull(result.routes());
        assertTrue(result.routes().isEmpty());
        assertNotNull(result.overLimitRoutes());
        assertNotNull(result.unreachableSegments());
    }

    @Test
    void nullTelemetry_defaultsToNone() {
        var result = new RoutePlanResult(List.of(NORMAL), List.of(), List.of(), false, null, null);
        assertNotNull(result.telemetry());
        assertFalse(result.telemetry().rateLimited());
    }

    @Test
    void durationLimitConstant_is120() {
        assertEquals(120, RoutePlanResult.DURATION_LIMIT_MINUTES);
    }
}
