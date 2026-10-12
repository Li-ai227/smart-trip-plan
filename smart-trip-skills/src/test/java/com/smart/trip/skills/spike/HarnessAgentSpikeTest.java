package com.smart.trip.skills.spike;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * C-006 增量 C · Gate 0 Spike：验证 AgentScope {@link HarnessAgent} 在本项目的真实依赖树
 * （JDK 21 + Spring Boot 4.0 BOM + agentscope 2.0.0-RC1）下能否装配、真实调用 qwen3-max。
 *
 * <p>这是项目首次真正 import {@code io.agentscope} 并组装 Agent——此前全代码库 0 处使用 AgentScope、
 * 0 次 LLM 调用（NIH 现状）。本 spike 是增量 C 的阻塞门禁：装配不过则不推进原生化，转 ADR 定调。</p>
 *
 * <p>两级断言：①{@link #harnessAgentAssemblesOffline()} 离线装配（构造不触网、不起 sandbox/redis）；
 * ②{@link #harnessAgentLiveCall()} 仅当存在 {@code DASHSCOPE_API_KEY} 时跑一次真实 qwen3-max 调用。
 * 无 key 时第②项自动跳过，绝不静默假装通过。</p>
 */
class HarnessAgentSpikeTest {

    /** 最小 @Tool 业务方法桩：仅用于证明 Toolkit 注册与 Agent 装配链路打通。 */
    static final class ProbeTools {
        @Tool(name = "echo_destination", description = "回显目的地，用于 spike 验证工具装配")
        public String echo(@ToolParam(name = "destination", description = "目的地名") String destination) {
            return "received:" + destination;
        }
    }

    private static Model qwenModel() {
        // 构造不触网（enableEncrypt 默认 false）。RC1 的 build() 强校验 apiKey 非空，故 key 缺失时
        // 用明显假占位（仅离线装配用，不会发起真实请求；真实调用见 live 测试用真 key）。
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "offline-assembly-placeholder-not-a-real-key";
        }
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen3-max")
                .stream(false)
                .build();
    }

    private static HarnessAgent minimalAgent(Path workspace) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ProbeTools());
        return HarnessAgent.builder()
                .name("xhs-spike")
                .sysPrompt("You are a probe agent for assembly verification.")
                .model(qwenModel())
                .toolkit(toolkit)
                .workspace(workspace)
                .disableSubagents()
                .build();
    }

    @Test
    @DisplayName("HarnessAgent 在真实依赖树下离线装配成功（不触网/不起 sandbox/redis）")
    void harnessAgentAssemblesOffline(@TempDir Path workspace) throws Exception {
        try (HarnessAgent agent = minimalAgent(workspace)) {
            assertNotNull(agent, "HarnessAgent 应能在 JDK21+SB4 BOM+agentscope-RC1 下装配");
        }
    }

    @Test
    @DisplayName("真实调用 qwen3-max（仅当 DASHSCOPE_API_KEY 存在；否则跳过，不伪装通过）")
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    void harnessAgentLiveCall(@TempDir Path workspace) throws Exception {
        try (HarnessAgent agent = minimalAgent(workspace)) {
            Msg reply = agent.call(
                    Msg.builder().role(MsgRole.USER)
                            .textContent("只回复两个字：成都。").build()).block();
            assertNotNull(reply, "真实调用应返回非空回复 Msg");
            String text = reply.getTextContent();
            System.out.println("[spike] qwen3-max reply = " + text);
            assertTrue(text != null && !text.isBlank(), "回复文本不应为空");
        }
    }
}
