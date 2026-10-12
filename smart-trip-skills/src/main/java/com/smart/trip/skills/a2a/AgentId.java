package com.smart.trip.skills.a2a;

import com.smart.trip.common.exception.InvalidRequestException;

/**
 * Agent 唯一标识 {@code agent://<id>}（工程结构.md §2.2 / C-005 AC-2）。
 *
 * <p>承载裸标识（如 {@code route-planner}），并提供规范化 URI 形态 {@link #uri()}
 * （如 {@code agent://route-planner}），供 A2A 收发方寻址与注册发现使用。
 * 通过 {@link #of(String)} 解析裸 id 或完整 URI，非法输入快速失败（编码规范 §5）。</p>
 *
 * @param id 裸标识（不含 scheme），非空白
 * @deprecated 自 C-005-B 起，迁移到框架 {@code ConfigurableAgentCard.name}（ADR-008）。
 *     框架 A2A 标准协议以 {@code AgentCard.name()} 作为 Agent 标识。
 */
@Deprecated(since = "C-005-B", forRemoval = true)
public record AgentId(String id) {

    /** A2A 标识 scheme 前缀（工程结构.md §2.2）。 */
    public static final String SCHEME = "agent://";

    /**
     * 紧凑构造器：校验裸标识非空白（防御式编程，编码规范 §5）。
     */
    public AgentId {
        if (id == null || id.isBlank()) {
            throw new InvalidRequestException("AgentId 不可为空白");
        }
    }

    /**
     * 解析裸 id 或完整 {@code agent://} URI 为标识。
     *
     * @param idOrUri 裸标识或 {@code agent://<id>} 形态，非空白
     * @return 规范化标识
     * @throws InvalidRequestException 当入参为空白，或为仅含 scheme 无 id 的非法 URI
     */
    public static AgentId of(String idOrUri) {
        if (idOrUri == null || idOrUri.isBlank()) {
            throw new InvalidRequestException("AgentId 不可为空白");
        }
        String bare = idOrUri.startsWith(SCHEME) ? idOrUri.substring(SCHEME.length()) : idOrUri;
        return new AgentId(bare);
    }

    /**
     * 返回规范化 URI 形态 {@code agent://<id>}。
     *
     * @return 完整 A2A 标识 URI
     */
    public String uri() {
        return SCHEME + id;
    }
}
