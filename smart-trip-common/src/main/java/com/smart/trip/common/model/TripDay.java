package com.smart.trip.common.model;

import com.smart.trip.common.util.CollectionUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单日行程（数据模型.md §2.3 / 业务模型.md §2）。
 *
 * <p>业务不变量（业务模型.md §4）：每日行程在 8:00–20:00 内、至少含午餐与晚餐；
 * 校验归 Itinerary / OutputQualityGate（C-008）。集合字段构造时归一为不可变拷贝。</p>
 *
 * @param date        当日日期
 * @param dayIndex    第几天（1-based）
 * @param attractions 景点（有序），可空
 * @param meals       餐饮，可空
 * @param routes      点间路线，可空
 * @param dailyCost   当日小计（元）
 */
public record TripDay(
        LocalDate date,
        int dayIndex,
        List<Attraction> attractions,
        List<Meal> meals,
        List<Route> routes,
        BigDecimal dailyCost) {

    /**
     * 紧凑构造器：对可空集合做 null 安全的不可变拷贝（决策 D-5/D-6）。
     */
    public TripDay {
        attractions = CollectionUtils.safeCopy(attractions);
        meals = CollectionUtils.safeCopy(meals);
        routes = CollectionUtils.safeCopy(routes);
    }
}
