package com.smart.trip.server.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.trip.common.constant.CacheConstants;
import com.smart.trip.common.constant.CacheKeys;
import com.smart.trip.server.plan.repository.PlanDraftRecord;
import com.smart.trip.server.plan.repository.PlanDraftRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * {@link PlanCacheService} 单测（C-018 T-5 / T-14 MySQL 双层写入）。
 */
class PlanCacheServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private PlanDraftRepository planDraftRepository;
    private PlanCacheService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        planDraftRepository = mock(PlanDraftRepository.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new PlanCacheService(redisTemplate, planDraftRepository);
    }

    @Nested
    @DisplayName("C-018: 计划草案写入与读取（含 MySQL 双层写入 T-14）")
    class PlanDraft {

        @Test
        @DisplayName("storePlanDraft 同时写入 Redis 和 MySQL")
        void storeAndGetPlanDraft() {
            String planId = "P-20260710-001";
            String draft = "# 云南5日游计划\n1. XHS分析...";

            service.storePlanDraft(planId, "user-1", draft);

            // Redis 写入
            verify(valueOps).set(CacheKeys.planDraftKey(planId), draft, CacheConstants.HISTORY_TTL);
            // MySQL 写入
            verify(planDraftRepository).insertDraft(planId, "user-1", draft);

            // Redis 读取
            when(valueOps.get(CacheKeys.planDraftKey(planId))).thenReturn(draft);
            Optional<String> loaded = service.getPlanDraft(planId);
            assertTrue(loaded.isPresent());
            assertEquals(draft, loaded.get());
        }

        @Test
        @DisplayName("getPlanDraft Redis 缺失时降级读 MySQL")
        void getPlanDraftFallbackToMySql() {
            String planId = "P-FALLBACK";
            String draft = "# MySQL 降级草案";

            when(valueOps.get(CacheKeys.planDraftKey(planId))).thenReturn(null);
            when(planDraftRepository.findByPlanId(planId)).thenReturn(
                    new PlanDraftRecord(null, planId, "user1", 1, draft,
                            PlanDraftRecord.STATUS_PLAN_REVIEW, 0, null, null, null));

            Optional<String> loaded = service.getPlanDraft(planId);

            assertTrue(loaded.isPresent());
            assertEquals(draft, loaded.get());
        }

        @Test
        @DisplayName("getPlanDraft Redis 和 MySQL 都缺失返回 empty")
        void getPlanDraftWhenMissingReturnsEmpty() {
            String planId = "P-NO-DRAFT";

            when(valueOps.get(CacheKeys.planDraftKey(planId))).thenReturn(null);
            when(planDraftRepository.findByPlanId(planId)).thenReturn(null);

            assertTrue(service.getPlanDraft(planId).isEmpty());
        }

        @Test
        @DisplayName("MySQL 写入失败不影响 Redis 写入（WARNING 日志，不阻塞主链路）")
        void mySqlWriteFailureDoesNotBlockRedis() {
            String planId = "P-MYSQL-FAIL";
            String draft = "# 草案";

            when(planDraftRepository.insertDraft(planId, "user-1", draft)).thenReturn(0);

            service.storePlanDraft(planId, "user-1", draft);

            // Redis 写入仍正常执行
            verify(valueOps).set(CacheKeys.planDraftKey(planId), draft, CacheConstants.HISTORY_TTL);
        }

        @Test
        @DisplayName("getPlanDraft MySQL 查询失败时返回 empty（不抛异常）")
        void mySqlQueryFailureReturnsEmpty() {
            String planId = "P-MYSQL-ERR";

            when(valueOps.get(CacheKeys.planDraftKey(planId))).thenReturn(null);
            when(planDraftRepository.findByPlanId(planId)).thenThrow(new RuntimeException("DB down"));

            Optional<String> loaded = service.getPlanDraft(planId);

            assertTrue(loaded.isEmpty());
        }
    }
}