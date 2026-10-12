package com.smart.trip.skills.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smart.trip.common.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentId} 单测：A2A 标识 {@code agent://<id>}（工程结构.md §2.2 / C-005 AC-2）。
 */
class AgentIdTest {

    @Nested
    @DisplayName("解析与规范化")
    class Parsing {

        @Test
        @DisplayName("裸 id 与 agent:// URI 解析为同一标识")
        void ofAcceptsBareAndUri() {
            AgentId fromBare = AgentId.of("route-planner");
            AgentId fromUri = AgentId.of("agent://route-planner");

            assertEquals("route-planner", fromBare.id());
            assertEquals("agent://route-planner", fromBare.uri());
            assertEquals(fromBare, fromUri);
        }

        @Test
        @DisplayName("uri() 对齐工程结构 §2.2 的标识形态")
        void uriMatchesSpec() {
            assertEquals("agent://supervisor", AgentId.of("supervisor").uri());
            assertEquals("agent://budget-controller", AgentId.of("budget-controller").uri());
        }
    }

    @Nested
    @DisplayName("非法输入快速失败")
    class Invalid {

        @Test
        @DisplayName("空白 id 抛 InvalidRequestException")
        void blankRejected() {
            assertThrows(InvalidRequestException.class, () -> AgentId.of("  "));
        }

        @Test
        @DisplayName("null 抛 InvalidRequestException")
        void nullRejected() {
            assertThrows(InvalidRequestException.class, () -> AgentId.of(null));
        }

        @Test
        @DisplayName("仅有 scheme 无 id 抛 InvalidRequestException")
        void schemeOnlyRejected() {
            assertThrows(InvalidRequestException.class, () -> AgentId.of("agent://"));
        }
    }
}
