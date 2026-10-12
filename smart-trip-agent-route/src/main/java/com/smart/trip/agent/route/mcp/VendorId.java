package com.smart.trip.agent.route.mcp;

/**
 * 地图厂商标识（C-007 增量 B / AC-9）。
 */
public enum VendorId {

    /** 百度地图。 */
    BAIDU("baidu"),

    /** 高德地图。 */
    AMAP("amap");

    private final String label;

    VendorId(String label) {
        this.label = label;
    }

    /**
     * @return 指标/日志用标签
     */
    public String label() {
        return label;
    }
}
