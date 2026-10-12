package com.smart.trip.skills.trace;

import com.smart.trip.common.util.MaskUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感字段脱敏工具（C-010 trace 方案）。
 *
 * <p>对 API Key（DEEPSEEK_API_KEY / DASHSCOPE_API_KEY）在文本中出现时
 * 正则替换为 {@code ***REDACTED***}，防止密钥泄漏到审计日志。</p>
 *
 * <p>线程安全（无状态）。</p>
 */
public final class SensitiveDataMasker {

    /** 脱敏替换文本。 */
    public static final String REDACTED = "***REDACTED***";

    // ===== DeepSeek API Key 模式 =====

    /** DeepSeek API Key 常见格式：sk- 开头，后跟字母数字连字符。 */
    private static final Pattern DEEPSEEK_KEY = Pattern.compile(
            "sk-[a-zA-Z0-9\\-_]{20,}");

    /** Bearer 令牌格式（Authorization header 中）。 */
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(Bearer\\s+)(sk-[a-zA-Z0-9\\-_]{20,})",
            Pattern.CASE_INSENSITIVE);

    // ===== DashScope API Key 模式 =====

    /** DashScope API Key 格式：sk- 开头（与 DeepSeek 共用模式，故复用）。 */
    private static final Pattern DASHSCOPE_KEY = Pattern.compile(
            "sk-[a-zA-Z0-9\\-_]{20,}");

    /** 环境变量格式：DEEPSEEK_API_KEY=xxx 或 DASHSCOPE_API_KEY=xxx。 */
    private static final Pattern ENV_KEY_PATTERN = Pattern.compile(
            "(DEEPSEEK_API_KEY|DASHSCOPE_API_KEY)\\s*[=:]\\s*['\"]?([^\\s'\"]+)['\"]?",
            Pattern.CASE_INSENSITIVE);

    /** apiKey 字段 JSON 格式："apiKey": "sk-..."。 */
    private static final Pattern JSON_API_KEY = Pattern.compile(
            "(\"api_?key\"\\s*:\\s*\")[^\"]+(\")",
            Pattern.CASE_INSENSITIVE);

    /** 中国大陆手机号（1[3-9] 开头 11 位），匹配文本中的手机号后委托 MaskUtils。 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    private SensitiveDataMasker() {
    }

    /**
     * 对文本中出现的 API Key 进行脱敏。
     *
     * @param text 原始文本，可为 null
     * @return 脱敏后文本；null 输入返回空字符串
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = text;
        // 按优先级处理：先处理环境变量格式（最长匹配），再处理通用 key 格式
        result = ENV_KEY_PATTERN.matcher(result).replaceAll("$1=***REDACTED***");
        result = JSON_API_KEY.matcher(result).replaceAll("$1***REDACTED***$2");
        result = BEARER_TOKEN.matcher(result).replaceAll("$1***REDACTED***");
        result = DEEPSEEK_KEY.matcher(result).replaceAll(REDACTED);
        result = maskPhoneNumbers(result);
        return result;
    }

    private static String maskPhoneNumbers(String text) {
        Matcher m = PHONE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, MaskUtils.maskPhone(m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 截断文本到指定最大长度，超出部分以 "…" 标记。
     * 总长（含后缀）不超过 maxLength。
     *
     * @param text     原始文本
     * @param maxLength 最大长度（含截断标记）
     * @return 截断后文本
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        String suffix = "…";
        if (maxLength <= suffix.length()) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - suffix.length()) + suffix;
    }

    /**
     * 脱敏 + 截断组合操作（常用组合）。
     *
     * @param text      原始文本
     * @param maxLength 最大长度
     * @return 脱敏并截断后的文本
     */
    public static String maskAndTruncate(String text, int maxLength) {
        return truncate(mask(text), maxLength);
    }
}
