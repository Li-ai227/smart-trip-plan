package com.smart.trip.skills.xhs;

import com.smart.trip.skills.env.EnvAccessor;
import com.smart.trip.skills.factory.BaseHarnessAgentFactory;
import com.smart.trip.skills.llm.LlmModelFactory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Path;

/**
 * XHS 原生 Agent 工厂（C-006 增量 C / C-005 增量 B AC-10 继承重构）：组装真实
 * {@code HarnessAgent}（主 LLM + Toolkit + Skill）并实现 {@link XHSAgentRunner}。
 *
 * <p>继承 {@link BaseHarnessAgentFactory}，复用双模型解析（ADR-009）和默认中间件链；
 * 仅提供 XHS agent-specific 的 {@link XHSAnalysisTools @Tool} 工具包和系统提示词。</p>
 *
 * <p><b>职责分离</b>：本工厂只负责"接 LLM"；业务不变量（评分≥3.5、负面剔除、降级）仍由确定性
 * {@link XHSNoteAnalyzer} 经 {@link XHSAnalysisTools} 的 {@code @Tool} 强制。{@link #run} 的权威候选
 * 取自 {@link XHSAnalysisTools#lastResult()}（确定性产物），不解析 LLM 自由文本——LLM 非确定性不污染候选。</p>
 *
 * <p><b>无状态最小配置</b>：不接 sandbox/Redis session/subagent；每次 {@link #run} 构造独立 Toolkit +
 * {@link XHSAnalysisTools}（请求间隔离 {@code lastResult}）+ 短生命周期 HarnessAgent。密钥走环境变量
 * （禁硬编码）。该类涉及真实 LLM/网络，单测不可离线覆盖，集成验证经 env-gated 测试，JaCoCo 排除。</p>
 */
public final class XHSHarnessAgentFactory extends BaseHarnessAgentFactory
        implements XHSAgentRunner, AutoCloseable {

    private static final String SYS_PROMPT =
            "你是智能旅游规划系统的小红书笔记分析专家。优先加载并遵循 xhs_note_analysis 技能；"
                    + "分析必须经 analyze_xhs_notes 工具产出候选，业务不变量（评分≥3.5、负面剔除）"
                    + "由工具强制，禁止在工具外伪造、放宽或新增候选。";

    private final Model primaryModel;
    private final Model fallbackModel;
    private final XHSNoteAnalyzer analyzer;
    private final ClasspathSkillRepository skillRepository;
    private final Path workspace;

    /**
     * 构造器
     * @param primaryModel  主 LLM 模型（如 deepseek-v4-pro），非 null
     * @param fallbackModel 降级 LLM 模型（如 qwen3-max），可为 null（无二级降级）
     * @param analyzer      确定性分析器（承载全部业务不变量），非 null
     * @param workspace     HarnessAgent 本地 workspace 目录，非 null
     */
    public XHSHarnessAgentFactory(Model primaryModel, Model fallbackModel,
                                  XHSNoteAnalyzer analyzer, Path workspace) {
        if (primaryModel == null || analyzer == null || workspace == null) {
            throw new IllegalArgumentException("primaryModel/analyzer/workspace 均不可为 null");
        }
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.analyzer = analyzer;
        this.workspace = workspace;
        try {
            // 从 classpath 加载 SKILL.md 技能文件
            this.skillRepository = new ClasspathSkillRepository(SKILLS_RESOURCE_DIR);
        } catch (Exception ex) {
            throw new IllegalStateException("加载 classpath skills 失败: " + SKILLS_RESOURCE_DIR, ex);
        }
    }

    /**
     * 用环境变量构造工厂（密钥禁硬编码，ADR-009）。
     *
     * <p><b>两者皆缺 → 返回 {@code null}</b>（不构建原生 Agent，
     * 由 {@code XHSAnalysisAgent} 走纯确定性直路）。</p>
     *
     * @param analyzer  确定性分析器
     * @param workspace 本地 workspace 目录
     * @return 工厂实例，或 {@code null}（密钥全缺失时）
     * @deprecated 请使用 {@link #fromEnvironment(XHSNoteAnalyzer, Path, EnvAccessor)}
     */
    @Deprecated
    public static XHSHarnessAgentFactory fromEnvironment(XHSNoteAnalyzer analyzer, Path workspace) {
        return fromEnvironment(analyzer, workspace, EnvAccessor.fromSystemEnv());
    }

    /**
     * 用环境变量构造工厂（经 {@link EnvAccessor} 读取密钥，支持 .env 加载）。
     *
     * @param analyzer  确定性分析器
     * @param workspace 本地 workspace 目录
     * @param env       环境变量访问器，不可为 null
     * @return 工厂实例，或 {@code null}（密钥全缺失时）
     */
    public static XHSHarnessAgentFactory fromEnvironment(XHSNoteAnalyzer analyzer, Path workspace,
                                                          EnvAccessor env) {
        LlmModelFactory.ResolvedModels models = LlmModelFactory.fromEnvironment(env);
        if (!models.hasAny()) {
            // 无 LLM key → 不构建 Agent，走确定性直路
            return null;
        }
        return new XHSHarnessAgentFactory(models.preferred(), models.runtime(), analyzer, workspace);
    }

    /**
     * 核心：执行一次分析
     * @param query 分析请求(目的地 + 偏好等),非 null
     * @return
     * @throws Exception
     */
    @Override
    public XHSAnalysisResult run(XHSAnalysisQuery query) throws Exception {
        // 1. 每次请求创建全新 Toolkit（请求间隔离 lastResult）
        XHSAnalysisTools tools = new XHSAnalysisTools(analyzer);
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);
        // 2. 构建短生命周期 HarnessAgent
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("xhs-analyst")
                .sysPrompt(SYS_PROMPT)
                .model(primaryModel)
                .toolkit(toolkit)
                .skillRepository(skillRepository)
                .workspace(workspace)
                .disableSubagents(); // 子 Agent 不允许委派
        if (fallbackModel != null) {
            builder.fallbackModel(fallbackModel);
        }
        // 3. 执行 ReAct 循环
        try (HarnessAgent agent = builder.build()) {
            agent.call(buildPrompt(query)).block();
        }
        // 4. 从工具侧取权威结果（不解析 LLM 自由文本）
        return tools.lastResult();
    }

    @Override
    public void close() throws Exception {
        skillRepository.close();
    }

    @Override
    protected Toolkit createToolkit() {
        XHSAnalysisTools tools = new XHSAnalysisTools(analyzer);
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);
        return toolkit;
    }

    @Override
    protected String systemPrompt() {
        return SYS_PROMPT;
    }

    /**
     * 构建用户提示词
     * @param query
     * @return
     */
    private static Msg buildPrompt(XHSAnalysisQuery query) {
        String prefs = String.join(",", query.preferences());
        String text = "请分析目的地「" + query.destination() + "」的小红书旅游笔记"
                + (prefs.isBlank() ? "" : "，偏好：" + prefs)
                + "。调用 analyze_xhs_notes 工具获取候选景点，并据其结果作答。";
        return Msg.builder().role(MsgRole.USER).textContent(text).build();
    }
}
