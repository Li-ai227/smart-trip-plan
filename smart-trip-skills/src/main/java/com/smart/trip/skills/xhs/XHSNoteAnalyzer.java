package com.smart.trip.skills.xhs;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.exception.RateLimitedException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.XHSNote;
import com.smart.trip.skills.governance.CallResult;
import com.smart.trip.skills.governance.GovernedExternalCaller;
import com.smart.trip.skills.governance.RateLimiter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@code xhs-note-analysis} 业务 Skill 核心：检索 → 解析 → 候选聚合 → 筛选 → 降级（C-006 AC-3~AC-6）。
 *
 * <p>跨 Agent 可复用、无状态（工程结构.md §2.1 / R2，禁依赖任何 Agent）。统一外部调用治理委托
 * {@link GovernedExternalCaller}（限频/重试/降级），评分阈值（≥3.5）与负面情感剔除为本 Skill
 * 内部业务规则（业务模型.md §4）。候选全局一致性由下游 {@code OutputQualityGate} 复核。</p>
 *
 * <p>降级语义（接口协议.md §2.2/§2.3，{@code fallback} 不得静默移除）：检索故障/限频耗尽 → 走缓存
 * 或空默认（{@code fallback=true}）；检索为空（无可用笔记）→ 同样 {@code fallback=true}；而「有笔记
 * 但候选评分均 &lt;3.5」是业务质量结果，{@code fallback=false}，由下游质量门决定是否触发 HITL。</p>
 */
public final class XHSNoteAnalyzer {

    /** 推荐候选的最低评分阈值（业务模型.md §4，强规则）。 */
    public static final double MIN_RECOMMEND_RATING = 3.5;

    /** 负面情感剔除阈值：负面占比超过此值的候选不进入推荐池（AC-5）。 */
    static final double MAX_NEGATIVE_RATIO = 0.5;

    private static final String MSG_NO_NOTES = "无可用笔记：检索为空或降级，已返回空候选";
    private static final String MSG_LOW_QUALITY = "候选质量不足：无评分≥3.5 的景点";

    private final XHSNoteSource source;
    private final XHSNoteCache cache;
    private final AttractionCatalog catalog;
    private final GovernedExternalCaller caller;
    private final Predicate<XHSNote> admission;
    private final XHSNoteParser parser;

    /**
     * 构造分析器（默认准入：{@link SensitiveContentScreen} 敏感内容过滤，无 Parser 直通）。
     *
     * @param source  小红书检索端口
     * @param cache   笔记缓存端口（降级读取）
     * @param catalog 景点目录端口（ID→Attraction）
     * @param caller  统一外部调用治理执行器（含限频/重试/降级）
     */
    public XHSNoteAnalyzer(XHSNoteSource source, XHSNoteCache cache,
                           AttractionCatalog catalog, GovernedExternalCaller caller) {
        this(source, cache, catalog, caller, new SensitiveContentScreen(), null);
    }

    /**
     * 构造分析器并注入笔记准入断言（用于敏感/不安全内容剔除，边界 #4）。
     *
     * @param source    小红书检索端口
     * @param cache     笔记缓存端口（降级读取）
     * @param catalog   景点目录端口（ID→Attraction）
     * @param caller    统一外部调用治理执行器（含限频/重试/降级）
     * @param admission 笔记准入断言，返回 {@code false} 的笔记被剔除，不可为 null
     */
    public XHSNoteAnalyzer(XHSNoteSource source, XHSNoteCache cache, AttractionCatalog catalog,
                           GovernedExternalCaller caller, Predicate<XHSNote> admission) {
        this(source, cache, catalog, caller, admission, null);
    }

    /**
     * 构造分析器并注入准入断言与可选 Parser（增量 B / AC-10 · Source→Parser→Analyzer 链路）。
     *
     * @param source    小红书检索端口
     * @param cache     笔记缓存端口（降级读取）
     * @param catalog   景点目录端口（ID→Attraction）
     * @param caller    统一外部调用治理执行器（含限频/重试/降级）
     * @param admission 笔记准入断言，返回 {@code false} 的笔记被剔除，不可为 null
     * @param parser    笔记解析端口，可为 null（null 时笔记直通不解析，向后兼容增量 A）
     */
    public XHSNoteAnalyzer(XHSNoteSource source, XHSNoteCache cache, AttractionCatalog catalog,
                           GovernedExternalCaller caller, Predicate<XHSNote> admission,
                           XHSNoteParser parser) {
        this.source = source;
        this.cache = cache;
        this.catalog = catalog;
        this.caller = caller;
        this.admission = admission;
        this.parser = parser;
    }

    /**
     * 执行一次小红书笔记分析：检索（经统一治理）→ 聚合候选 → 评分/情感筛选 → 频次排序。
     *
     * @param query 分析请求（含目的地与偏好），非 null
     * @return 候选景点（按提及频次降序，已满足 {@code rating≥3.5} 与负面剔除）及降级标记
     */
    public XHSAnalysisResult analyze(XHSAnalysisQuery query) {
        Retrieved retrieved = retrieveNotes(query);
        List<XHSNote> notes = retrieved.notes();
        XHSAnalysisResult.Telemetry telemetry = retrieved.telemetry();

        if (notes.isEmpty()) {
            return new XHSAnalysisResult(List.of(), true, MSG_NO_NOTES, telemetry);
        }

        List<XHSNote> parsed = parseNotes(notes);
        if (parsed.isEmpty()) {
            return new XHSAnalysisResult(List.of(), true, MSG_NO_NOTES, telemetry);
        }

        List<AttractionCandidate> candidates = aggregate(parsed);
        if (candidates.isEmpty()) {
            return new XHSAnalysisResult(List.of(), retrieved.fallback(), MSG_LOW_QUALITY, telemetry);
        }
        return new XHSAnalysisResult(candidates, retrieved.fallback(), null, telemetry);
    }

    /**
     * 经统一治理检索笔记：故障/限频耗尽走缓存或空默认；限频命中绝不调用检索源（不超频）。
     */
    private Retrieved retrieveNotes(XHSAnalysisQuery query) {
        CacheProbe probe = new CacheProbe();
        try {
            CallResult<List<XHSNote>> result = caller.call(
                    RateLimiter.API_XHS,
                    () -> nullToEmpty(source.search(query)),
                    cause -> probe.read(query));
            List<XHSNote> notes = nullToEmpty(result.value());
            if (!result.fallback() && !notes.isEmpty()) {
                // 实时检索成功：以实时为准刷新缓存（边界 #5）。
                cache.put(query.destination(), notes);
            }
            return new Retrieved(notes, result.fallback(),
                    new XHSAnalysisResult.Telemetry(false, probe.outcome()));
        } catch (RateLimitedException ex) {
            // 边界 #2：命中限频 → 短路，绝不调用检索源，走缓存降级。
            List<XHSNote> notes = probe.read(query);
            return new Retrieved(notes, true,
                    new XHSAnalysisResult.Telemetry(true, probe.outcome()));
        }
    }

    /**
     * 解析笔记（增量 B / AC-10）：有 Parser 时逐笔记解析，解析失败的笔记降级跳过。
     * 无 Parser 时直通（向后兼容增量 A：笔记已含 mentions）。
     */
    private List<XHSNote> parseNotes(List<XHSNote> notes) {
        if (parser == null) {
            return notes;
        }
        List<XHSNote> parsed = new ArrayList<>();
        for (XHSNote note : notes) {
            try {
                parsed.add(parser.parse(note));
            } catch (Exception ex) {
                // AC-10 降级：解析失败的笔记降级为「无结构化提及」并跳过，不抛裸异常。
            }
        }
        return parsed;
    }

    /** 聚合笔记为候选景点：解析 ID→Attraction，应用评分阈值与负面情感剔除，按频次排序。 */
    private List<AttractionCandidate> aggregate(List<XHSNote> notes) {
        Map<String, Tally> tallies = tallyMentions(notes);
        List<AttractionCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, Tally> entry : tallies.entrySet()) {
            Optional<Attraction> resolved = catalog.findById(entry.getKey());
            if (resolved.isEmpty()) {
                continue;
            }
            Attraction attraction = resolved.get();
            if (attraction.rating() < MIN_RECOMMEND_RATING) {
                continue;
            }
            Tally tally = entry.getValue();
            AttractionCandidate candidate = new AttractionCandidate(
                    attraction, tally.total(), tally.positive, tally.neutral, tally.negative);
            if (candidate.negativeRatio() > MAX_NEGATIVE_RATIO) {
                continue;
            }
            candidates.add(candidate);
        }
        candidates.sort(byFrequencyThenQuality());
        return candidates;
    }

    /** 按提及景点聚合情感计数（逐提及情感，跳过未准入笔记与空提及，保留首现顺序）。 */
    private Map<String, Tally> tallyMentions(List<XHSNote> notes) {
        Map<String, Tally> tallies = new LinkedHashMap<>();
        for (XHSNote note : notes) {
            if (note == null || !admission.test(note) || note.mentions().isEmpty()) {
                continue;
            }
            for (AttractionMention mention : note.mentions()) {
                if (mention == null || mention.attractionId() == null
                        || mention.attractionId().isBlank()) {
                    continue;
                }
                tallies.computeIfAbsent(mention.attractionId(), k -> new Tally())
                        .add(mention.sentiment());
            }
        }
        return tallies;
    }

    /** 排序：提及频次降序 → 评分降序 → 负面占比升序（降权）→ 名称升序（稳定确定性）。 */
    private static Comparator<AttractionCandidate> byFrequencyThenQuality() {
        return Comparator.comparingInt(AttractionCandidate::mentionCount).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (AttractionCandidate c) -> c.attraction().rating()).reversed())
                .thenComparingDouble(AttractionCandidate::negativeRatio)
                .thenComparing(c -> c.attraction().name());
    }

    private static List<XHSNote> nullToEmpty(List<XHSNote> notes) {
        return notes == null ? List.of() : notes;
    }

    /** 检索产物：笔记集 + 是否降级 + 可观测遥测。 */
    private record Retrieved(List<XHSNote> notes, boolean fallback,
                            XHSAnalysisResult.Telemetry telemetry) {
    }

    /** 缓存探针：一次降级读取，记录命中/未命中以驱动命中率指标（AC-7）。 */
    private final class CacheProbe {
        private CacheOutcome outcome = CacheOutcome.NOT_CONSULTED;

        private List<XHSNote> read(XHSAnalysisQuery query) {
            List<XHSNote> notes = cache.get(query.destination()).orElse(null);
            outcome = notes == null || notes.isEmpty() ? CacheOutcome.MISS : CacheOutcome.HIT;
            return notes == null ? List.of() : notes;
        }

        private CacheOutcome outcome() {
            return outcome;
        }
    }

    /** 单景点的情感计数。 */
    private static final class Tally {
        private int positive;
        private int neutral;
        private int negative;

        private void add(SentimentType sentiment) {
            SentimentType type = sentiment == null ? SentimentType.NEUTRAL : sentiment;
            switch (type) {
                case POSITIVE -> positive++;
                case NEGATIVE -> negative++;
                default -> neutral++;
            }
        }

        private int total() {
            return positive + neutral + negative;
        }
    }
}
