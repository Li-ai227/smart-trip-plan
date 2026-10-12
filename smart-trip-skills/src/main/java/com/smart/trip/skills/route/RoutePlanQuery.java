package com.smart.trip.skills.route;

import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.util.CollectionUtils;
import java.util.List;

/**
 * 路线规划请求（C-007 AC-3，对齐 {@code TASK_ASSIGN(taskType=ROUTE_PLANNING)} 的 {@code context}）。
 *
 * @param destination    目的地（非空白）
 * @param attractions    待规划景点列表（按访问序），可空（归一为空列表）
 * @param transportMode  偏好交通方式（可空，缺省"驾车"）
 */
public record RoutePlanQuery(
        String destination,
        List<Attraction> attractions,
        String transportMode) {

    /** 离线估算缺省交通方式。 */
    public static final String DEFAULT_TRANSPORT_MODE = "驾车";

    /**
     * 紧凑构造器：校验目的地非空白，景点列表 null 安全不可变拷贝。
     */
    public RoutePlanQuery {
        if (destination == null || destination.isBlank()) {
            throw new InvalidRequestException("RoutePlanQuery destination 不可为空白");
        }
        attractions = CollectionUtils.safeCopy(attractions);
        if (transportMode == null || transportMode.isBlank()) {
            transportMode = DEFAULT_TRANSPORT_MODE;
        }
    }

    /**
     * 便捷构造：仅目的地 + 景点列表，交通方式缺省。
     *
     * @param destination 目的地，非空白
     * @param attractions 景点列表
     * @return 路线规划请求
     */
    public static RoutePlanQuery of(String destination, List<Attraction> attractions) {
        return new RoutePlanQuery(destination, attractions, null);
    }
}
