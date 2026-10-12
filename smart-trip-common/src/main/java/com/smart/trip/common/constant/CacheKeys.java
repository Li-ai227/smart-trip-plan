package com.smart.trip.common.constant;

/**
 * Redis 缓存 Key 拼装工具（C-003）。
 *
 * <p>按 {@code .harness/wiki/数据模型.md} §4 Redis Key Schema 生成统一前缀 {@code trip:} 的键，
 * 所有入参做非空/非空白校验（编码规范 §5 防御式编程），杜绝在业务代码中散写键格式。</p>
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /**
     * 生成行程方案键：{@code trip:plan:{planId}}。
     *
     * @param planId 方案 ID，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当 planId 为 null 或空白
     */
    public static String planKey(String planId) {
        return join(CacheConstants.DOMAIN_PLAN, require(planId, "planId"));
    }

    /**
     * 生成方案状态键：{@code trip:plan:{planId}:status}。
     *
     * @param planId 方案 ID，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当 planId 为 null 或空白
     */
    public static String planStatusKey(String planId) {
        return join(CacheConstants.DOMAIN_PLAN, require(planId, "planId"), CacheConstants.STATUS_SUFFIX);
    }

    /**
     * 生成会话上下文键：{@code trip:session:{sessionId}}。
     *
     * @param sessionId 会话 ID，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当 sessionId 为 null 或空白
     */
    public static String sessionKey(String sessionId) {
        return join(CacheConstants.DOMAIN_SESSION, require(sessionId, "sessionId"));
    }

    /**
     * 生成 PlanNotebook 中间步骤键：{@code trip:step:{planId}}。
     *
     * @param planId 方案 ID，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当 planId 为 null 或空白
     */
    public static String stepKey(String planId) {
        return join(CacheConstants.DOMAIN_STEP, require(planId, "planId"));
    }

    /**
     * 生成用户偏好键：{@code trip:pref:{userId}}。
     *
     * @param userId 用户 ID，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当 userId 为 null 或空白
     */
    public static String prefKey(String userId) {
        return join(CacheConstants.DOMAIN_PREF, require(userId, "userId"));
    }

    /**
     * 生成小红书笔记缓存键：{@code trip:xhs:note:{noteId}}。
     *
     * @param noteId 笔记 ID，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当 noteId 为 null 或空白
     */
    public static String xhsNoteKey(String noteId) {
        return join(CacheConstants.DOMAIN_XHS_NOTE, require(noteId, "noteId"));
    }

    /**
     * 生成限频计数键：{@code trip:ratelimit:{api}:{window}}。
     *
     * @param api    外部 API 标识，不可为空白
     * @param window 时间窗口标识，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当任一参数为 null 或空白
     */
    public static String rateLimitKey(String api, String window) {
        return join(CacheConstants.DOMAIN_RATELIMIT, require(api, "api"), require(window, "window"));
    }

    /**
     * 生成子 Agent 产出物缓存键：{@code trip:agent:result:{planId}:{agent}}。
     *
     * @param planId 方案 ID，不可为空白
     * @param agent  Agent 标识，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当任一参数为 null 或空白
     */
    public static String agentResultKey(String planId, String agent) {
        return join(CacheConstants.DOMAIN_AGENT_RESULT, require(planId, "planId"), require(agent, "agent"));
    }

    /**
     * 生成用户方案索引键：{@code trip:user:{userId}:plans}。
     *
     * <p>值类型为 Redis Sorted Set，score = 提交时间戳 millis。TTL 30 天。</p>
     *
     * @param userId 用户 ID，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当 userId 为 null 或空白
     */
    public static String userPlansKey(String userId) {
        return join(CacheConstants.DOMAIN_USER, require(userId, "userId"), "plans");
    }

    /**
     * 生成方案摘要键：{@code trip:plan:{planId}:summary}。
     *
     * <p>值类型为 Redis String（JSON），TTL 30 天。</p>
     *
     * @param planId 方案 ID，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当 planId 为 null 或空白
     */
    public static String planSummaryKey(String planId) {
        return join(CacheConstants.DOMAIN_PLAN, require(planId, "planId"), CacheConstants.SUMMARY_SUFFIX);
    }

    /**
     * 生成计划草案键：{@code trip:plan:{planId}:draft}（C-018 AC-3）。
     */
    public static String planDraftKey(String planId) {
        return join(CacheConstants.DOMAIN_PLAN, require(planId, "planId"), CacheConstants.DRAFT_SUFFIX);
    }

    /**
     * 生成请求指纹键：{@code trip:req:fingerprint:{hash}}。
     *
     * <p>用于相同规划参数命中已完成方案，避免重复编排。</p>
     *
     * @param hash 请求参数的 SHA-256 指纹，不可为空白
     * @return 完整 Redis Key
     * @throws IllegalArgumentException 当 hash 为 null 或空白
     */
    public static String requestFingerprintKey(String hash) {
        return join(CacheConstants.DOMAIN_REQ_FINGERPRINT, require(hash, "hash"));
    }

    /**
     * 生成全局标签缓存键：{@code trip:tags:all}。
     *
     * @return 完整 Redis Key（无需参数，全局共享）
     */
    public static String tagsAllKey() {
        return CacheConstants.KEY_PREFIX + CacheConstants.DOMAIN_TAGS + ":" + CacheConstants.ALL_SUFFIX;
    }

    /**
     * 按统一前缀与分隔符拼装各段。
     *
     * @param segments 已校验的键段，按顺序拼接
     * @return {@code trip:} 前缀开头的完整 Key
     */
    private static String join(String... segments) {
        return CacheConstants.KEY_PREFIX + String.join(CacheConstants.SEPARATOR, segments);
    }

    /**
     * 校验键段非空白。
     *
     * @param value 待校验值
     * @param name  参数名（用于异常信息定位）
     * @return 原值
     * @throws IllegalArgumentException 当 value 为 null 或空白
     */
    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缓存 Key 参数不可为空: " + name);
        }
        return value;
    }
}
