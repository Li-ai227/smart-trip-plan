package com.smart.trip.skills.quality;

import com.smart.trip.common.model.TripPlan;
import com.smart.trip.common.util.CollectionUtils;
import java.util.List;

/**
 * {@link OutputQualityGate} 复核结论（C-005 AC-6 / 边界 #5）。
 *
 * <p>携带是否需人工介入、违规原因清单与被复核的 {@link TripPlan}。即便存在违规也保留原 plan
 * （边界 #5：标记而非抛弃数据），由 Server 决定对外 {@code requiresHumanIntervention} 暴露。</p>
 *
 * @param requiresHITL 是否需触发人工介入（存在任一全局不变量违规即为 true）
 * @param violations   违规原因清单（构造时归一为不可变拷贝），无违规为空
 * @param plan         被复核的行程方案（原样保留，不丢弃）
 */
public record QualityGateResult(boolean requiresHITL, List<QualityViolation> violations, TripPlan plan) {

    /**
     * 紧凑构造器：违规清单 null 安全不可变拷贝（决策 D-6）。
     */
    public QualityGateResult {
        violations = CollectionUtils.safeCopy(violations);
    }

    /**
     * 是否通过复核（无任何违规）。
     *
     * @return 无违规返回 {@code true}
     */
    public boolean passed() {
        return violations.isEmpty();
    }
}
