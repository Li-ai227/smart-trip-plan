package com.smart.trip.common.util;

/**
 * 敏感信息脱敏工具（C-004 安全 NFR / 编码规范 §7 / 数据模型.md §6）。
 *
 * <p>手机号、身份证等在日志与对外响应路径输出掩码，禁止泄露完整原文。空值安全（{@code null → null}）。</p>
 */
public final class MaskUtils {

    /** 手机号保留的前缀位数。 */
    private static final int PHONE_HEAD = 3;

    /** 手机号保留的后缀位数。 */
    private static final int PHONE_TAIL = 4;

    /** 身份证保留的前缀位数。 */
    private static final int ID_HEAD = 6;

    /** 身份证保留的后缀位数。 */
    private static final int ID_TAIL = 4;

    private MaskUtils() {
    }

    /**
     * 脱敏手机号：保留前 3 后 4，中间以 {@code *} 掩码（如 {@code 138****8000}）。
     *
     * @param phone 原始手机号，可为 null
     * @return 掩码串；null 入参返回 null
     */
    public static String maskPhone(String phone) {
        return mask(phone, PHONE_HEAD, PHONE_TAIL);
    }

    /**
     * 脱敏身份证号：保留前 6 后 4，中间以 {@code *} 掩码。
     *
     * @param idCard 原始身份证号，可为 null
     * @return 掩码串；null 入参返回 null
     */
    public static String maskIdCard(String idCard) {
        return mask(idCard, ID_HEAD, ID_TAIL);
    }

    /**
     * 通用掩码：保留前 {@code head} 后 {@code tail} 位，中间替换为等长 {@code *}。
     * 当原文长度不足以保留头尾时整体掩码为 4 个 {@code *}，不泄露原文。
     *
     * @param raw  原始串，可为 null
     * @param head 保留前缀位数
     * @param tail 保留后缀位数
     * @return 掩码串；null 入参返回 null
     */
    private static String mask(String raw, int head, int tail) {
        if (raw == null) {
            return null;
        }
        if (raw.length() <= head + tail) {
            return "****";
        }
        int maskedLen = raw.length() - head - tail;
        return raw.substring(0, head) + "*".repeat(maskedLen) + raw.substring(raw.length() - tail);
    }
}
