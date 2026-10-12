package com.smart.trip.skills.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.smart.trip.common.dto.PlanSummary;
import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.model.TripPlan;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link TripPlanPersistenceService} 接口 + {@link PlanHistoryQuery} DTO 结构守护（C-017 T-3）。
 */
class TripPlanPersistenceServiceTest {

    @Nested
    @DisplayName("TripPlanPersistenceService 接口契约")
    class InterfaceContract {

        @Test
        @DisplayName("save 方法签名：void save(TripPlan, String userId, String planStatus)")
        void saveMethodSignature() throws NoSuchMethodException {
            Method save = TripPlanPersistenceService.class.getMethod(
                    "save", TripPlan.class, String.class, String.class);
            assertEquals(void.class, save.getReturnType());
        }

        @Test
        @DisplayName("queryHistory 方法签名：List<PlanSummary> queryHistory(PlanHistoryQuery)")
        void queryHistoryMethodSignature() throws NoSuchMethodException {
            Method query = TripPlanPersistenceService.class.getMethod(
                    "queryHistory", PlanHistoryQuery.class);
            assertEquals(List.class, query.getReturnType());
        }

        @Test
        @DisplayName("countHistory 方法签名：long countHistory(PlanHistoryQuery)")
        void countHistoryMethodSignature() throws NoSuchMethodException {
            Method count = TripPlanPersistenceService.class.getMethod(
                    "countHistory", PlanHistoryQuery.class);
            assertEquals(long.class, count.getReturnType());
        }

        @Test
        @DisplayName("queryDetail 方法签名：Optional<TripPlan> queryDetail(String planId)")
        void queryDetailMethodSignature() throws NoSuchMethodException {
            Method detail = TripPlanPersistenceService.class.getMethod(
                    "queryDetail", String.class);
            assertEquals(Optional.class, detail.getReturnType());
        }

        @Test
        @DisplayName("softDelete 方法签名：boolean softDelete(String planId, String userId)")
        void softDeleteMethodSignature() throws NoSuchMethodException {
            Method delete = TripPlanPersistenceService.class.getMethod(
                    "softDelete", String.class, String.class);
            assertEquals(boolean.class, delete.getReturnType());
        }
    }

    @Nested
    @DisplayName("PlanHistoryQuery DTO")
    class HistoryQueryDto {

        @Test
        @DisplayName("必填字段 userId 不可为 null")
        void userIdRequired() {
            PlanHistoryQuery query = new PlanHistoryQuery(
                    "user-001", 1, 10, null, null, null, null, null);
            assertEquals("user-001", query.userId());
        }

        @Test
        @DisplayName("分页默认值归一：page < 1 归为 1，pageSize < 1 归为 10")
        void paginationDefaults() {
            PlanHistoryQuery query = new PlanHistoryQuery(
                    "user-001", 0, -5, null, null, null, null, null);
            assertEquals(1, query.page());
            assertEquals(10, query.pageSize());
        }

        @Test
        @DisplayName("pageSize 上限 100")
        void pageSizeCap() {
            PlanHistoryQuery query = new PlanHistoryQuery(
                    "user-001", 1, 200, null, null, null, null, null);
            assertEquals(100, query.pageSize());
        }

        @Test
        @DisplayName("所有筛选字段可为 null（无筛选）")
        void allFiltersNullable() {
            PlanHistoryQuery query = new PlanHistoryQuery(
                    "user-001", 1, 10, null, null, null, null, null);
            assertNull(query.keyword());
            assertNull(query.travelStyle());
            assertNull(query.planStatus());
            assertNull(query.startDate());
            assertNull(query.endDate());
        }

        @Test
        @DisplayName("offset 计算正确")
        void offsetCalculation() {
            PlanHistoryQuery query = new PlanHistoryQuery(
                    "user-001", 3, 20, null, null, null, null, null);
            assertEquals(40, query.offset());
        }

        @Test
        @DisplayName("完整筛选构造")
        void fullFilter() {
            PlanHistoryQuery query = new PlanHistoryQuery(
                    "user-001", 1, 10, "北京",
                    TravelStyle.FAMILY, "confirmed",
                    LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
            assertEquals("北京", query.keyword());
            assertEquals(TravelStyle.FAMILY, query.travelStyle());
            assertEquals("confirmed", query.planStatus());
            assertEquals(LocalDate.of(2026, 7, 1), query.startDate());
            assertEquals(LocalDate.of(2026, 7, 31), query.endDate());
        }
    }
}
