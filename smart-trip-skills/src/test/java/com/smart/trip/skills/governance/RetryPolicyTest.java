package com.smart.trip.skills.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RetryPolicy} 单测：3 次重试 + 指数退避 1/2/4s（C-005 AC-4 / 接口协议.md §2 调用约束）。
 */
class RetryPolicyTest {

    @Test
    @DisplayName("默认策略：3 次重试，基准退避 1s")
    void defaultPolicy() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();
        assertEquals(3, policy.maxRetries());
        assertEquals(1_000L, policy.baseBackoffMillis());
    }

    @Test
    @DisplayName("指数退避序列严格为 1/2/4s（接口协议 §2）")
    void exponentialBackoffSequence() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();
        assertEquals(1_000L, policy.backoffMillis(1));
        assertEquals(2_000L, policy.backoffMillis(2));
        assertEquals(4_000L, policy.backoffMillis(3));
        assertEquals(List.of(1_000L, 2_000L, 4_000L), policy.backoffSequence());
    }

    @Test
    @DisplayName("非法重试次数与退避基准快速失败")
    void rejectsInvalidConfig() {
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(-1, 1_000L));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(3, 0L));
    }

    @Test
    @DisplayName("退避序号越界快速失败")
    void rejectsOutOfRangeRetryIndex() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();
        assertThrows(IllegalArgumentException.class, () -> policy.backoffMillis(0));
        assertThrows(IllegalArgumentException.class, () -> policy.backoffMillis(4));
    }
}
