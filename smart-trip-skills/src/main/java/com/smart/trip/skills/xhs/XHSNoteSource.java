package com.smart.trip.skills.xhs;

import com.smart.trip.common.model.XHSNote;
import java.util.List;

/**
 * 小红书笔记检索端口（C-006 AC-3 / 设计约束「端口化」）。
 *
 * <p>抽象「按目的地/偏好检索旅游笔记」这一外部能力，便于主链路用桩实现验证契约、真实 HTTP
 * 适配延后接入（合规：遵守小红书 robots.txt 与 API 条款）。实现可因超时/网络故障抛任意异常，
 * 由 {@link com.smart.trip.skills.governance.GovernedExternalCaller} 统一施加限频/重试/降级
 * （绝不静默挂起，接口协议.md §2）。</p>
 */
@FunctionalInterface
public interface XHSNoteSource {

    /**
     * 按查询检索小红书笔记。
     *
     * @param query 分析请求（含目的地与偏好）
     * @return 命中的笔记列表（可空/可空集合，由调用方做 null 安全处理）
     * @throws Exception 当检索失败/超时（驱动统一治理的重试/降级）
     */
    List<XHSNote> search(XHSAnalysisQuery query) throws Exception;
}
