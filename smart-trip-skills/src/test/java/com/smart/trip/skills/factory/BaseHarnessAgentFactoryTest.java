package com.smart.trip.skills.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.agentscope.core.model.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BaseHarnessAgentFactory} 单测（C-005 增量 B AC-10 / TDD Red→Green）。
 *
 * <p>验证双模型组装三路径（双 key/单 key/无 key）和默认中间件链。模型构建用框架真实 Builder
 * （不需网络，仅构造本地对象），集成级行为经 env-gated 测试验证。</p>
 */
class BaseHarnessAgentFactoryTest {

    @Nested
    @DisplayName("resolveModels：双 key / 单 key / 无 key 三路径")
    class ResolveModels {

        @Test
        @DisplayName("双 key：主 deepseek + 降级 qwen")
        void dualKey_primaryAndFallback() {
            BaseHarnessAgentFactory.ModelPair pair =
                    BaseHarnessAgentFactory.resolveModels("sk-deep-test", "sk-dash-test");

            assertNotNull(pair.primary(), "主模型不可为 null");
            assertNotNull(pair.fallback(), "降级模型不可为 null");
        }

        @Test
        @DisplayName("仅 DASHSCOPE_API_KEY：降级为 qwen 单模型")
        void dashscopeOnly_singleQwen() {
            BaseHarnessAgentFactory.ModelPair pair =
                    BaseHarnessAgentFactory.resolveModels(null, "sk-dash-test");

            assertNotNull(pair.primary(), "单模型降级时 primary 应为 qwen");
            assertNull(pair.fallback(), "单模型无二级降级");
        }

        @Test
        @DisplayName("仅 DEEPSEEK_API_KEY（无 DASHSCOPE）：主 deepseek 无降级")
        void deepseekOnly_noDashscope() {
            BaseHarnessAgentFactory.ModelPair pair =
                    BaseHarnessAgentFactory.resolveModels("sk-deep-test", null);

            assertNotNull(pair.primary(), "主 deepseek 可用");
            assertNull(pair.fallback(), "无 dashscope → 无降级模型");
        }

        @Test
        @DisplayName("双 key 均缺失：fail-fast IllegalStateException")
        void noKeys_failFast() {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> BaseHarnessAgentFactory.resolveModels(null, null));
            assertNotNull(ex.getMessage());
        }

        @Test
        @DisplayName("双 key 均空白：fail-fast IllegalStateException")
        void blankKeys_failFast() {
            assertThrows(IllegalStateException.class,
                    () -> BaseHarnessAgentFactory.resolveModels("  ", ""));
        }
    }
}
