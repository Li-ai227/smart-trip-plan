package com.smart.trip.common.dto;

import com.smart.trip.common.enums.TravelStyle;
import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.util.CollectionUtils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 用户规划请求（数据模型.md §2.1 / 接口协议.md §1.1）。
 *
 * <p>字段上的 {@code jakarta.validation} 注解为对外声明契约，由 server 层 {@code @Valid} 生效（C-011）；
 * common 不引入校验运行时。{@link #validate()} 提供可独立运行的显式自校验，违例抛
 * {@link InvalidRequestException}（码 {@code INVALID_REQUEST}），见决策 D-2。</p>
 *
 * @param destination 目的地，非空白
 * @param startDate   起始日期，非空
 * @param days        天数，取值 [1, 30]
 * @param budget      总预算（元），> 0
 * @param headcount   人数，≥ 1
 * @param travelStyle 旅行风格，可空
 * @param preferences 偏好标签，可空（构造时归一为不可变空列表）
 * @param planMode    是否开启计划模式（C-018 AC-1），默认 {@code false}；开启后 Supervisor
 *                    先只读起草执行计划并等待人工确认，再委派子 Agent
 */
public record TripPlanRequest(
        @NotBlank String destination,
        @NotNull LocalDate startDate,
        @Min(1) @Max(30) int days,
        @NotNull @Positive BigDecimal budget,
        @Min(1) int headcount,
        TravelStyle travelStyle,
        List<String> preferences,
        Boolean planMode) {

    /** 天数下界（含）。 */
    private static final int MIN_DAYS = 1;

    /** 天数上界（含）。 */
    private static final int MAX_DAYS = 30;

    /** 人数下界（含）。 */
    private static final int MIN_HEADCOUNT = 1;

    /**
     * 紧凑构造器：对可空集合做 null 安全的不可变拷贝（决策 D-2/D-6）。构造不做范围校验，
     * 以保证反序列化任意载荷可用、{@code preferences=null} 可正常构造。
     */
    public TripPlanRequest {
        if (planMode == null) planMode = false;
        preferences = CollectionUtils.safeCopy(preferences);
    }

    /**
     * 向后兼容构造器（C-018 前的既有 7 参调用点），{@code planMode} 默认 {@code false}。
     */
    public TripPlanRequest(String destination, LocalDate startDate, int days, BigDecimal budget,
            int headcount, TravelStyle travelStyle, List<String> preferences) {
        this(destination, startDate, days, budget, headcount, travelStyle, preferences, false);
    }

    /**
     * 显式自校验字段约束（数据模型.md §2.1）。
     *
     * @throws InvalidRequestException 当任一字段非法（目的地空白 / 日期缺失 / days 越界 / budget≤0 / headcount&lt;1）
     */
    public void validate() {
        if (destination == null || destination.isBlank()) {
            throw new InvalidRequestException("destination 不可为空");
        }
        if (startDate == null) {
            throw new InvalidRequestException("startDate 不可为空");
        }
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new InvalidRequestException("days 必须在 [" + MIN_DAYS + ", " + MAX_DAYS + "]: " + days);
        }
        if (budget == null || budget.signum() <= 0) {
            throw new InvalidRequestException("budget 必须 > 0: " + budget);
        }
        if (headcount < MIN_HEADCOUNT) {
            throw new InvalidRequestException("headcount 必须 ≥ " + MIN_HEADCOUNT + ": " + headcount);
        }
    }
}
