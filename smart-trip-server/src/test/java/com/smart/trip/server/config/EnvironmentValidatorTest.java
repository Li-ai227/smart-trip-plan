package com.smart.trip.server.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EnvironmentValidator} 必需环境变量校验测试（C-003 AC-3/AC-4）。
 *
 * <p>覆盖：缺失检测、启用时缺失 fail-fast 且点名变量、关闭时跳过、全部存在时通过。
 * 对应"缺少 DASHSCOPE_API_KEY 启动 fail-fast 指明缺失变量"的边界用例。</p>
 */
class EnvironmentValidatorTest {

    private static UnaryOperator<String> resolverOf(Map<String, String> env) {
        return env::get;
    }

    @Test
    @DisplayName("findMissing 返回缺失键且保持顺序")
    void findMissingReturnsBlankKeys() {
        UnaryOperator<String> resolver = resolverOf(Map.of("BAIDU_MAP_AK", "ak-123"));

        List<String> missing = EnvironmentValidator.findMissing(
                EnvironmentValidator.REQUIRED_KEYS, resolver);

        assertEquals(List.of("DASHSCOPE_API_KEY", "DEEPSEEK_API_KEY"), missing);
    }

    @Test
    @DisplayName("启用校验且缺失 → 抛异常并点名变量")
    void enabledWithMissingThrowsNamingVar() {
        UnaryOperator<String> resolver = resolverOf(Map.of());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> EnvironmentValidator.validateOrThrow(true, resolver));

        assertTrue(ex.getMessage().contains("DASHSCOPE_API_KEY"));
        assertTrue(ex.getMessage().contains("DEEPSEEK_API_KEY"));
        assertTrue(ex.getMessage().contains("BAIDU_MAP_AK"));
    }

    @Test
    @DisplayName("空白值视为缺失")
    void blankValueTreatedAsMissing() {
        UnaryOperator<String> resolver = resolverOf(
                Map.of("DASHSCOPE_API_KEY", "  ", "DEEPSEEK_API_KEY", "dk", "BAIDU_MAP_AK", "ak"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> EnvironmentValidator.validateOrThrow(true, resolver));

        assertTrue(ex.getMessage().contains("DASHSCOPE_API_KEY"));
    }

    @Test
    @DisplayName("关闭校验 → 即便缺失也不抛（支持 CI/本地空跑）")
    void disabledSkipsValidation() {
        UnaryOperator<String> resolver = resolverOf(Map.of());

        assertDoesNotThrow(() -> EnvironmentValidator.validateOrThrow(false, resolver));
    }

    @Test
    @DisplayName("全部存在 → 通过")
    void allPresentPasses() {
        UnaryOperator<String> resolver = resolverOf(Map.of(
                "DASHSCOPE_API_KEY", "sk-1", "DEEPSEEK_API_KEY", "dk-1", "BAIDU_MAP_AK", "ak-1"));

        assertDoesNotThrow(() -> EnvironmentValidator.validateOrThrow(true, resolver));
    }
}
