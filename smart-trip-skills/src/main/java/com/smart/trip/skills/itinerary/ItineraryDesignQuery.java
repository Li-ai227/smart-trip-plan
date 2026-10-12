package com.smart.trip.skills.itinerary;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.util.CollectionUtils;
import com.smart.trip.skills.route.RoutePlanResult;
import java.util.List;

/**
 * 行程编排请求（C-008 AC-1 / AC-3，对齐 {@code TASK_ASSIGN(taskType=ITINERARY_DESIGN)} 的 {@code context}）。
 *
 * @param request              用户规划请求（含目的地、天数、风格等），非 null
 * @param candidateAttractions 候选景点列表（C-006 产出），可空（归一为空列表）
 * @param routeResult          路线规划结果（C-007 产出），可 null（无路线时按景点顺序编排）
 */
public record ItineraryDesignQuery(
        TripPlanRequest request,
        List<Attraction> candidateAttractions,
        RoutePlanResult routeResult) {

    /**
     * 紧凑构造器：校验 request 非 null，候选列表 null 安全不可变拷贝。
     */
    public ItineraryDesignQuery {
        if (request == null) {
            throw new InvalidRequestException("ItineraryDesignQuery request 不可为 null");
        }
        candidateAttractions = CollectionUtils.safeCopy(candidateAttractions);
    }
}
