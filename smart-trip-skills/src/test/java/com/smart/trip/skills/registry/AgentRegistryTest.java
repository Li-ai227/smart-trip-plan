package com.smart.trip.skills.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smart.trip.common.enums.AgentStatus;
import com.smart.trip.skills.a2a.AgentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link InMemoryAgentRegistry} 与 {@link ResilientAgentRegistry} 单测（C-005 AC-3 / 边界 #3）。
 *
 * <p>覆盖注册/发现/注销，以及 Nacos（主注册）不可达时降级到本地缓存路由（边界 #3：A2A 不裸崩）。</p>
 */
class AgentRegistryTest {

    private static AgentRegistration registration(String id) {
        return new AgentRegistration(AgentId.of(id), "10.0.0.1:8080", AgentStatus.IDLE);
    }

    @Nested
    @DisplayName("InMemoryAgentRegistry：注册/发现/注销")
    class InMemory {

        @Test
        @DisplayName("注册后可按 agent://<id> 解析定位")
        void registerThenResolve() {
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
            registry.register(registration("route-planner"));

            assertTrue(registry.resolve(AgentId.of("route-planner")).isPresent());
            assertEquals("10.0.0.1:8080",
                    registry.resolve(AgentId.of("route-planner")).orElseThrow().address());
            assertEquals(1, registry.list().size());
        }

        @Test
        @DisplayName("注销后不可再解析")
        void deregisterRemoves() {
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
            registry.register(registration("route-planner"));
            registry.deregister(AgentId.of("route-planner"));
            assertTrue(registry.resolve(AgentId.of("route-planner")).isEmpty());
        }

        @Test
        @DisplayName("未注册的标识解析为空")
        void resolveUnknownIsEmpty() {
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
            assertTrue(registry.resolve(AgentId.of("ghost")).isEmpty());
        }
    }

    @Nested
    @DisplayName("ResilientAgentRegistry：主注册不可达降级本地（边界 #3）")
    class Resilient {

        /** 模拟 Nacos 不可达：所有读写均抛异常。 */
        private static final class UnavailableRegistry implements AgentRegistry {
            @Override
            public void register(AgentRegistration registration) {
                throw new IllegalStateException("Nacos 不可达");
            }

            @Override
            public void deregister(AgentId agentId) {
                throw new IllegalStateException("Nacos 不可达");
            }

            @Override
            public java.util.Optional<AgentRegistration> resolve(AgentId agentId) {
                throw new IllegalStateException("Nacos 不可达");
            }

            @Override
            public java.util.List<AgentRegistration> list() {
                throw new IllegalStateException("Nacos 不可达");
            }
        }

        @Test
        @DisplayName("主注册健康：解析走主注册")
        void primaryHealthyResolvesViaPrimary() {
            InMemoryAgentRegistry primary = new InMemoryAgentRegistry();
            ResilientAgentRegistry registry =
                    new ResilientAgentRegistry(primary, new InMemoryAgentRegistry());
            registry.register(registration("route-planner"));

            assertTrue(registry.resolve(AgentId.of("route-planner")).isPresent());
            // 写穿透到主注册
            assertTrue(primary.resolve(AgentId.of("route-planner")).isPresent());
        }

        @Test
        @DisplayName("主注册不可达：降级到本地缓存仍可解析，不裸崩")
        void primaryDownFallsBackToLocal() {
            ResilientAgentRegistry registry =
                    new ResilientAgentRegistry(new UnavailableRegistry(), new InMemoryAgentRegistry());
            // 注册即便主注册抛错也应写入本地缓存（best-effort 主 + 必达本地）
            registry.register(registration("route-planner"));

            assertTrue(registry.resolve(AgentId.of("route-planner")).isPresent());
            assertEquals(1, registry.list().size());
        }

        @Test
        @DisplayName("主注册解析抛错时 list 也降级本地而非抛出")
        void listDegradesGracefully() {
            ResilientAgentRegistry registry =
                    new ResilientAgentRegistry(new UnavailableRegistry(), new InMemoryAgentRegistry());
            registry.register(registration("a"));
            registry.register(registration("b"));
            assertEquals(2, registry.list().size());
            assertFalse(registry.resolve(AgentId.of("a")).isEmpty());
        }

        @Test
        @DisplayName("主注册不可达：注销降级为仅本地，不抛出")
        void deregisterPrimaryDown_fallsBackToLocal() {
            InMemoryAgentRegistry local = new InMemoryAgentRegistry();
            ResilientAgentRegistry registry =
                    new ResilientAgentRegistry(new UnavailableRegistry(), local);
            registry.register(registration("xhs-analyst"));
            assertTrue(local.resolve(AgentId.of("xhs-analyst")).isPresent(),
                    "注册应写入本地缓存");

            registry.deregister(AgentId.of("xhs-analyst"));
            assertTrue(local.resolve(AgentId.of("xhs-analyst")).isEmpty(),
                    "注销降级后本地缓存应已清除");
        }

        @Test
        @DisplayName("主注册健康且有注册：解析直接返回主注册结果，不查本地")
        void primaryHasRegistration_returnsWithoutFallingBackToLocal() {
            InMemoryAgentRegistry primary = new InMemoryAgentRegistry();
            InMemoryAgentRegistry local = new InMemoryAgentRegistry();
            primary.register(registration("route-planner"));

            ResilientAgentRegistry registry = new ResilientAgentRegistry(primary, local);
            assertTrue(registry.resolve(AgentId.of("route-planner")).isPresent());
            assertTrue(local.resolve(AgentId.of("route-planner")).isEmpty(),
                    "本地未注册，但主注册有结果则直接返回");
        }

        @Test
        @DisplayName("主注册健康但无注册：fallback 到本地缓存")
        void primaryEmpty_fallsBackToLocal() {
            InMemoryAgentRegistry primary = new InMemoryAgentRegistry();
            InMemoryAgentRegistry local = new InMemoryAgentRegistry();
            local.register(registration("budget-agent"));

            ResilientAgentRegistry registry = new ResilientAgentRegistry(primary, local);
            assertTrue(registry.resolve(AgentId.of("budget-agent")).isPresent(),
                    "主注册为空但本地有注册应返回本地结果");
        }

        @Test
        @DisplayName("主注册健康：注销透传到主注册和本地")
        void deregisterPrimaryHealthy_removesFromBoth() {
            InMemoryAgentRegistry primary = new InMemoryAgentRegistry();
            InMemoryAgentRegistry local = new InMemoryAgentRegistry();
            ResilientAgentRegistry registry = new ResilientAgentRegistry(primary, local);
            registry.register(registration("itinerary-agent"));

            registry.deregister(AgentId.of("itinerary-agent"));
            assertTrue(primary.resolve(AgentId.of("itinerary-agent")).isEmpty());
            assertTrue(local.resolve(AgentId.of("itinerary-agent")).isEmpty());
        }
    }
}
