package com.smart.trip.common.util;

import com.smart.trip.common.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 标识符生成工具（C-004 AC-5 / 决策 D-5；C-005 决策 D-5 新增 A2A msgId/traceId）。
 *
 * <p>planId 格式 {@code P-yyyyMMdd-NNN}（数据模型.md §2.2 / 接口协议.md 示例），采用纯函数签名，
 * 不持有可变静态计数器、不隐含当前时间（编码规范 §6/§8.2）；运行期序列源为 Redis {@code INCR}，
 * 归后续仓储层，不在 common 维护。</p>
 *
 * <p>A2A 标识 {@code msgId}（{@code M-...}）/ {@code traceId}（{@code T-...}）见接口协议.md §2 示例。
 * 与纯函数 planId 不同，二者为唯一标识，基于 {@link UUID} 天然非确定生成（每条消息唯一、链路 traceId
 * 由调用方透传以贯穿全链路）。</p>
 */
public final class IdUtils {

    /** planId 前缀。 */
    private static final String PLAN_PREFIX = "P-";

    /** A2A 消息 ID 前缀（接口协议.md §2 示例 {@code M-...}）。 */
    private static final String MSG_PREFIX = "M-";

    /** A2A 链路追踪 ID 前缀（接口协议.md §2 示例 {@code T-...}）。 */
    private static final String TRACE_PREFIX = "T-";

    /** 日期部分格式：{@code yyyyMMdd}。 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 序列下界（含）。 */
    private static final int SEQ_MIN = 1;

    /** 序列上界（含），对应 {@code NNN} 三位上限。 */
    private static final int SEQ_MAX = 999;

    private IdUtils() {
    }

    /**
     * 生成行程方案 ID：{@code P-yyyyMMdd-NNN}。
     *
     * @param date     方案生成日期，不可为 null
     * @param sequence 当日序列号，取值 [1, 999]
     * @return 形如 {@code P-20260701-001} 的方案 ID
     * @throws InvalidRequestException 当 date 为 null，或 sequence 越界（{@code <1} 或 {@code >999}，NNN 进位）
     */
    public static String newPlanId(LocalDate date, int sequence) {
        if (date == null) {
            throw new InvalidRequestException("planId 生成失败: date 不可为 null");
        }
        if (sequence < SEQ_MIN || sequence > SEQ_MAX) {
            throw new InvalidRequestException(
                    "planId 序列越界(NNN 仅支持 " + SEQ_MIN + ".." + SEQ_MAX + "): " + sequence);
        }
        return PLAN_PREFIX + DATE_FORMAT.format(date) + "-" + String.format("%03d", sequence);
    }

    /**
     * 生成 A2A 消息 ID：{@code M-<uuid>}（接口协议.md §2 示例 {@code M-...}）。
     *
     * @return 全局唯一的消息 ID，每次调用不同
     */
    public static String newMsgId() {
        return MSG_PREFIX + UUID.randomUUID();
    }

    /**
     * 生成 A2A 链路追踪 ID：{@code T-<uuid>}（接口协议.md §2 示例 {@code T-...}）。
     *
     * <p>仅在链路起点生成；下游消息应透传上游 {@code traceId} 以贯穿全链路（§5）。</p>
     *
     * @return 全局唯一的链路追踪 ID，每次调用不同
     */
    public static String newTraceId() {
        return TRACE_PREFIX + UUID.randomUUID();
    }
}
