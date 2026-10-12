package com.smart.trip.skills.xhs;

/**
 * 笔记缓存访问结果（C-006 AC-7 / 接口协议.md §6）。
 *
 * <p>用于派生 {@code memory.cache.hit.rate}：仅当检索降级真正咨询缓存时才计入命中率，
 * 实时检索成功的快路径不污染分母（{@link #NOT_CONSULTED}）。</p>
 */
public enum CacheOutcome {

    /** 实时检索成功，未咨询缓存（不计入命中率分母）。 */
    NOT_CONSULTED,

    /** 降级咨询缓存且命中（计入命中）。 */
    HIT,

    /** 降级咨询缓存但未命中（计入未命中）。 */
    MISS
}
