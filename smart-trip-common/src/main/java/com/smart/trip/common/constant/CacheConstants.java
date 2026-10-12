package com.smart.trip.common.constant;

import java.time.Duration;

/**
 * Redis 缓存键与生命周期常量（C-003）。
 *
 * <p>对齐 {@code .harness/wiki/数据模型.md} §4 Redis Key Schema 与 ADR-006：
 * 统一前缀 {@code trip:}，所有 Key 默认 TTL 24h，用户数据仅存 Redis + 内存、不落盘。
 * 键的具体拼装见 {@link CacheKeys}，本类只承载常量真相源，禁止散落魔法值。</p>
 */
public final class CacheConstants {

    /** 统一 Key 前缀（数据模型.md §4）。 */
    public static final String KEY_PREFIX = "trip:";

    /** Key 段分隔符。 */
    public static final String SEPARATOR = ":";

    /** 默认 TTL 小时数（ADR-006：短期状态 24h）。 */
    public static final long DEFAULT_TTL_HOURS = 24L;

    /** 默认 TTL（由 {@link #DEFAULT_TTL_HOURS} 派生）。 */
    public static final Duration DEFAULT_TTL = Duration.ofHours(DEFAULT_TTL_HOURS);

    /** 行程方案域段：{@code trip:plan:{planId}}。 */
    public static final String DOMAIN_PLAN = "plan";

    /** 会话上下文域段：{@code trip:session:{sessionId}}。 */
    public static final String DOMAIN_SESSION = "session";

    /** PlanNotebook 中间步骤域段（断点续传）：{@code trip:step:{planId}}。 */
    public static final String DOMAIN_STEP = "step";

    /** 用户偏好域段：{@code trip:pref:{userId}}。 */
    public static final String DOMAIN_PREF = "pref";

    /** 小红书笔记缓存域段：{@code trip:xhs:note:{noteId}}。 */
    public static final String DOMAIN_XHS_NOTE = "xhs:note";

    /** 限频计数域段：{@code trip:ratelimit:{api}:{window}}。 */
    public static final String DOMAIN_RATELIMIT = "ratelimit";

    /** 子 Agent 产出物缓存域段：{@code trip:agent:result:{planId}:{agent}}。 */
    public static final String DOMAIN_AGENT_RESULT = "agent:result";

    /** 方案状态后缀：{@code trip:plan:{planId}:status}。 */
    public static final String STATUS_SUFFIX = "status";

    /** 方案关联用户后缀：{@code trip:plan:{planId}:userId}。 */
    public static final String USER_ID_SUFFIX = "userId";

    /** 认证 accessToken 域段：{@code auth:access:{userId}}，TTL 2h（C-011 AC-2）。 */
    public static final String DOMAIN_AUTH_ACCESS = "auth:access";

    /** 认证 refreshToken 域段：{@code auth:refresh:{userId}}，TTL 7d（C-011 AC-2）。 */
    public static final String DOMAIN_AUTH_REFRESH = "auth:refresh";

    /** 全局标签缓存域段：{@code trip:tags:all}。 */
    public static final String DOMAIN_TAGS = "tags";

    /** 全局后缀（表示所有数据）：{@code :all}。 */
    public static final String ALL_SUFFIX = "all";

    /** 用户域段：{@code trip:user:{userId}:plans}（Sorted Set，score=epoch millis）。 */
    public static final String DOMAIN_USER = "user";

    /** 方案摘要后缀：{@code trip:plan:{planId}:summary}。 */
    public static final String SUMMARY_SUFFIX = "summary";

    /** 请求指纹域段：{@code trip:req:fingerprint:{hash}}，用于相同参数命中缓存。 */
    public static final String DOMAIN_REQ_FINGERPRINT = "req:fingerprint";

    /** 计划草案后缀：{@code trip:plan:{planId}:draft}（C-018 AC-3，草案 Markdown）。 */
    public static final String DRAFT_SUFFIX = "draft";

    /** 历史索引 TTL（30 天）——超过方案详情 24h TTL，保留可搜索的摘要信息。 */
    public static final Duration HISTORY_TTL = Duration.ofDays(30);

    /** accessToken TTL（C-011 AC-2：2 小时）。 */
    public static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(2);

    /** refreshToken TTL（C-011 AC-2：7 天）。 */
    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private CacheConstants() {
    }
}
