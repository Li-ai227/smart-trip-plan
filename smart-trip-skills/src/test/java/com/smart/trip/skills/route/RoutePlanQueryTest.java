package com.smart.trip.skills.route;

import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RoutePlanQuery} 构造校验测试（C-007 T-1，Red→Green）。
 */
class RoutePlanQueryTest {

    private static final GeoLocation CHENGDU = new GeoLocation(104.0665, 30.5728, "成都", "成都");
    private static final Attraction A1 = new Attraction("A-001", "宽窄巷子", CHENGDU, 4.5, 120, BigDecimal.TEN, List.of());

    @Test
    void nullDestination_throws() {
        assertThrows(InvalidRequestException.class,
                () -> new RoutePlanQuery(null, List.of(A1), null));
    }

    @Test
    void blankDestination_throws() {
        assertThrows(InvalidRequestException.class,
                () -> new RoutePlanQuery("  ", List.of(A1), null));
    }

    @Test
    void nullAttractions_normalizedToEmptyList() {
        var query = new RoutePlanQuery("成都", null, null);
        assertNotNull(query.attractions());
        assertTrue(query.attractions().isEmpty());
    }

    @Test
    void nullTransportMode_defaultsToDriving() {
        var query = new RoutePlanQuery("成都", List.of(A1), null);
        assertEquals(RoutePlanQuery.DEFAULT_TRANSPORT_MODE, query.transportMode());
    }

    @Test
    void blankTransportMode_defaultsToDriving() {
        var query = new RoutePlanQuery("成都", List.of(A1), "  ");
        assertEquals(RoutePlanQuery.DEFAULT_TRANSPORT_MODE, query.transportMode());
    }

    @Test
    void ofFactory_correctDefaults() {
        var query = RoutePlanQuery.of("成都", List.of(A1));
        assertEquals("成都", query.destination());
        assertEquals(1, query.attractions().size());
        assertEquals(RoutePlanQuery.DEFAULT_TRANSPORT_MODE, query.transportMode());
    }
}
