package com.smart.trip.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MaskUtils} 脱敏测试（C-004 安全 NFR / 编码规范 §7）。
 *
 * <p>手机号、身份证在日志与序列化路径输出掩码，不泄露完整敏感信息；空值安全（{@code null → null}）。</p>
 */
class MaskUtilsTest {

    @Test
    @DisplayName("手机号保留前 3 后 4，中间掩码")
    void maskPhoneStandard() {
        assertEquals("138****8000", MaskUtils.maskPhone("13800138000"));
    }

    @Test
    @DisplayName("身份证保留前 6 后 4，中间掩码")
    void maskIdCardStandard() {
        assertEquals("110101********1234", MaskUtils.maskIdCard("110101199001011234"));
    }

    @Test
    @DisplayName("空值安全")
    void nullSafe() {
        assertNull(MaskUtils.maskPhone(null));
        assertNull(MaskUtils.maskIdCard(null));
    }

    @Test
    @DisplayName("过短输入整体掩码，不泄露原文")
    void shortInputFullyMasked() {
        assertEquals("****", MaskUtils.maskPhone("123"));
    }
}
