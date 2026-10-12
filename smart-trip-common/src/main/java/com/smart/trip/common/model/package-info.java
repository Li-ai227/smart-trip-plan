/**
 * 公共领域模型（C-004）。
 *
 * <p>落地 {@code .harness/wiki/数据模型.md} §2 的全部领域模型，均为不可变 record（决策 D-1）：
 * {@code GeoLocation / Attraction / Meal / Route / BudgetBreakdown / Budget / TripDay / TripPlan}。
 * 模型仅承载字段契约；业务不变量阈值校验归对应 Agent / OutputQualityGate。</p>
 */
package com.smart.trip.common.model;
