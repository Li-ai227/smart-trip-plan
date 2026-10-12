package com.smart.trip.skills.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SensitiveDataMasker} 脱敏单测（C-010 trace 方案）。
 */
class SensitiveDataMaskerTest {

    @Nested
    @DisplayName("API Key 脱敏")
    class ApiKeyMasking {

        @Test
        @DisplayName("DeepSeek sk- 前缀 key → 替换为 ***REDACTED***")
        void deepseekKey() {
            String input = "Authorization: Bearer sk-abcdef1234567890abcdef1234567890abcdef12";
            String result = SensitiveDataMasker.mask(input);
            assertFalse(result.contains("sk-abcdef"));
            assertTrue(result.contains("***REDACTED***"));
        }

        @Test
        @DisplayName("纯 sk- key 字符串 → 替换为 ***REDACTED***")
        void bareSkKey() {
            String input = "sk-proj-1234567890abcdef1234567890abcdef1234567890abcd";
            String result = SensitiveDataMasker.mask(input);
            assertEquals("***REDACTED***", result);
        }

        @Test
        @DisplayName("环境变量格式 DEEPSEEK_API_KEY=sk-xxx → value 脱敏")
        void envVarDeepseekKey() {
            String input = "DEEPSEEK_API_KEY=sk-abcdef1234567890abcdef1234567890abcdef12";
            String result = SensitiveDataMasker.mask(input);
            assertFalse(result.contains("sk-abcdef"));
            assertTrue(result.contains("***REDACTED***"));
            assertTrue(result.startsWith("DEEPSEEK_API_KEY="));
        }

        @Test
        @DisplayName("环境变量格式 DASHSCOPE_API_KEY=sk-xxx → value 脱敏")
        void envVarDashscopeKey() {
            String input = "DASHSCOPE_API_KEY=sk-dash1234567890abcdef1234567890abcdef123456";
            String result = SensitiveDataMasker.mask(input);
            assertFalse(result.contains("sk-dash"));
            assertTrue(result.contains("***REDACTED***"));
        }

        @Test
        @DisplayName("JSON apiKey 字段 → value 脱敏，key 保留")
        void jsonApiKeyField() {
            String input = "{\"apiKey\": \"sk-abcdef1234567890abcdef1234567890abcdef12\"}";
            String result = SensitiveDataMasker.mask(input);
            assertFalse(result.contains("sk-abcdef"));
            assertTrue(result.contains("***REDACTED***"));
            assertTrue(result.contains("\"apiKey\""));
        }

        @Test
        @DisplayName("JSON api_key 字段 → value 脱敏")
        void jsonApiKeyUnderscore() {
            String input = "{\"api_key\": \"sk-abcdef1234567890abcdef1234567890abcdef12\"}";
            String result = SensitiveDataMasker.mask(input);
            assertFalse(result.contains("sk-abcdef"));
            assertTrue(result.contains("***REDACTED***"));
        }
    }

    @Nested
    @DisplayName("手机号脱敏")
    class PhoneMasking {

        @Test
        @DisplayName("手机号中间 4 位替换为 ****")
        void phoneInText() {
            String input = "联系人手机号是13812345678，请联系";
            String result = SensitiveDataMasker.mask(input);
            assertFalse(result.contains("13812345678"));
            assertTrue(result.contains("138****5678"));
        }

        @Test
        @DisplayName("多个手机号均脱敏")
        void multiplePhones() {
            String input = "手机1:13912341234 手机2:18687654321";
            String result = SensitiveDataMasker.mask(input);
            assertFalse(result.contains("13912341234"));
            assertFalse(result.contains("18687654321"));
            assertTrue(result.contains("139****1234"));
            assertTrue(result.contains("186****4321"));
        }

        @Test
        @DisplayName("非手机号数字不误脱敏")
        void nonPhoneNumber() {
            String input = "预算 8000 元，景点编号 12345678901234";
            String result = SensitiveDataMasker.mask(input);
            assertTrue(result.contains("8000"));
        }
    }

    @Nested
    @DisplayName("非敏感内容原样返回")
    class NoFalsePositives {

        @Test
        @DisplayName("普通文本 → 无变化")
        void normalText() {
            String input = "请为北京 2 日游规划行程，预算 5000 元";
            String result = SensitiveDataMasker.mask(input);
            assertEquals(input, result);
        }

        @Test
        @DisplayName("短 sk- 前缀（非 key 长度）→ 不脱敏")
        void shortSkPrefix() {
            String input = "sk-123"; // 太短，不匹配 key 模式
            String result = SensitiveDataMasker.mask(input);
            assertEquals(input, result); // 长度 < 20，不匹配
        }

        @Test
        @DisplayName("null 输入 → 返回空字符串")
        void nullInput() {
            assertEquals("", SensitiveDataMasker.mask(null));
        }

        @Test
        @DisplayName("空字符串输入 → 返回空字符串")
        void emptyInput() {
            assertEquals("", SensitiveDataMasker.mask(""));
        }
    }

    @Nested
    @DisplayName("截断")
    class Truncation {

        @Test
        @DisplayName("文本短于 maxLength → 原样返回")
        void noTruncation() {
            String input = "短文本";
            assertEquals(input, SensitiveDataMasker.truncate(input, 100));
        }

        @Test
        @DisplayName("文本长于 maxLength → 截断并附加标记")
        void truncated() {
            String input = "a".repeat(100);
            String result = SensitiveDataMasker.truncate(input, 50);
            assertEquals(50, result.length());
            assertTrue(result.endsWith("…"));
        }

        @Test
        @DisplayName("null 输入 → 空字符串")
        void nullTruncation() {
            assertEquals("", SensitiveDataMasker.truncate(null, 10));
        }
    }

    @Nested
    @DisplayName("组合操作 maskAndTruncate")
    class MaskAndTruncate {

        @Test
        @DisplayName("先脱敏后截断 → 正确顺序")
        void maskThenTruncate() {
            String input = "DEEPSEEK_API_KEY=sk-abcdef1234567890abcdef1234567890abcdef12 extra content";
            String result = SensitiveDataMasker.maskAndTruncate(input, 80);
            assertFalse(result.contains("sk-abcdef"));
            assertTrue(result.contains("***REDACTED***"));
            assertTrue(result.length() <= 80);
        }
    }
}
