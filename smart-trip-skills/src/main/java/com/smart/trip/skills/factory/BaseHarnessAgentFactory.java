package com.smart.trip.skills.factory;

import io.agentscope.core.formatter.openai.DeepSeekFormatter;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;

/**
 * 通用 HarnessAgent 工厂基类（C-005 增量 B AC-10 / 决策 D-10）。
 *
 * <p>封装所有 Agent 共享的组装流程：双模型解析（ADR-009：主 deepseek-v4-pro + 降级 qwen3-max）、
 * workspace 管理、默认中间件链。子类仅覆写 agent-specific 的 {@link #createToolkit()} 和
 * {@link #systemPrompt()} 即可。</p>
 */
public abstract class BaseHarnessAgentFactory {

    /** 主 LLM 模型名（硅基流动官方：deepseek-ai/DeepSeek-V4-Pro）。 */
    public static final String DEFAULT_PRIMARY_MODEL = "deepseek-ai/DeepSeek-V4-Pro";

    /** 降级 LLM 模型名（硅基流动官方：Qwen/Qwen3.8-27B）。 */
    public static final String DEFAULT_FALLBACK_MODEL = "Qwen/Qwen3.8-27B";

    /** 硅基流动 OpenAI 兼容端点。
     *
     * <p>AgentScope OpenAIChatModel 的 {@code buildApiUrl()} 在结尾追加 {@code /chat/completions}。
     * 若 baseUrl 路径以 {@code /v1} 结尾，框架自动去掉 endpoint 中的 {@code /v1/} 前缀，
     * 最终请求 URL 为 {@code https://api.siliconflow.cn/v1/chat/completions}。</p>
     */
    public static final String DEFAULT_DEEPSEEK_BASE_URL = "https://api.siliconflow.cn/v1";

    /** 主 LLM 密钥环境变量。 */
    public static final String ENV_DEEPSEEK_KEY = "DEEPSEEK_API_KEY";

    /** 降级 LLM 密钥环境变量。 */
    public static final String ENV_DASHSCOPE_KEY = "DASHSCOPE_API_KEY";

    /** Skill classpath 父目录。 */
    protected static final String SKILLS_RESOURCE_DIR = "skills";

    /**
     * 主/降级模型对。
     *
     * @param primary  主模型，不可为 null
     * @param fallback 降级模型，可为 null
     */
    public record ModelPair(Model primary, Model fallback) {
    }

    /**
     * 根据环境变量密钥解析双模型（ADR-009）。
     *
     * <p>降级策略：有 deepseek key → 主 deepseek + 降级 qwen（qwen key 缺失则无二级降级）；
     * 无 deepseek key 但有 dashscope key → 主 qwen 单模型；两者皆缺 → fail-fast。</p>
     *
     * @param deepseekKey  DEEPSEEK_API_KEY，可为 null/blank
     * @param dashscopeKey DASHSCOPE_API_KEY，可为 null/blank
     * @return 模型对
     * @throws IllegalStateException 当两个 key 均缺失
     */
    public static ModelPair resolveModels(String deepseekKey, String dashscopeKey) {
        Model deepseek = buildDeepSeek(deepseekKey);
        Model qwen = buildQwen(dashscopeKey);
        if (deepseek != null) {
            return new ModelPair(deepseek, qwen);
        }
        if (qwen != null) {
            return new ModelPair(qwen, null);
        }
        throw new IllegalStateException(
                "缺少 LLM 密钥：需 " + ENV_DEEPSEEK_KEY + "（主 deepseek-v4-pro）"
                        + "或至少 " + ENV_DASHSCOPE_KEY + "（降级 qwen3-max）（密钥禁硬编码）");
    }

    /**
     * 子类提供 agent-specific 的工具包。
     *
     * @return Toolkit 实例
     */
    protected abstract Toolkit createToolkit();

    /**
     * 子类提供 agent-specific 的系统提示词。
     *
     * @return 系统提示词
     */
    protected abstract String systemPrompt();

    /**
     * 构造主 LLM deepseek-v4-pro（DeepSeek 官方 OpenAI 兼容端点）；key 空白返回 null。
     */
    static Model buildDeepSeek(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(DEFAULT_PRIMARY_MODEL)
                .baseUrl(DEFAULT_DEEPSEEK_BASE_URL)
                .formatter(new DeepSeekFormatter())
                .stream(false)
                .build();
    }

    /**
     * 构造降级 LLM qwen3-max（DashScope）；key 空白返回 null。
     */
    static Model buildQwen(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(DEFAULT_FALLBACK_MODEL)
                .stream(false)
                .build();
    }
}
