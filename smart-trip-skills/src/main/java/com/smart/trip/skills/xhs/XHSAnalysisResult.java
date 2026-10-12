package com.smart.trip.skills.xhs;

import com.smart.trip.common.util.CollectionUtils;
import java.util.List;

/**
 * 小红书笔记分析结果（C-006 AC-2/AC-6）。
 *
 * <p>由 {@link XHSNoteAnalyzer} 产出、经 XHSAnalysisAgent 装配为 {@code TASK_RESULT}：
 * {@code candidates} 为按提及频次降序的候选景点（已满足 {@code rating ≥ 3.5} 与负面情感剔除）；
 * {@code fallback=true} 表示走了缓存/默认降级或无可用笔记（接口协议.md §2.2/§2.3，不得静默移除）；
 * {@code message} 为可读说明（如「无可用笔记」「候选质量不足」），供日志/可观测使用。</p>
 *
 * @param candidates 候选景点列表（不可变；可空时归一为空列表）
 * @param fallback   是否为降级产出
 * @param message    可读说明，可空（正常充足时为 null）
 * @param telemetry  可观测遥测（限频命中 + 缓存访问结果），不可为 null
 */
public record XHSAnalysisResult(
        List<AttractionCandidate> candidates,
        boolean fallback,
        String message,
        Telemetry telemetry) {

    /**
     * 紧凑构造器：候选列表 null 安全不可变拷贝，遥测缺省归一（决策 D-5/D-6）。
     */
    public XHSAnalysisResult {
        candidates = CollectionUtils.safeCopy(candidates);
        telemetry = telemetry == null ? Telemetry.none() : telemetry;
    }

    /**
     * 可观测遥测载荷（C-006 AC-7）：供 XHSAnalysisAgent 派发 {@code api.rate_limit.hit} 与
     * {@code memory.cache.hit}（命中率）指标，不影响业务候选结果。
     *
     * @param rateLimited  是否命中限频（短路未调用检索源）
     * @param cacheOutcome 缓存访问结果（未咨询 / 命中 / 未命中）
     */
    public record Telemetry(boolean rateLimited, CacheOutcome cacheOutcome) {

        /**
         * 紧凑构造器：缓存结果缺省归一为 {@link CacheOutcome#NOT_CONSULTED}。
         */
        public Telemetry {
            cacheOutcome = cacheOutcome == null ? CacheOutcome.NOT_CONSULTED : cacheOutcome;
        }

        /**
         * 无遥测信号（未限频、未咨询缓存）。
         *
         * @return 缺省遥测
         */
        public static Telemetry none() {
            return new Telemetry(false, CacheOutcome.NOT_CONSULTED);
        }
    }
}
