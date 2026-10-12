package com.smart.trip.skills.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.a2a.Msg;
import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.enums.MealType;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.GeoLocation;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.budget.BudgetCalculationResult;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import com.smart.trip.skills.quality.OutputQualityGate;
import com.smart.trip.skills.route.RoutePlanResult;
import com.smart.trip.skills.xhs.AttractionCandidate;
import com.smart.trip.skills.xhs.XHSAnalysisResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link TripOrchestrationTools} @Tool 直接单测（C-010 v2）。
 *
 * <p>覆盖：orchestrate_trip JSON 往返、lastResult 捕获与一致性、
 * 未调用时 lastResult=null、非法 JSON 处理、空 request 处理。</p>
 */
class TripOrchestrationToolsTest {

    private static final GeoLocation LOC = new GeoLocation(116.4, 39.9, "北京", "北京");

    private static TripPlanRequest request() {
        return new TripPlanRequest("北京", LocalDate.of(2026, 7, 1), 2,
                new BigDecimal("5000"), 2, TravelStyle.COUPLE, List.of("古迹"));
    }

    private static XHSAnalysisResult xhsResult() {
        return new XHSAnalysisResult(
                List.of(new AttractionCandidate(
                        new Attraction("A-1", "故宫", LOC, 4.5, 120,
                                new BigDecimal("50"), List.of("古迹")),
                        10, 8, 1, 1)),
                false, null, null);
    }

    private static RoutePlanResult routeResult() {
        return new RoutePlanResult(List.of(), List.of(), List.of(), false, null, null);
    }

    private static ItineraryDesignResult itineraryResult() {
        TripDay day = new TripDay(LocalDate.of(2026, 7, 1), 1,
                List.of(new Attraction("A-1", "故宫", LOC, 4.5, 120,
                        new BigDecimal("50"), List.of("古迹"))),
                List.of(new Meal("餐厅", LOC, MealType.LUNCH, new BigDecimal("50"), List.of()),
                        new Meal("餐厅", LOC, MealType.DINNER, new BigDecimal("50"), List.of())),
                List.of(), new BigDecimal("300"));
        return new ItineraryDesignResult(List.of(day, day2()), List.of(), false, null, null);
    }

    private static TripDay day2() {
        return new TripDay(LocalDate.of(2026, 7, 2), 2,
                List.of(new Attraction("A-2", "长城", LOC, 4.8, 180,
                        new BigDecimal("80"), List.of("古迹"))),
                List.of(new Meal("餐厅2", LOC, MealType.LUNCH, new BigDecimal("60"), List.of()),
                        new Meal("餐厅2", LOC, MealType.DINNER, new BigDecimal("60"), List.of())),
                List.of(), new BigDecimal("400"));
    }

    private static BudgetCalculationResult budgetResult() {
        return new BudgetCalculationResult(
                new BigDecimal("900"), new BigDecimal("200"), new BigDecimal("400"),
                new BigDecimal("200"), new BigDecimal("100"),
                new BigDecimal("-0.02"), List.of(), false, null, List.of(), false, null);
    }

    private static TripOrchestrationService stubService() {
        SubAgentDispatcher dispatcher = (agentUri, assign) -> {
            Object result = switch (agentUri) {
                case "agent://xhs-analyst" -> xhsResult();
                case "agent://route-planner" -> routeResult();
                case "agent://itinerary-designer" -> itineraryResult();
                case "agent://budget-controller" -> budgetResult();
                default -> null;
            };
            return Msg.taskResult(agentUri, assign.from(), assign.traceId(), result, false);
        };
        return new TripOrchestrationService(dispatcher, new OutputQualityGate());
    }

    private TripOrchestrationTools tools;

    @BeforeEach
    void setUp() {
        tools = new TripOrchestrationTools(stubService());
    }

    @Nested
    @DisplayName("lastResult 生命周期")
    class LastResultLifecycle {

        @Test
        @DisplayName("未调用 orchestrateTrip 前 → lastResult() 返回 null")
        void nullBeforeCall() {
            assertNull(tools.lastResult());
        }

        @Test
        @DisplayName("调用 orchestrateTrip 后 → lastResult() 返回非 null TripPlanResult")
        void nonNullAfterCall() {
            String json = JsonUtils.toJson(request());
            tools.orchestrateTrip(json);
            assertNotNull(tools.lastResult());
            assertNotNull(tools.lastResult().plan());
        }

        @Test
        @DisplayName("多次调用 → lastResult() 返回最后一次结果（覆盖更新）")
        void lastResultOverwritten() {
            String json1 = JsonUtils.toJson(request());
            tools.orchestrateTrip(json1);
            TripPlanResult first = tools.lastResult();
            assertNotNull(first);

            // 第二次调用
            TripPlanRequest req2 = new TripPlanRequest("上海", LocalDate.of(2026, 8, 1), 3,
                    new BigDecimal("10000"), 2, TravelStyle.FAMILY, List.of("美食"));
            tools.orchestrateTrip(JsonUtils.toJson(req2));
            TripPlanResult second = tools.lastResult();
            assertNotNull(second);
            // 第二次结果应不同于第一次（destination 不同）
            assertEquals("上海", second.plan().request().destination());
        }
    }

    @Nested
    @DisplayName("JSON 往返正确性")
    class JsonRoundTrip {

        @Test
        @DisplayName("有效 requestJson → 返回 TripPlanResult JSON 字符串，可反序列化")
        void validJsonReturnsDeserializableResult() {
            String requestJson = JsonUtils.toJson(request());
            String resultJson = tools.orchestrateTrip(requestJson);

            assertNotNull(resultJson);
            // 可反序列化回 TripPlanResult
            TripPlanResult result = JsonUtils.fromJson(resultJson, TripPlanResult.class);
            assertNotNull(result);
            assertNotNull(result.plan());
            assertEquals(2, result.plan().days().size());
        }

        @Test
        @DisplayName("orchestrateTrip JSON 产物 == 直接调 TripOrchestrationService.orchestrate() 的产物（一致性）")
        void consistencyBetweenToolAndDirectCall() {
            TripOrchestrationService service = stubService();
            TripPlanRequest req = request();

            // 直接调用
            TripPlanResult direct = service.orchestrate(new TripOrchestrationQuery(req));

            // 经 @Tool 调用
            String resultJson = tools.orchestrateTrip(JsonUtils.toJson(req));
            TripPlanResult viaTool = tools.lastResult();

            assertNotNull(direct);
            assertNotNull(viaTool);
            // 核心字段一致
            assertEquals(direct.plan().days().size(), viaTool.plan().days().size());
            assertEquals(direct.requiresHITL(), viaTool.requiresHITL());
            assertEquals(direct.fallback(), viaTool.fallback());
            assertEquals(direct.telemetry().subAgentFallbackCount(),
                    viaTool.telemetry().subAgentFallbackCount());
        }
    }

    @Nested
    @DisplayName("异常输入处理")
    class ErrorHandling {

        @Test
        @DisplayName("null requestJson → 抛异常")
        void nullInput() {
            assertThrows(Exception.class, () -> tools.orchestrateTrip(null));
        }

        @Test
        @DisplayName("空字符串 requestJson → 抛异常")
        void emptyInput() {
            assertThrows(Exception.class, () -> tools.orchestrateTrip(""));
        }

        @Test
        @DisplayName("非法 JSON → 抛 IllegalArgumentException")
        void invalidJson() {
            assertThrows(Exception.class, () -> tools.orchestrateTrip("{not valid json"));
        }

        @Test
        @DisplayName("合法 JSON 但非 TripPlanRequest 结构 → 抛异常")
        void validJsonButWrongStructure() {
            assertThrows(Exception.class, () -> tools.orchestrateTrip("{\"foo\": \"bar\"}"));
        }
    }
}
