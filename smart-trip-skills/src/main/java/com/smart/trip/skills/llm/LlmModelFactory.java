package com.smart.trip.skills.llm;

import com.smart.trip.skills.env.EnvAccessor;
import io.agentscope.core.formatter.openai.DeepSeekFormatter;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;

/**
 * LLM 模型构建工厂（ADR-009：主 deepseek-v4-pro / 降级 qwen3-max）。
 *
 * <p>提取各 HarnessAgentFactory 的共用双模型构建逻辑，消除跨 Agent 的代码重复。
 * 密钥走环境变量（禁硬编码），key 空白时返回 {@code null}。</p>
 */
public final class LlmModelFactory {

    /** 主 LLM 模型名（硅基流动：deepseek-v4-pro）。 */
    public static final String DEFAULT_PRIMARY_MODEL = "deepseek-v4-pro";

    /** 降级 LLM 模型名（阿里云百炼：qwen3.8flash）。 */
    public static final String DEFAULT_FALLBACK_MODEL = "qwen3.8flash";

    /** 硅基流动 OpenAI 兼容端点（base_url）。
     *
     * <p>AgentScope OpenAIChatModel 的 {@code buildApiUrl()} 在结尾追加 {@code /chat/completions}。
     * 若 baseUrl 路径以 {@code /v1} 结尾，框架自动去掉 endpoint 中的 {@code /v1/} 前缀，
     * 最终请求 URL 为 {@code https://api.siliconflow.cn/v1/chat/completions}。</p>
     */
    public static final String DEFAULT_DEEPSEEK_BASE_URL = "https://api.siliconflow.cn/v1";

    /** 主 LLM 密钥环境变量（DeepSeek 官方）。 */
    public static final String ENV_DEEPSEEK_KEY = "DEEPSEEK_API_KEY";

    /** 降级 LLM 密钥环境变量（DashScope）。 */
    public static final String ENV_DASHSCOPE_KEY = "DASHSCOPE_API_KEY";

    private LlmModelFactory() {
    }

    /**
     * 构造主 LLM deepseek-v4-pro（DeepSeek 官方 OpenAI 兼容端点）；key 空白返回 null。
     *
     * @param apiKey DeepSeek API Key，可为 null/空白
     * @return Model 实例，或 null
     */
    public static Model buildDeepSeek(String apiKey) {
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
     * 构造降级 LLM qwen3-max（DashScope 原生协议）；key 空白返回 null。
     *
     * @param apiKey DashScope API Key，可为 null/空白
     * @return Model 实例，或 null
     */
    public static Model buildQwen(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(DEFAULT_FALLBACK_MODEL)
                .stream(false)
                .build();
    }

    /**
     * 从环境变量读取密钥并构建双模型。
     *
     * @return 双模型解析结果
     * @deprecated 请使用 {@link #fromEnvironment(EnvAccessor)}，支持 Spring .env 加载
     */
    @Deprecated
    public static ResolvedModels fromEnvironment() {
        return fromEnvironment(EnvAccessor.fromSystemEnv());
    }

    /**
     * 从 {@link EnvAccessor} 读取密钥并构建双模型。
     *
     * <p>相比 {@link #fromEnvironment()}，本方法支持 Spring {@code .env} 文件加载：
     * 在 Spring 上下文中传入 {@code EnvAccessor.fromSpringEnv(environment)}，
     * 即可自动读取 {@code .env} 文件中配置的密钥。</p>
     *
     * @param env 环境变量访问器，不可为 null
     * @return 双模型解析结果
     */
    public static ResolvedModels fromEnvironment(EnvAccessor env) {
        Model deepseek = buildDeepSeek(env.get(ENV_DEEPSEEK_KEY));
        Model qwen = buildQwen(env.get(ENV_DASHSCOPE_KEY));
        return new ResolvedModels(deepseek, qwen);
    }

    /**
     * 双模型解析结果。
     *
     * @param primary  主模型（deepseek），可为 null
     * @param fallback 降级模型（qwen），可为 null
     */
    public record ResolvedModels(Model primary, Model fallback) {

        /**
         * 是否至少有一个可用模型。
         *
         * @return true 至少一个模型可用
         */
        public boolean hasAny() {
            return primary != null || fallback != null;
        }

        /**
         * 返回首选主模型（有 deepseek 用 deepseek，否则用 qwen）。
         *
         * @return 首选模型，或 null（均缺失）
         */
        public Model preferred() {
            return primary != null ? primary : fallback;
        }

        /**
         * 返回运行期降级模型（仅当 deepseek 为主时 qwen 作降级）。
         *
         * @return 降级模型，或 null
         */
        public Model runtime() {
            return primary != null ? fallback : null;
        }
    }
}
