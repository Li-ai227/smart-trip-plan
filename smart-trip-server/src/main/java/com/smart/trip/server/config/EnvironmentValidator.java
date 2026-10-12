package com.smart.trip.server.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 启动期必需环境变量校验逻辑（C-003 AC-3/AC-4）。
 *
 * <p>纯逻辑、无 Spring 依赖，便于单测（编码规范 §8.2）。密钥仅来自环境变量/配置中心，
 * 仓库零明文密钥（编码规范 §7）。Spring 适配与启动期触发见
 * {@link EnvironmentValidationListener}。</p>
 */
public final class EnvironmentValidator {

    /**
     * 必须由环境变量/配置中心提供的密钥（缺失即 fail-fast）。
     *
     * <p>{@code DEEPSEEK_API_KEY}=主 LLM deepseek-v4-pro（ADR-009）；{@code DASHSCOPE_API_KEY}=
     * 降级 LLM qwen3-max；{@code BAIDU_MAP_AK}=地图。主 LLM 运行期不可用由 HarnessAgent 原生
     * fallbackModel 切回 qwen3-max（运行期降级），与此处启动期密钥校验是两件事。</p>
     */
    public static final List<String> REQUIRED_KEYS =
            List.of("DASHSCOPE_API_KEY", "DEEPSEEK_API_KEY", "BAIDU_MAP_AK");

    private EnvironmentValidator() {
    }

    /**
     * 返回缺失（null 或空白）的键列表。
     *
     * @param keys     待检查的键
     * @param resolver 键到值的解析器（如环境变量读取）
     * @return 缺失键列表，保持入参顺序；全部存在时返回空列表
     */
    public static List<String> findMissing(List<String> keys, UnaryOperator<String> resolver) {
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            String value = resolver.apply(key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }
        return missing;
    }

    /**
     * 校验必需密钥；启用且存在缺失时抛出异常使启动 fail-fast，异常信息点名缺失变量。
     *
     * @param enabled  是否启用校验（test/local profile 关闭以支持空跑与 CI）
     * @param resolver 键到值的解析器
     * @throws IllegalStateException 当启用校验且存在缺失变量
     */
    public static void validateOrThrow(boolean enabled, UnaryOperator<String> resolver) {
        if (!enabled) {
            return;
        }
        List<String> missing = findMissing(REQUIRED_KEYS, resolver);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "启动失败：缺少必需环境变量 " + String.join(", ", missing)
                            + "；请通过环境变量或配置中心提供（编码规范 §7 安全红线）。");
        }
    }
}
