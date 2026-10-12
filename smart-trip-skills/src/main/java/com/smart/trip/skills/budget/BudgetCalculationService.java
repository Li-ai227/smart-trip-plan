package com.smart.trip.skills.budget;

import com.smart.trip.common.dto.TripPlanRequest;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.model.Meal;
import com.smart.trip.common.model.Route;
import com.smart.trip.common.model.TripDay;
import com.smart.trip.skills.itinerary.ItineraryDesignResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 费用核算确定性主逻辑（C-009 AC-3~AC-9）。
 *
 * <p>四类费用拆分（交通/住宿/餐饮/门票）、BigDecimal 精确核算、overrunRate 红线判定（含等号 0.15）、
 * 逐日对账一致性、缺失项保守估算降级。金额一律 BigDecimal，禁用 double。</p>
 */
public final class BudgetCalculationService {

    private static final int OVERRUN_SCALE = 10;

    /**
     * 执行费用核算。
     *
     * @param query 核算请求（含 TripPlanRequest + ItineraryDesignResult），非 null
     * @return 核算结果
     * @throws InvalidRequestException 当 request/itinerary/budget 非法时
     */
    public BudgetCalculationResult calculate(BudgetCalculationQuery query) {
        ValidatedInput input = validateAndExtract(query);
        if (input.tripDays.isEmpty()) {
            return emptyResult();
        }
        Accumulator acc = accumulateCosts(input);
        return buildResult(acc, input.budget);
    }

    private static ValidatedInput validateAndExtract(BudgetCalculationQuery query) {
        TripPlanRequest request = query.request();
        ItineraryDesignResult itinerary = query.itineraryResult();
        if (request == null) {
            throw new InvalidRequestException("BudgetCalculationService: request 不可为 null");
        }
        if (itinerary == null) {
            throw new InvalidRequestException("BudgetCalculationService: itineraryResult 不可为 null");
        }
        BigDecimal budget = request.budget();
        if (budget == null || budget.signum() <= 0) {
            throw new InvalidRequestException("budget 必须 > 0: " + budget);
        }
        int headcount = Math.max(request.headcount(), 1);
        int days = request.days();
        List<TripDay> tripDays = itinerary.days() != null ? itinerary.days() : List.of();
        return new ValidatedInput(budget, headcount, days, tripDays);
    }

    /**
     * 费用累加主逻辑
     * @param input
     * @return
     */
    private static Accumulator accumulateCosts(ValidatedInput input) {
        // 住宿：headcount × days × 200（一律保守估算，无酒店数据源）
        BigDecimal totalHotel = BudgetDefaults.HOTEL_UNIT_PRICE
                .multiply(BigDecimal.valueOf(input.headcount))
                .multiply(BigDecimal.valueOf(input.days));
        int fallbackCount = 1; // 住宿一律记为 fallback

        BigDecimal totalTicket = BigDecimal.ZERO;
        BigDecimal totalFood = BigDecimal.ZERO;
        BigDecimal totalTransport = BigDecimal.ZERO;
        List<DailyCostDetail> dailyDetails = new ArrayList<>(input.tripDays.size());

        for (TripDay day : input.tripDays) {
            // 门票：Σ attraction.ticketPrice()（null 计 ZERO，不标记 fallback）
            BigDecimal dayTicket = computeDayTicket(day);
            // 餐饮：Σ meal.avgPrice() × headcount（null 时计 30/人·餐 + fallback=true）
            DayFoodResult foodResult = computeDayFood(day, input.headcount);
            // 交通：Σ route.distanceKm() × 1 元/km（无路线时计 100/天 + fallback=true）
            DayTransportResult transportResult = computeDayTransport(day);
            BigDecimal dayHotel = BudgetDefaults.HOTEL_UNIT_PRICE
                    .multiply(BigDecimal.valueOf(input.headcount));

            totalTicket = totalTicket.add(dayTicket);
            totalFood = totalFood.add(foodResult.cost);
            totalTransport = totalTransport.add(transportResult.cost);
            if (foodResult.fallback) {
                fallbackCount++;
            }
            if (transportResult.fallback) {
                fallbackCount++;
            }

            BigDecimal dailyTotal = dayTicket.add(foodResult.cost)
                    .add(transportResult.cost).add(dayHotel);
            dailyDetails.add(new DailyCostDetail(
                    day.dayIndex(), dayTicket, foodResult.cost,
                    transportResult.cost, dayHotel, dailyTotal));
        }

        BigDecimal totalCost = totalTicket.add(totalFood).add(totalTransport).add(totalHotel);
        return new Accumulator(totalCost, totalTransport, totalHotel, totalFood,
                totalTicket, dailyDetails, fallbackCount);
    }

    /**
     * HITL 预算红线判定
     * @param acc
     * @param budget
     * @return
     */
    private static BudgetCalculationResult buildResult(Accumulator acc, BigDecimal budget) {
        // overrunRate = (totalCost - budget) / budget，保留10位标度后 compareTo
        BigDecimal overrunRate = acc.totalCost.subtract(budget)
                .divide(budget, OVERRUN_SCALE, RoundingMode.HALF_UP);
        // 红线：overrunRate >= 0.15（含等号，用 BigDecimal.compareTo）
        boolean requiresHITL = overrunRate.compareTo(BudgetDefaults.OVERRUN_THRESHOLD) >= 0;

        String hitlReason = null;
        List<String> advice = List.of();
        if (requiresHITL) {
            hitlReason = buildHitlReason(acc, overrunRate);
            advice = buildAdvice(acc); // 按最高费用类别给出调整建议
        }

        return new BudgetCalculationResult(
                acc.totalCost, acc.totalTransport, acc.totalHotel, acc.totalFood, acc.totalTicket,
                overrunRate, acc.dailyDetails, requiresHITL, hitlReason, advice,
                acc.fallbackCount > 0,
                new BudgetCalculationResult.Telemetry(acc.fallbackCount, requiresHITL));
    }

    private static BudgetCalculationResult emptyResult() {
        return new BudgetCalculationResult(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), false, null, List.of(), false,
                new BudgetCalculationResult.Telemetry(0, false));
    }

    private static BigDecimal computeDayTicket(TripDay day) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Attraction a : day.attractions()) {
            BigDecimal price = a.ticketPrice();
            if (price != null) {
                sum = sum.add(price);
            }
        }
        return sum;
    }

    private static DayFoodResult computeDayFood(TripDay day, int headcount) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean fallback = false;
        for (Meal meal : day.meals()) {
            BigDecimal avgPrice = meal.avgPrice();
            if (avgPrice != null) {
                sum = sum.add(avgPrice.multiply(BigDecimal.valueOf(headcount)));
            } else {
                sum = sum.add(BudgetDefaults.MEAL_FALLBACK_PRICE
                        .multiply(BigDecimal.valueOf(headcount)));
                fallback = true;
            }
        }
        return new DayFoodResult(sum, fallback);
    }

    private static DayTransportResult computeDayTransport(TripDay day) {
        List<Route> routes = day.routes();
        if (routes == null || routes.isEmpty()) {
            return new DayTransportResult(BudgetDefaults.TRANSPORT_DAILY_FALLBACK, true);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Route route : routes) {
            sum = sum.add(BudgetDefaults.TRANSPORT_COST_PER_KM
                    .multiply(BigDecimal.valueOf(route.distanceKm())));
        }
        return new DayTransportResult(sum, false);
    }

    private static String buildHitlReason(Accumulator acc, BigDecimal overrunRate) {
        String maxCategory = findMaxCategory(acc);
        return "超支率 " + overrunRate.setScale(4, RoundingMode.HALF_UP).toPlainString()
                + "（≥15%），" + maxCategory + "费用占比最高";
    }

    private static List<String> buildAdvice(Accumulator acc) {
        List<String> advice = new ArrayList<>(3);
        String maxCategory = findMaxCategory(acc);
        switch (maxCategory) {
            case "住宿" -> advice.add("建议降档住宿标准");
            case "门票" -> advice.add("建议减少收费景点或选择免费替代");
            case "交通" -> advice.add("建议换用公共交通或拼车方式");
            case "餐饮" -> advice.add("建议选择性价比更高的餐厅");
            default -> advice.add("建议整体缩减行程规模");
        }
        if (!"门票".equals(maxCategory) && acc.totalTicket.signum() > 0) {
            advice.add("可考虑减少收费景点");
        }
        if (!"住宿".equals(maxCategory)) {
            advice.add("可考虑降档住宿");
        }
        return List.copyOf(advice);
    }

    private static String findMaxCategory(Accumulator acc) {
        BigDecimal max = acc.totalTicket;
        String category = "门票";
        if (acc.totalFood.compareTo(max) > 0) {
            max = acc.totalFood;
            category = "餐饮";
        }
        if (acc.totalTransport.compareTo(max) > 0) {
            max = acc.totalTransport;
            category = "交通";
        }
        if (acc.totalHotel.compareTo(max) > 0) {
            category = "住宿";
        }
        return category;
    }

    private record ValidatedInput(BigDecimal budget, int headcount, int days,
                                  List<TripDay> tripDays) {
    }

    private record Accumulator(BigDecimal totalCost, BigDecimal totalTransport,
                               BigDecimal totalHotel, BigDecimal totalFood,
                               BigDecimal totalTicket, List<DailyCostDetail> dailyDetails,
                               int fallbackCount) {
    }

    private record DayFoodResult(BigDecimal cost, boolean fallback) {
    }

    private record DayTransportResult(BigDecimal cost, boolean fallback) {
    }
}
