package com.smart.trip.server.plan.service;

import com.smart.trip.common.constant.CacheConstants;
import com.smart.trip.common.constant.CacheKeys;
import com.smart.trip.server.plan.repository.PlanDraftRecord;
import com.smart.trip.server.plan.repository.PlanDraftRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 规划 Redis 缓存服务（C-011 AC-7 / ADR-006）。
 *
 * <p>封装三层 Key：{@code trip:plan:{planId}}（TripPlanDetail JSON）、
 * {@code trip:plan:{planId}:status}、{@code trip:plan:{planId}:userId}，TTL 均 30d。
 * Key 前缀复用 {@link CacheConstants}。</p>
 *
 * <p>T-14: 草案存取增加 MySQL 双层写入。{@link #storePlanDraft} 同时写入 Redis + MySQL，
 * {@link #getPlanDraft} 优先读 Redis，Redis 缺失时降级读 MySQL。MySQL 写入失败不阻塞主链路。</p>
 */
public class PlanCacheService {

    private static final System.Logger LOG = System.getLogger(PlanCacheService.class.getName());

    private final StringRedisTemplate redisTemplate;
    private final PlanDraftRepository planDraftRepository;

    /**
     * 构造规划缓存服务（兼容旧版，无 MySQL 备用）。
     *
     * @param redisTemplate Spring Data Redis 字符串模板
     */
    public PlanCacheService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, null);
    }

    /**
     * 构造规划缓存服务（含 MySQL 草案仓库）。
     *
     * @param redisTemplate        Spring Data Redis 字符串模板
     * @param planDraftRepository  MySQL 草案仓库（可 null，null 时不写入 MySQL）
     */
    public PlanCacheService(StringRedisTemplate redisTemplate,
                             PlanDraftRepository planDraftRepository) {
        this.redisTemplate = redisTemplate;
        this.planDraftRepository = planDraftRepository;
    }

    /**
     * 存储 TripPlanDetail JSON。
     *
     * @param planId   方案 ID
     * @param planJson JSON 字符串
     */
    public void storePlan(String planId, String planJson) {
        LOG.log(System.Logger.Level.INFO, "storePlan: planId={0}", planId);
        redisTemplate.opsForValue().set(
                CacheKeys.planKey(planId), planJson, CacheConstants.HISTORY_TTL);
    }

    /**
     * 读取 TripPlanDetail JSON。
     *
     * @param planId 方案 ID
     * @return JSON 字符串，或 empty
     */
    public Optional<String> getPlan(String planId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(CacheKeys.planKey(planId)));
    }

    /**
     * 设置方案状态。
     *
     * @param planId 方案 ID
     * @param status 状态字符串（PlanStatus 名称）
     */
    public void setStatus(String planId, String status) {
        LOG.log(System.Logger.Level.INFO, "setStatus: planId={0}, status={1}", planId, status);
        redisTemplate.opsForValue().set(
                CacheKeys.planStatusKey(planId), status, CacheConstants.HISTORY_TTL);
    }

    /**
     * 读取方案状态。
     *
     * @param planId 方案 ID
     * @return 状态字符串，或 empty
     */
    public Optional<String> getStatus(String planId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(CacheKeys.planStatusKey(planId)));
    }

    /**
     * 设置方案关联用户。
     *
     * @param planId 方案 ID
     * @param userId 用户 ID
     */
    public void setUserId(String planId, String userId) {
        redisTemplate.opsForValue().set(
                planUserIdKey(planId), userId, CacheConstants.HISTORY_TTL);
    }

    /**
     * 读取方案关联用户。
     *
     * @param planId 方案 ID
     * @return 用户 ID，或 empty
     */
    public Optional<String> getUserId(String planId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(planUserIdKey(planId)));
    }

    /**
     * 判断方案是否存在（status key 存在即视为存在）。
     *
     * @param planId 方案 ID
     * @return true 表示存在
     */
    public boolean exists(String planId) {
        return getStatus(planId).isPresent();
    }

    /**
     * 存储反馈 JSON。
     *
     * @param planId       方案 ID
     * @param feedbackJson 反馈 JSON
     */
    public void storeFeedback(String planId, String feedbackJson) {
        String key = CacheKeys.planKey(planId) + CacheConstants.SEPARATOR + "feedback";
        redisTemplate.opsForValue().set(key, feedbackJson, CacheConstants.HISTORY_TTL);
    }

    // ========== 请求指纹缓存 ==========

    /**
     * 存储请求指纹 → planId 映射。
     *
     * @param hash   请求参数 SHA-256 指纹
     * @param planId 方案 ID
     */
    public void storeRequestFingerprint(String hash, String planId) {
        LOG.log(System.Logger.Level.INFO,
                "storeRequestFingerprint: hash={0}, planId={1}", hash, planId);
        redisTemplate.opsForValue().set(
                CacheKeys.requestFingerprintKey(hash), planId, CacheConstants.HISTORY_TTL);
    }

    /**
     * 根据请求指纹查找已有方案 ID。
     *
     * @param hash 请求参数 SHA-256 指纹
     * @return 已缓存的 planId，或 empty
     */
    public Optional<String> getRequestFingerprint(String hash) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(CacheKeys.requestFingerprintKey(hash)));
    }

    // ========== 历史方案索引 ==========

    /**
     * 存储方案摘要 JSON（TTL 30 天）。
     *
     * @param planId      方案 ID
     * @param summaryJson 摘要 JSON 字符串
     */
    public void storePlanSummary(String planId, String summaryJson) {
        redisTemplate.opsForValue().set(
                CacheKeys.planSummaryKey(planId), summaryJson, CacheConstants.HISTORY_TTL);
    }

    /**
     * 读取方案摘要 JSON。
     *
     * @param planId 方案 ID
     * @return 摘要 JSON 字符串，或 empty
     */
    public Optional<String> getPlanSummary(String planId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(CacheKeys.planSummaryKey(planId)));
    }

    /**
     * 将方案 ID 加入用户历史索引（Sorted Set，score = 时间戳）。
     *
     * @param userId    用户 ID
     * @param planId    方案 ID
     * @param timestamp 提交时间戳（epoch millis）
     */
    public void addToUserPlanIndex(String userId, String planId, long timestamp) {
        String key = CacheKeys.userPlansKey(userId);
        redisTemplate.opsForZSet().add(key, planId, timestamp);
        redisTemplate.expire(key, CacheConstants.HISTORY_TTL);
    }

    /**
     * 获取用户历史方案 ID 列表（逆序 = 最新在前）。
     *
     * @param userId 用户 ID
     * @param offset 偏移量
     * @param count  数量
     * @return planId 列表（逆序）
     */
    public List<String> getUserPlanIds(String userId, long offset, long count) {
        Set<String> ids = redisTemplate.opsForZSet()
                .reverseRange(CacheKeys.userPlansKey(userId), offset, offset + count - 1);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(ids);
    }

    /**
     * 获取用户历史方案总数。
     *
     * @param userId 用户 ID
     * @return 方案总数
     */
    public long getUserPlanCount(String userId) {
        Long count = redisTemplate.opsForZSet().zCard(CacheKeys.userPlansKey(userId));
        return count != null ? count : 0L;
    }

    private static String planUserIdKey(String planId) {
        return CacheKeys.planKey(planId) + CacheConstants.SEPARATOR + CacheConstants.USER_ID_SUFFIX;
    }

    // ========== 计划草案（C-018） ==========

    /** 存储计划草案 Markdown（C-018 AC-3）。T-14: 同时写入 Redis 和 MySQL。 */
    public void storePlanDraft(String planId, String userId, String draft) {
        // 1. Redis 主链路（必须成功：等同主链路性能）
        redisTemplate.opsForValue().set(
                CacheKeys.planDraftKey(planId), draft, CacheConstants.HISTORY_TTL);
        // 2. MySQL 异步备写（失败不阻塞主链路）
        if (planDraftRepository != null) {
            try {
                int rows = planDraftRepository.insertDraft(planId, userId, draft);
                if (rows == 0) {
                    LOG.log(System.Logger.Level.WARNING,
                            "草案 MySQL 写入返回 0: planId={0}", planId);
                }
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.WARNING,
                        "草案 MySQL 写入异常: planId={0}, error={1}", planId, ex.getMessage());
            }
        }
    }

    /** 读取计划草案 Markdown（C-018 AC-3）。T-14: Redis 缺失时降级读 MySQL。 */
    public Optional<String> getPlanDraft(String planId) {
        // 1. 优先读 Redis
        String redisVal = redisTemplate.opsForValue().get(CacheKeys.planDraftKey(planId));
        if (redisVal != null) {
            return Optional.of(redisVal);
        }
        // 2. Redis 缺失 → 降级读 MySQL
        if (planDraftRepository != null) {
            try {
                PlanDraftRecord record = planDraftRepository.findByPlanId(planId);
                if (record != null && record.draftMarkdown() != null) {
                    LOG.log(System.Logger.Level.INFO,
                            "草案 Redis 缺失，MySQL 降级读取成功: planId={0}", planId);
                    return Optional.of(record.draftMarkdown());
                }
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.WARNING,
                        "草案 MySQL 降级读异常: planId={0}, error={1}", planId, ex.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * 更新草案状态（C-018 AC-4）。用于 CONFIRM（→ confirmed）或 REJECT（→ failed）场景。
     * MySQL 写入失败不阻塞主链路（边界-8）。
     *
     * @param planId        方案 ID
     * @param draftStatus   目标状态（confirmed / failed / rejected）
     * @param failureReason 失败原因（draftStatus=failed 时填写，可 null）
     */
    public void updatePlanDraftStatus(String planId, String draftStatus, String failureReason) {
        if (planDraftRepository != null) {
            try {
                int rows = planDraftRepository.updateStatus(planId, draftStatus, failureReason);
                if (rows == 0) {
                    LOG.log(System.Logger.Level.WARNING,
                            "草案状态 MySQL 更新返回 0: planId={0}, status={1}", planId, draftStatus);
                }
            } catch (Exception ex) {
                LOG.log(System.Logger.Level.WARNING,
                        "草案状态 MySQL 更新异常: planId={0}, status={1}, error={2}",
                        planId, draftStatus, ex.getMessage());
            }
        }
    }
}
