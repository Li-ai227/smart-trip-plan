package com.smart.trip.skills.a2a.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TripAgentCardFactory} 单测（C-005 增量 B AC-11 / TDD Red→Green）。
 *
 * <p>验证从 Agent 元数据构建 {@link ConfigurableAgentCard} 的三种路径：
 * 完整元数据构建、最小元数据构建、缺必填字段 fail-fast。</p>
 */
class TripAgentCardFactoryTest {

    @Test
    @DisplayName("完整元数据构建 AgentCard：name/description/version/skills 全部映射")
    void fullMetadata_buildsCompleteCard() {
        List<String> skillIds = List.of("xhs_note_analysis", "route_planning");

        ConfigurableAgentCard card = TripAgentCardFactory.buildCard(
                "xhs-analyst",
                "小红书笔记分析专家",
                "0.1.0",
                skillIds);

        assertNotNull(card);
        assertEquals("xhs-analyst", card.getName());
        assertEquals("小红书笔记分析专家", card.getDescription());
        assertEquals("0.1.0", card.getVersion());
        assertNotNull(card.getSkills());
        assertEquals(2, card.getSkills().size());
        assertEquals("xhs_note_analysis", card.getSkills().get(0).id());
        assertEquals("route_planning", card.getSkills().get(1).id());
    }

    @Test
    @DisplayName("最小元数据构建：无 skills 时返回空列表")
    void minimalMetadata_emptySkills() {
        ConfigurableAgentCard card = TripAgentCardFactory.buildCard(
                "budget-agent",
                "费用统筹",
                "0.1.0",
                List.of());

        assertNotNull(card);
        assertEquals("budget-agent", card.getName());
        assertEquals(0, card.getSkills().size());
    }

    @Test
    @DisplayName("缺 name 时 fail-fast")
    void nullName_failFast() {
        assertThrows(IllegalArgumentException.class,
                () -> TripAgentCardFactory.buildCard(null, "desc", "1.0", List.of()));
    }

    @Test
    @DisplayName("缺 description 时 fail-fast")
    void nullDescription_failFast() {
        assertThrows(IllegalArgumentException.class,
                () -> TripAgentCardFactory.buildCard("name", null, "1.0", List.of()));
    }
}
