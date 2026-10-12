package com.smart.trip.skills.quality;

/**
 * 全局一致性违规项（C-005 AC-6 / 业务模型.md §4）。
 *
 * @param rule   违反的规则标识（{@code OutputQualityGate.RULE_*}）
 * @param detail 可读违规原因（含定位信息，如第几天）
 */
public record QualityViolation(String rule, String detail) {
}
