/**
 * 统一外部调用治理（C-005 T-4/T-5）。
 *
 * <p>重试退避（{@code RetryPolicy}）、超时（{@code TimeoutPolicy}）、限频（{@code RateLimiter}）、
 * 降级（{@code FallbackHandler}）经 {@code GovernedExternalCaller} 编排为单一治理链；
 * {@code CallDepthGuard} 守护调用链深度 ≤5。杜绝各 Agent 各自复制治理逻辑（设计约束）。</p>
 */
package com.smart.trip.skills.governance;
