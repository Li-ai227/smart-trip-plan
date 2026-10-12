/**
 * 地图厂商 HTTP 适配层（C-007 增量 B / 工程结构 §4）。
 *
 * <p>百度 + 高德双厂商真实 REST API 适配，实现 {@code MapGeocoder}/{@code MapRouter}/{@code MapTraffic}
 * 端口，并提供 failover 智能切换（AC-9）与进程内坐标 TTL 缓存（AC-8）。</p>
 */
package com.smart.trip.agent.route.mcp;
