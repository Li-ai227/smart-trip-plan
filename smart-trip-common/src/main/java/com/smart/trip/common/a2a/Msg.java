package com.smart.trip.common.a2a;

import com.smart.trip.common.exception.InvalidRequestException;
import com.smart.trip.common.util.CollectionUtils;
import com.smart.trip.common.util.IdUtils;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 不可变 A2A 消息信封（接口协议.md §2 / C-005 决策 D-1..D-4）。
 *
 * <p>字段逐一对齐 §2.1/§2.2：{@code msgId/from/to/type/payload/timeoutMs/traceId}。本类型为项目自建契约
 * 真相源（铁律「重新实现优于包装」），不依赖 AgentScope；其运行时传输/注册由后续 skills 层适配。</p>
 *
 * <p>构造后不可变：canonical 构造器对 {@code payload} 做 null 安全不可变拷贝，保证 A2A 反序列化任意载荷
 * 可往返（决策 D-3）。契约不变量由静态工厂 {@link #taskAssign}/{@link #taskResult}/{@link #taskError}
 * 强制，违规抛 {@link InvalidRequestException}。{@code fallback} 按 §2.2 落位 {@code payload}，
 * {@code traceId/timeoutMs} 为顶层字段，三者均为协同链路关键字段，序列化往返不得静默移除（§2.3）。</p>
 *
 * @param msgId     消息唯一 ID（{@code M-...}）
 * @param from      发送方 Agent 标识
 * @param to        接收方 Agent 标识（广播可为 null）
 * @param type      消息类型
 * @param payload   业务载荷（不可变；§2.1 {@code {taskType,context}} / §2.2 {@code {status,result,fallback}}）
 * @param timeoutMs 单次超时（毫秒）；TASK_ASSIGN 必须 {@code >0}，默认 {@link #DEFAULT_TIMEOUT_MS}
 * @param traceId   全链路追踪 ID（{@code T-...}），下游透传贯穿
 */
public record Msg(
        String msgId,
        String from,
        String to,
        MessageType type,
        Map<String, Object> payload,
        long timeoutMs,
        String traceId) {

    /** 单次调用默认超时：30s（接口协议.md §2 调用约束）。 */
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    /** payload 键：任务类型（TASK_ASSIGN）。 */
    public static final String KEY_TASK_TYPE = "taskType";

    /** payload 键：处理状态（TASK_RESULT/TASK_ERROR）。 */
    public static final String KEY_STATUS = "status";

    /** payload 键：业务结果（TASK_RESULT）。 */
    public static final String KEY_RESULT = "result";

    /** payload 键：降级标记（TASK_RESULT，§2.2/§2.3）。 */
    public static final String KEY_FALLBACK = "fallback";

    /** payload 键：错误码（TASK_ERROR）。 */
    public static final String KEY_ERROR_CODE = "errorCode";

    /** payload 键：错误信息（TASK_ERROR）。 */
    public static final String KEY_ERROR_MESSAGE = "errorMessage";

    /** 处理状态值：成功。 */
    public static final String STATUS_OK = "OK";

    /** 处理状态值：错误。 */
    public static final String STATUS_ERROR = "ERROR";

    /**
     * canonical 构造器：仅对 {@code payload} 做 null 安全不可变拷贝（决策 D-3，保持宽松以支持往返）。
     */
    public Msg {
        payload = CollectionUtils.safeCopy(payload);
    }

    /**
     * 构造委派消息 {@code TASK_ASSIGN}，使用默认超时 {@link #DEFAULT_TIMEOUT_MS}。
     *
     * @param from    发送方，非空
     * @param to      接收方，非空
     * @param traceId 链路追踪 ID，非空
     * @param payload 任务载荷，非空且非 empty
     * @return 不可变 TASK_ASSIGN 消息
     * @throws InvalidRequestException 当任一契约字段缺失时
     */
    public static Msg taskAssign(String from, String to, String traceId, Map<String, Object> payload) {
        return taskAssign(from, to, traceId, payload, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 构造委派消息 {@code TASK_ASSIGN}（§2.1）。强制契约：traceId/timeoutMs(&gt;0)/payload 必含。
     *
     * @param from      发送方，非空
     * @param to        接收方，非空
     * @param traceId   链路追踪 ID，非空
     * @param payload   任务载荷，非空且非 empty
     * @param timeoutMs 单次超时（毫秒），必须 {@code >0}
     * @return 不可变 TASK_ASSIGN 消息
     * @throws InvalidRequestException 当任一契约字段缺失或 timeoutMs 非正时
     */
    public static Msg taskAssign(String from, String to, String traceId,
                                 Map<String, Object> payload, long timeoutMs) {
        requireText(from, "from");
        requireText(to, "to");
        requireText(traceId, "traceId");
        if (payload == null || payload.isEmpty()) {
            throw new InvalidRequestException("TASK_ASSIGN payload 不可为空");
        }
        if (timeoutMs <= 0) {
            throw new InvalidRequestException("TASK_ASSIGN timeoutMs 必须 > 0: " + timeoutMs);
        }
        return new Msg(IdUtils.newMsgId(), from, to, MessageType.TASK_ASSIGN, payload, timeoutMs, traceId);
    }

    /**
     * 构造结果消息 {@code TASK_RESULT}（§2.2）。降级场景必须显式 {@code fallback=true}（§2.3）。
     *
     * @param from     发送方（子 Agent），非空
     * @param to       接收方，非空
     * @param traceId  链路追踪 ID（透传自上游），非空
     * @param result   业务结果（可空）
     * @param fallback 是否为降级产出
     * @return 不可变 TASK_RESULT 消息
     * @throws InvalidRequestException 当 from/to/traceId 缺失时
     */
    public static Msg taskResult(String from, String to, String traceId, Object result, boolean fallback) {
        requireText(from, "from");
        requireText(to, "to");
        requireText(traceId, "traceId");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(KEY_STATUS, STATUS_OK);
        payload.put(KEY_RESULT, result);
        payload.put(KEY_FALLBACK, fallback);
        return new Msg(IdUtils.newMsgId(), from, to, MessageType.TASK_RESULT, payload, 0L, traceId);
    }

    /**
     * 构造错误消息 {@code TASK_ERROR}：子 Agent 失败必须返回可识别错误，禁止静默超时（§2 测试关注点）。
     *
     * @param from         发送方（子 Agent），非空
     * @param to           接收方，非空
     * @param traceId      链路追踪 ID（透传自上游），非空
     * @param errorCode    可识别错误码（对齐接口协议错误码），非空
     * @param errorMessage 可读错误信息，非空
     * @return 不可变 TASK_ERROR 消息
     * @throws InvalidRequestException 当任一字段缺失时
     */
    public static Msg taskError(String from, String to, String traceId,
                                String errorCode, String errorMessage) {
        requireText(from, "from");
        requireText(to, "to");
        requireText(traceId, "traceId");
        requireText(errorCode, "errorCode");
        requireText(errorMessage, "errorMessage");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(KEY_STATUS, STATUS_ERROR);
        payload.put(KEY_ERROR_CODE, errorCode);
        payload.put(KEY_ERROR_MESSAGE, errorMessage);
        payload.put(KEY_FALLBACK, false);
        return new Msg(IdUtils.newMsgId(), from, to, MessageType.TASK_ERROR, payload, 0L, traceId);
    }

    /**
     * 是否为降级产出（读取 {@code payload.fallback}，§2.2/§2.3）。
     *
     * @return 当 {@code payload} 中 {@code fallback} 为 {@code true} 时返回 true
     */
    public boolean isFallback() {
        return Boolean.TRUE.equals(payload.get(KEY_FALLBACK));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("A2A Msg 字段不可为空: " + field);
        }
    }
}
