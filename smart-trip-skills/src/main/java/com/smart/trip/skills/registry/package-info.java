/**
 * Agent 注册发现（C-005 T-3）。
 *
 * <p>{@code AgentRegistry} 抽象注册/注销/解析/列举；{@code InMemoryAgentRegistry} 为本地实现，
 * {@code ResilientAgentRegistry} 在主注册（Nacos）不可达时降级本地缓存路由（边界 #3）。
 * {@code list()} 即 {@code /admin/agent/status} 的数据源。</p>
 */
package com.smart.trip.skills.registry;
