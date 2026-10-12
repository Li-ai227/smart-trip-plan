package com.smart.trip.skills.a2a.server;

import io.a2a.spec.AgentSkill;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import java.util.List;

/**
 * Agent Card 工厂（C-005 增量 B AC-11）。
 *
 * <p>从 Agent 元数据（name/description/version/skills）构建框架
 * {@link ConfigurableAgentCard}，供 {@code AgentScopeA2aServer.Builder} 使用。
 * 每个 Agent 经 {@link ConfigurableAgentCard} 暴露 name/description/capabilities/skills
 * 元数据，走标准 A2A 协议的服务发现。</p>
 */
public final class TripAgentCardFactory {

    private TripAgentCardFactory() {
    }

    /**
     * 从 Agent 元数据构建 {@link ConfigurableAgentCard}。
     *
     * @param name        Agent 名称，不可为 null
     * @param description Agent 描述，不可为 null
     * @param version     版本号
     * @param skillIds    技能 ID 列表（可空列表）
     * @return 构建好的 AgentCard
     * @throws IllegalArgumentException name 或 description 为 null
     */
    public static ConfigurableAgentCard buildCard(String name, String description,
            String version, List<String> skillIds) {
        if (name == null) {
            throw new IllegalArgumentException("Agent name 不可为 null");
        }
        if (description == null) {
            throw new IllegalArgumentException("Agent description 不可为 null");
        }
        List<AgentSkill> skills = skillIds == null ? List.of() : skillIds.stream()
                .map(id -> new AgentSkill.Builder().id(id).name(id).description(id)
                        .tags(List.of()).build())
                .toList();

        return new ConfigurableAgentCard.Builder()
                .name(name)
                .description(description)
                .version(version != null ? version : "0.0.1")
                .skills(skills)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }
}
