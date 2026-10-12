/**
 * 路线规划业务 Skill（{@code route-planning}，C-007）。
 *
 * <p>职责：景点间交通路线规划 + 三类降级 + 超限标记。端口化设计——
 * 地图工具以接口声明（{@link com.smart.trip.skills.route.MapGeocoder}、
 * {@link com.smart.trip.skills.route.MapRouter}、
 * {@link com.smart.trip.skills.route.MapTraffic}），桩实现作测试替身。
 * 禁止依赖任何 Agent 模块（ArchUnit R2）。</p>
 */
package com.smart.trip.skills.route;
