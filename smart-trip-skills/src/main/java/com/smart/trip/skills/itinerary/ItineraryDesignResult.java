package com.smart.trip.skills.itinerary;

import com.smart.trip.common.model.TripDay;
import com.smart.trip.common.util.CollectionUtils;
import java.util.List;

/**
 * 行程编排结果（C-008 AC-2），对齐 C-006 {@code XHSAnalysisResult} / C-007 {@code RoutePlanResult} 模式。
 *
 * <p>由 {@link ItineraryDesignService} 产出、经 ItineraryAgent 装配为 {@code TASK_RESULT}：
 * {@code days} 为按日期排序的行程列表；{@code risks} 为编排风险清单；{@code fallback=true}
 * 表示走了降级逻辑（餐饮兜底/路线缺失/候选不足）。</p>
 *
 * @param days      逐日行程（不可变）
 * @param risks     风险清单（不可变）
 * @param fallback  是否为降级产出
 * @param message   可读说明，可 null
 * @param telemetry 可观测遥测
 */
public record ItineraryDesignResult(
        List<TripDay> days,
        List<ItineraryRisk> risks,
        boolean fallback,
        String message,
        Telemetry telemetry) {

    /**
     * 紧凑构造器：集合 null 安全不可变拷贝，遥测缺省归一。
     */
    public ItineraryDesignResult {
        days = CollectionUtils.safeCopy(days);
        risks = CollectionUtils.safeCopy(risks);
        telemetry = telemetry == null ? Telemetry.none() : telemetry;
    }

    /**
     * 可观测遥测载荷（C-008 AC-7）。
     *
     * @param mealFallbackCount 餐饮降级次数
     * @param lowRatingRemoved  低评分景点剔除数
     */
    public record Telemetry(int mealFallbackCount, int lowRatingRemoved) {

        /**
         * 无遥测信号。
         *
         * @return 缺省遥测
         */
        public static Telemetry none() {
            return new Telemetry(0, 0);
        }
    }
}
