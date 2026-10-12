package com.smart.trip.skills.persistence;

import com.smart.trip.common.enums.TravelStyle;
import java.time.LocalDate;

/**
 * 历史规划查询参数（C-017 AC-3 / DECISION-2）。
 *
 * <p>分页 + 多维筛选：关键词（目的地模糊搜索）、旅行风格、终态、日期范围。
 * 紧凑构造器自动归一分页参数（page &ge; 1、pageSize ∈ [1, 100]）。</p>
 *
 * @param userId      用户 ID（必填）
 * @param page        页码（从 1 开始，&lt; 1 归为 1）
 * @param pageSize    每页条数（&lt; 1 归为 10，上限 100）
 * @param keyword     关键词（目的地模糊匹配，可空）
 * @param travelStyle 旅行风格筛选（可空）
 * @param planStatus  终态筛选（confirmed / failed，可空）
 * @param startDate   起始日期下限（含，可空）
 * @param endDate     起始日期上限（含，可空）
 */
public record PlanHistoryQuery(
        String userId,
        int page,
        int pageSize,
        String keyword,
        TravelStyle travelStyle,
        String planStatus,
        LocalDate startDate,
        LocalDate endDate) {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 紧凑构造器：分页参数归一。
     */
    public PlanHistoryQuery {
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        } else if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
    }

    /**
     * 计算 SQL OFFSET。
     *
     * @return 偏移量
     */
    public int offset() {
        return (page - 1) * pageSize;
    }
}
