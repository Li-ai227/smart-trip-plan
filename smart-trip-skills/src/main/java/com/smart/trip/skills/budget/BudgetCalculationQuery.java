package com.smart.trip.skills.budget;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;

/**
 * 费用核算请求封装（C-009 T-1）。
 *
 * @param request         用户规划请求（含 budget/headcount/days/destination/travelStyle）
 * @param itineraryResult 行程编排产出（含 List&lt;TripDay&gt; 每日景点/餐饮/路线/dailyCost）
 */
public record BudgetCalculationQuery(
        TripPlanRequest request,
        ItineraryDesignResult itineraryResult) {
}
