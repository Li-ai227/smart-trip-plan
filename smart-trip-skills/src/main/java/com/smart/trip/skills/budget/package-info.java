/**
 * 费用核算业务 Skill（C-009 增量 A）：四类费用拆分、BigDecimal 精确核算、HITL 判定。
 *
 * <p>设计约束：金额一律 {@code BigDecimal}，禁用 {@code double}；预算红线与 HITL 触发
 * 在本 Skill 内部判定，全局复核与对外暴露由 Gate / Server 承接。</p>
 */
package com.smart.trip.skills.budget;
