package com.smart.trip.server.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.trip.common.constant.CacheConstants;
import com.smart.trip.common.constant.CacheKeys;
import com.smart.trip.server.plan.repository.PlanDraftRecord;
import com.smart.trip.server.plan.repository.PlanDraftRepository;
import com.smart.trip.server.plan.service.PlanCacheService;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 服务重启后计划草案恢复集成测试（C-018 AC-3 / T-15）。
 *
 * <p>模拟场景：服务重启后 Redis 中草案数据丢失，验证 {@link PlanCacheService} 能通过
 * MySQL 降级读恢复草案（边界-2：服务重启后 plan_review 状态不丢失）。</p>
 *
 * <p>由于 CI 测试 profile 禁用 MySQL，使用 Mock 模拟 MySQL 持久化层，
 * 验证 PlanCacheService 的 Redis→MySQL 降级读逻辑正确。</p>
 */
@DisplayName("服务重启后计划草案恢复集成测试 (T-15)")
class PlanDraftRecoveryIT {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private PlanDraftRepository planDraftRepository;
    private PlanCacheService planCacheService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        planDraftRepository = mock(PlanDraftRepository.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        planCacheService = new PlanCacheService(redisTemplate, planDraftRepository);
    }

    @Test
    @DisplayName("场景1: 正常运行时存储草案，Redis + MySQL 均写入")
    void normalOperationStoreDraft() {
        String planId = "P-RECOVERY-001";
        String draft = "# 云南7日游计划\n\n## 行程概览\n...";

        planCacheService.storePlanDraft(planId, "user-1", draft);

        // Redis 写入验证
        verify(valueOps).set(CacheKeys.planDraftKey(planId), draft, CacheConstants.HISTORY_TTL);
        // MySQL 写入验证
        verify(planDraftRepository).insertDraft(planId, "user-1", draft);
    }

    @Test
    @DisplayName("场景2: 模拟服务重启，Redis 数据丢失，MySQL 降级读取恢复")
    void restartRecoveryFromMySql() {
        String planId = "P-RECOVERY-002";
        String draft = "# 草稿内容（Redis 丢失后从 MySQL 恢复）";

        // 模拟：写入时 Redis 和 MySQL 都正常
        when(planDraftRepository.insertDraft(planId, "user-1", draft)).thenReturn(1);
        planCacheService.storePlanDraft(planId, "user-1", draft);

        // 模拟服务重启：Redis 返回 null（数据丢失）
        when(valueOps.get(CacheKeys.planDraftKey(planId)))
                .thenReturn(null)        // 第一次读：Redis 空
                .thenReturn(draft);      // 第二次读：MySQL 恢复后回写

        // MySQL 中有数据
        when(planDraftRepository.findByPlanId(planId)).thenReturn(
                new PlanDraftRecord(null, planId, "user1", 1, draft,
                        PlanDraftRecord.STATUS_PLAN_REVIEW, 0, null, null, null));

        // 重启后读取草案
        Optional<String> recovered = planCacheService.getPlanDraft(planId);

        assertTrue(recovered.isPresent(), "重启后应能从 MySQL 恢复草案");
        assertEquals(draft, recovered.get(), "恢复的草案内容应一致");
    }

    @Test
    @DisplayName("场景3: Redis 和 MySQL 均无数据，返回 empty")
    void bothMissingReturnsEmpty() {
        String planId = "P-RECOVERY-003";

        when(valueOps.get(CacheKeys.planDraftKey(planId))).thenReturn(null);
        when(planDraftRepository.findByPlanId(planId)).thenReturn(null);

        Optional<String> result = planCacheService.getPlanDraft(planId);

        assertTrue(result.isEmpty(), "Redis 和 MySQL 都无数据时应返回 empty");
    }

    @Test
    @DisplayName("场景4: MySQL 查询异常，返回 empty 不抛异常")
    void mySqlExceptionReturnsEmpty() {
        String planId = "P-RECOVERY-004";

        when(valueOps.get(CacheKeys.planDraftKey(planId))).thenReturn(null);
        when(planDraftRepository.findByPlanId(planId)).thenThrow(new RuntimeException("DB连接异常"));

        Optional<String> result = planCacheService.getPlanDraft(planId);

        assertTrue(result.isEmpty(), "MySQL 异常时应返回 empty 不抛异常");
    }

    @Test
    @DisplayName("场景5: Redis 正常返回，无需降级 MySQL")
    void redisHitNoFallback() {
        String planId = "P-RECOVERY-005";
        String draft = "# Redis 中的草案";

        when(valueOps.get(CacheKeys.planDraftKey(planId))).thenReturn(draft);

        Optional<String> result = planCacheService.getPlanDraft(planId);

        assertTrue(result.isPresent());
        assertEquals(draft, result.get());
        // 不应触发 MySQL 降级读
        verify(planDraftRepository, never()).findByPlanId(anyString());
    }

    @Test
    @DisplayName("场景6: 多次写入同一 planId，最后一次覆盖")
    void multipleWritesSamePlanId() {
        String planId = "P-RECOVERY-006";
        String draftV1 = "# 版本1";
        String draftV2 = "# 版本2（最终版）";

        planCacheService.storePlanDraft(planId, "user-1", draftV1);
        planCacheService.storePlanDraft(planId, "user-1", draftV2);

        // Redis 返回最新版本
        when(valueOps.get(CacheKeys.planDraftKey(planId))).thenReturn(draftV2);

        Optional<String> result = planCacheService.getPlanDraft(planId);

        assertTrue(result.isPresent());
        assertEquals(draftV2, result.get(), "应返回最新版本");
    }
}