package com.smart.trip.skills.governance;

/**
 * 受治理外部调用的结果包装（C-005 AC-4/AC-5）。
 *
 * <p>{@code fallback=true} 表示结果来自降级路径（重试耗尽后的默认值），上层据此在
 * {@code TASK_RESULT} 中显式标记 {@code fallback}（接口协议.md §2.2/§2.3，不得静默移除）。</p>
 *
 * @param value    调用结果或降级默认值，可空
 * @param fallback 是否为降级产出
 * @param <T>      结果类型
 */
public record CallResult<T>(T value, boolean fallback) {
}
