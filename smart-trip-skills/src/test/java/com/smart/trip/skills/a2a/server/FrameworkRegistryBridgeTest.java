package com.smart.trip.skills.a2a.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import io.agentscope.core.a2a.server.registry.AgentRegistry;
import io.agentscope.core.a2a.server.transport.TransportProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link FrameworkRegistryBridge} 单测（C-005 增量 B AC-11 / D-11 / TDD Red→Green）。
 *
 * <p>验证自建注册表→框架 {@link AgentRegistry} 的桥接：注册调用正确传播 AgentCard 与
 * TransportProperties，registryName 返回预期标识。</p>
 */
class FrameworkRegistryBridgeTest {

    @Test
    @DisplayName("register 正确传播 AgentCard 和 TransportProperties 到框架 AgentRegistry")
    void register_propagatesCardAndTransport() {
        RecordingRegistry recording = new RecordingRegistry();
        FrameworkRegistryBridge bridge = new FrameworkRegistryBridge(recording);

        AgentCard card = testCard("xhs-analyst", "小红书分析",
                List.of(new AgentSkill.Builder()
                        .id("xhs_note_analysis").name("XHS 笔记分析")
                        .description("分析小红书笔记").tags(List.of()).build()));
        List<TransportProperties> transports = List.of(
                TransportProperties.builder("JSONRPC").host("localhost").port(8080).build());

        bridge.register(card, transports);

        assertEquals(1, recording.registrations.size());
        RecordingRegistry.Registration reg = recording.registrations.get(0);
        assertEquals("xhs-analyst", reg.card.name());
        assertEquals(1, reg.transports.size());
        assertEquals("JSONRPC", reg.transports.get(0).transportType());
    }

    @Test
    @DisplayName("registryName 返回 trip-plan 标识")
    void registryName_returnsTripPlan() {
        RecordingRegistry recording = new RecordingRegistry();
        FrameworkRegistryBridge bridge = new FrameworkRegistryBridge(recording);

        assertEquals("trip-plan", bridge.registryName());
    }

    @Test
    @DisplayName("多次注册不同 Agent 各自独立记录")
    void multipleRegistrations_independentlyRecorded() {
        RecordingRegistry recording = new RecordingRegistry();
        FrameworkRegistryBridge bridge = new FrameworkRegistryBridge(recording);

        AgentCard card1 = testCard("agent-1", "d1", List.of());
        AgentCard card2 = testCard("agent-2", "d2", List.of());
        List<TransportProperties> transports = List.of(
                TransportProperties.builder("JSONRPC").host("localhost").port(8080).build());

        bridge.register(card1, transports);
        bridge.register(card2, transports);

        assertEquals(2, recording.registrations.size());
        assertEquals("agent-1", recording.registrations.get(0).card.name());
        assertEquals("agent-2", recording.registrations.get(1).card.name());
    }

    @Test
    @DisplayName("delegate 为 null 时 registryName 仍返回 trip-plan")
    void noDelegateRegistry_registryNameStillWorks() {
        FrameworkRegistryBridge bridge = new FrameworkRegistryBridge();

        assertEquals("trip-plan", bridge.registryName());
    }

    @Test
    @DisplayName("无 delegate 时 register 为 no-op，不抛异常")
    void noDelegate_registerIsNoOp() {
        FrameworkRegistryBridge bridge = new FrameworkRegistryBridge();
        AgentCard card = testCard("test", "test-desc", List.of());

        bridge.register(card, List.of(
                TransportProperties.builder("JSONRPC").host("localhost").port(8080).build()));
    }

    private static AgentCard testCard(String name, String description, List<AgentSkill> skills) {
        return new AgentCard.Builder()
                .name(name)
                .description(description)
                .url("http://localhost:8080")
                .version("0.1.0")
                .capabilities(new AgentCapabilities.Builder()
                        .streaming(false).pushNotifications(false)
                        .stateTransitionHistory(false).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(skills)
                .build();
    }

    /**
     * 记录式框架 AgentRegistry：记录 register 调用而不需要真实注册中心。
     */
    private static class RecordingRegistry implements AgentRegistry {

        final List<Registration> registrations = new ArrayList<>();

        record Registration(AgentCard card, List<TransportProperties> transports) {}

        @Override
        public String registryName() {
            return "recording";
        }

        @Override
        public void register(AgentCard agentCard, List<TransportProperties> transportProperties) {
            registrations.add(new Registration(agentCard, transportProperties));
        }
    }
}
