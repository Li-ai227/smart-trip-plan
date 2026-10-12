/**
 * {@code xhs-note-analysis} 业务 Skill：小红书笔记检索、解析与候选景点聚合（C-006）。
 *
 * <p>跨 Agent 可复用的无状态能力（工程结构.md §2.1 / R2，禁依赖任何 Agent）。端口（{@link
 * com.smart.trip.skills.xhs.XHSNoteSource} 检索 / {@link com.smart.trip.skills.xhs.XHSNoteCache}
 * 缓存 / {@link com.smart.trip.skills.xhs.AttractionCatalog} 景点目录）抽象外部依赖，主链路用
 * 桩实现验证契约，真实 HTTP/Redis/ReMe 适配延后接入。外部调用统一经
 * {@link com.smart.trip.skills.governance.GovernedExternalCaller} 施加限频/重试/降级。</p>
 */
package com.smart.trip.skills.xhs;
