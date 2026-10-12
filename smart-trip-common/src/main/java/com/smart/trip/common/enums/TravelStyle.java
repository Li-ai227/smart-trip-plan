package com.smart.trip.common.enums;

/**
 * 旅行风格（数据模型.md §3 / 业务模型.md §5），驱动个性化偏好。
 */
public enum TravelStyle {

    /** 亲子：节奏慢、亲子景点、安全餐饮。 */
    FAMILY,

    /** 情侣：浪漫景点、氛围餐厅、适度自由。 */
    COUPLE,

    /** 独自：灵活、深度、性价比。 */
    SOLO,

    /** 团建：集体活动、大容量餐厅、统一节奏。 */
    TEAM
}
