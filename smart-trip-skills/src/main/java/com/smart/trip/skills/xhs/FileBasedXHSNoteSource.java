package com.smart.trip.skills.xhs;

import com.smart.trip.common.enums.SentimentType;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.AttractionMention;
import com.smart.trip.common.model.XHSNote;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于 {@link AttractionCatalog} 的确定性小红书笔记源（C-012-D）。
 *
 * <p>当无 XHS API key 时，作为离线降级方案，根据 {@link AttractionCatalog} 中查询到的景点数据
 * 生成合成笔记，使下游 {@link XHSNoteAnalyzer} 能正常执行完整链路（检索→聚合→筛选→排序）。</p>
 *
 * <p>生成规则：对已知目的地城市，生成 4–12 条含景点提及的合成笔记（情感以 POSITIVE 为主），
 * 通过 {@link XHSNoteAnalyzer#analyze} 的评分阈值（≥3.5）和负面剔除（≤50%）不变量。</p>
 *
 * <p>参数设计（适配 150 城市 × 50 景点/城市 的知识库规模）：</p>
 * <ul>
 *   <li>{@code SELECT_CUTOFF = 25}：取评分最高的 25 个景点作为精选池（覆盖半数景点）</li>
 *   <li>{@code NOTE_MIN = 4, NOTE_MAX = 12}：笔记数量随精选池大小动态调整（小城市 4 条，大城市 12 条）</li>
 *   <li>{@code MENTION_MIN = 6, MENTION_MAX = 10}：每条笔记覆盖 6–10 个景点，确保 25 个精选池通过多条笔记轮选全部覆盖</li>
 * </ul>
 *
 * <p>未知目的地返回空列表（降级行为同现有 {@code query -> List.of()}）。</p>
 */
public final class FileBasedXHSNoteSource implements XHSNoteSource {

    private static final Logger LOG = Logger.getLogger(FileBasedXHSNoteSource.class.getName());

    /** 精选池上限：按评分取前 N 个景点参与笔记生成（适配 50 景点/城市，取评分前 50%）。 */
    private static final int SELECT_CUTOFF = 25;

    /** 笔记数量下限（适配景点较少的城市，至少 4 条笔记覆盖精选池半数以上）。 */
    private static final int NOTE_MIN = 4;

    /** 笔记数量上限（适配 50 景点的城市，与精选池配合实现全覆盖）。 */
    private static final int NOTE_MAX = 12;

    /** 每条笔记最少景点提及数（确保单条笔记有足够信息量）。 */
    private static final int MENTION_MIN = 6;

    /** 每条笔记最多景点提及数（避免单条笔记过长）。 */
    private static final int MENTION_MAX = 10;

    /** 笔记标题模板池（数量 ≥ NOTE_MAX，避免重复）。 */
    private static final String[] TITLE_TEMPLATES = {
            "%s热门景点推荐",
            "%s必去打卡攻略",
            "%s深度游指南",
            "%s旅游全攻略",
            "%s经典路线推荐",
            "%s周末短途游",
            "%s亲子游攻略",
            "%s穷游精华",
            "%s两天一夜行程",
            "%s自驾游路线",
            "%s拍照打卡指南",
            "%s美食美景攻略"
    };

    private final AttractionCatalog catalog;

    /**
     * @param catalog 景点目录（用于按目的地查找景点），非 null
     */
    public FileBasedXHSNoteSource(AttractionCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("FileBasedXHSNoteSource: catalog 不可为 null");
        }
        this.catalog = catalog;
    }

    @Override
    public List<XHSNote> search(XHSAnalysisQuery query) throws Exception {
        String destination = query.destination();
        if (destination == null || destination.isBlank()) {
            return List.of();
        }

        LOG.log(Level.INFO, "FileBasedXHSNoteSource: 开始搜索目的地「{0}」的笔记", destination);
        List<Attraction> cityAttractions = findCityAttractions(destination);
        LOG.log(Level.INFO, "FileBasedXHSNoteSource: 目的地「{0}」找到 {1} 个景点",
                new Object[]{destination, cityAttractions.size()});
        if (cityAttractions.isEmpty()) {
            LOG.log(Level.INFO, "FileBasedXHSNoteSource: 未知目的地「{0}」，返回空笔记", destination);
            return List.of();
        }

        // 按评分从高到低排序，取前 N 个景点作为精选池（避免笔记过长）
        List<Attraction> selected = cityAttractions.stream()
                .sorted((a, b) -> Double.compare(b.rating(), a.rating()))
                .limit(SELECT_CUTOFF)
                .toList();

        // 生成 4–12 条笔记：小城市（selected ≤ NOTE_MIN×2）保底 NOTE_MIN 条，
        // 大城市按比例递增，上限 NOTE_MAX。
        // 例如：selected=6 → 6/2=3 → max(4,3)=4 条；selected=25 → 25/2=12 → min(12,12)=12 条
        int noteCount = Math.min(NOTE_MAX, Math.max(NOTE_MIN, selected.size() / 2));
        List<XHSNote> notes = new ArrayList<>(noteCount);

        for (int i = 0; i < noteCount; i++) {
            String title = String.format(TITLE_TEMPLATES[i % TITLE_TEMPLATES.length], destination);
            String noteId = destination.hashCode() + "-note-" + i;

            // 每条笔记的景点提及数：6–8 个（小城市）→ 8–10 个（大城市），
            // 确保多条笔记轮选能覆盖 selected 池中的所有景点。
            // 例如：selected=25, noteCount=12 → mentionCount=ceil(25/12)=3 → clamp(6,10,3)=6
            // → 12 条笔记 × 6 个景点 = 72 次轮选，25 个精选池全覆盖近 3 轮
            int mentionCount = Math.min(MENTION_MAX, Math.max(MENTION_MIN,
                    (int) Math.ceil((double) selected.size() / noteCount)));
            List<AttractionMention> mentions = new ArrayList<>(mentionCount);
            for (int j = 0; j < mentionCount; j++) {
                int idx = (i * mentionCount + j) % selected.size();
                Attraction attr = selected.get(idx);
                // 情感分配：80% POSITIVE, 20% NEUTRAL，确保 negativeRatio < 0.5
                SentimentType sentiment = (i + j) % 5 == 0
                        ? SentimentType.NEUTRAL
                        : SentimentType.POSITIVE;
                mentions.add(new AttractionMention(attr.attractionId(), sentiment));
            }

            // likeCount 基于景点评分生成（高评分→高点赞）
            int likeCount = 500 + (int) (selected.get(0).rating() * 2000) - (i * 200);

            XHSNote note = new XHSNote(
                    noteId,
                    title,
                    destination + "旅游攻略正文，包含热门景点介绍和实用建议。",
                    List.of("旅游", destination),
                    Math.max(100, likeCount),
                    mentions);
            notes.add(note);
        }

        LOG.log(Level.INFO, "FileBasedXHSNoteSource: 为「{0}」生成 {1} 条笔记",
                new Object[]{destination, notes.size()});
        return notes;
    }

    private List<Attraction> findCityAttractions(String destination) {
        if (catalog instanceof FileBasedAttractionCatalog fac) {
            return fac.findMultiCity(destination);
        }
        return catalog.findByCity(destination);
    }
}
