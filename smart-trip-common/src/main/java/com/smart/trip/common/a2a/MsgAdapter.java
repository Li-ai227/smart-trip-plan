package com.smart.trip.common.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.MsgRole;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 自定义 {@link Msg}（任务信封）↔ 框架 {@code io.agentscope.core.message.Msg}（LLM 对话消息）
 * 双向桥接适配器（C-005 增量 B AC-8 / 决策 D-8）。
 *
 * <p>两种 {@code Msg} 本质不同：自定义 {@code Msg} 是 A2A 任务调度信封（{@code msgId/from/to/type/
 * payload/timeoutMs/traceId}），框架 {@code Msg} 是 LLM 对话消息（{@code role/ContentBlocks}）。
 * 适配器利用框架 {@code Msg.metadata} 携带自定义字段，实现双向无损转换，支持渐进迁移：
 * Agent 内部可用框架消息，A2A 边界仍走自定义信封。</p>
 *
 * <p>映射规则：{@code TASK_ASSIGN}→{@code USER}、{@code TASK_RESULT/TASK_ERROR}→{@code ASSISTANT}、
 * {@code HEARTBEAT/BROADCAST}→{@code SYSTEM}。全部 7 个自定义字段经 metadata 往返。</p>
 */
public final class MsgAdapter {

    /** Metadata 键前缀，避免与框架 metadata 冲突。 */
    static final String META_MSG_ID = "trip.msgId";
    static final String META_FROM = "trip.from";
    static final String META_TO = "trip.to";
    static final String META_TYPE = "trip.type";
    static final String META_TIMEOUT_MS = "trip.timeoutMs";
    static final String META_TRACE_ID = "trip.traceId";
    static final String META_PAYLOAD = "trip.payload";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private MsgAdapter() {
    }

    /**
     * 自定义 Msg → 框架 Msg。
     *
     * <p>自定义字段全部写入 {@code metadata}，{@code payload} 序列化为 JSON 文本作为 {@code TextContent}。
     * {@code TASK_ASSIGN}→{@code USER}、{@code TASK_RESULT/TASK_ERROR}→{@code ASSISTANT}、
     * {@code HEARTBEAT/BROADCAST}→{@code SYSTEM}。</p>
     *
     * @param msg 自定义任务信封，可为 null
     * @return 框架消息，null 时返回 empty
     */
    public static Optional<io.agentscope.core.message.Msg> toFrameworkMsg(Msg msg) {
        if (msg == null) {
            return Optional.empty();
        }
        MsgRole role = mapToRole(msg.type());
        Map<String, Object> metadata = buildMetadata(msg);
        // TASK_ASSIGN → USER role + payload 序列化为 textContent
        String textContent = serializePayload(msg.payload());

        io.agentscope.core.message.Msg frameworkMsg = io.agentscope.core.message.Msg.builder()
                .role(role)
                .textContent(textContent)
                .metadata(metadata)
                .build();
        return Optional.of(frameworkMsg);
    }

    /**
     * 框架 Msg → 自定义 Msg。
     *
     * <p>从 {@code metadata} 提取自定义字段重建 {@link Msg}。无 trip metadata 的框架消息返回 empty。</p>
     *
     * @param frameworkMsg 框架 LLM 消息，可为 null
     * @return 自定义任务信封，null/无 trip metadata 时返回 empty
     */
    public static Optional<Msg> fromFrameworkMsg(io.agentscope.core.message.Msg frameworkMsg) {
        if (frameworkMsg == null) {
            return Optional.empty();
        }
        // 从 metadata 还原 msgId/from/to/traceId/timeoutMs
        Map<String, Object> metadata = frameworkMsg.getMetadata();
        if (metadata == null || !metadata.containsKey(META_TYPE)) {
            return Optional.empty();
        }

        String msgId = getString(metadata, META_MSG_ID);
        String from = getString(metadata, META_FROM);
        String to = getString(metadata, META_TO);
        MessageType type = MessageType.valueOf(getString(metadata, META_TYPE));
        String traceId = getString(metadata, META_TRACE_ID);
        long timeoutMs = getLong(metadata, META_TIMEOUT_MS);
        // 从 textContent 反序列化 payload
        Map<String, Object> payload = deserializePayload(getString(metadata, META_PAYLOAD));

        return Optional.of(new Msg(msgId, from, to, type, payload, timeoutMs, traceId));
    }

    private static MsgRole mapToRole(MessageType type) {
        return switch (type) {
            case TASK_ASSIGN -> MsgRole.USER;
            case TASK_RESULT, TASK_ERROR -> MsgRole.ASSISTANT;
            case HEARTBEAT, BROADCAST -> MsgRole.SYSTEM;
        };
    }

    private static Map<String, Object> buildMetadata(Msg msg) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(META_MSG_ID, msg.msgId());
        if (msg.from() != null) {
            meta.put(META_FROM, msg.from());
        }
        if (msg.to() != null) {
            meta.put(META_TO, msg.to());
        }
        meta.put(META_TYPE, msg.type().name());
        meta.put(META_TIMEOUT_MS, msg.timeoutMs());
        if (msg.traceId() != null) {
            meta.put(META_TRACE_ID, msg.traceId());
        }
        meta.put(META_PAYLOAD, serializePayload(msg.payload()));
        return meta;
    }

    private static String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static Map<String, Object> deserializePayload(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? null : val.toString();
    }

    private static long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ex) {
                return 0L;
            }
        }
        return 0L;
    }
}
