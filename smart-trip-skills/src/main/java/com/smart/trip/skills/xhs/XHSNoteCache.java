package com.smart.trip.skills.xhs;

import com.smart.trip.common.model.XHSNote;
import java.util.List;
import java.util.Optional;

/**
 * 小红书笔记缓存端口（C-006 AC-6 / 设计约束「端口化」）。
 *
 * <p>支撑检索故障/限频时的降级读取：以目的地为索引返回上次成功检索的笔记集
 * （{@code fallback=true}）。主链路用内存桩验证契约；真实 Redis 适配延后至 C-015，按
 * 数据模型.md §4 以 {@code trip:xhs:note:&#123;noteId&#125;}（24h TTL，{@code CacheKeys.xhsNoteKey}）
 * 逐笔记落盘 + 目的地索引。用户数据不落盘，仅存 Redis + 内存。</p>
 */
public interface XHSNoteCache {

    /**
     * 读取目的地维度缓存的笔记集（降级路径用）。
     *
     * @param destination 目的地，非空白
     * @return 命中返回缓存笔记；未命中返回 {@link Optional#empty()}
     */
    Optional<List<XHSNote>> get(String destination);

    /**
     * 写入/刷新目的地维度的笔记集（实时检索成功后回填，实时优先覆盖旧缓存）。
     *
     * @param destination 目的地，非空白
     * @param notes       本次检索得到的笔记集
     */
    void put(String destination, List<XHSNote> notes);
}
