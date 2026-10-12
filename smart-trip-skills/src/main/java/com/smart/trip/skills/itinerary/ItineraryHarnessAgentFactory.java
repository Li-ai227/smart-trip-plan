package com.smart.trip.skills.itinerary;

import com.smart.trip.common.model.Attraction;
import com.smart.trip.common.util.JsonUtils;
import com.smart.trip.skills.env.EnvAccessor;
import com.smart.trip.skills.llm.LlmModelFactory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Itinerary 原生 Agent 工厂（C-008 增量 B / AC-9）：组装真实 {@code HarnessAgent}（主 LLM + Toolkit + Skill）
 * 并实现 {@link ItineraryAgentRunner}，让行程编排这条链真正经 AgentScope + 真实 LLM 跑通。
 *
 * <p><b>双模型（ADR-009）</b>：主 LLM = deepseek-v4-pro，降级 qwen3-max，
 * 模型构建逻辑复用 {@link LlmModelFactory}。</p>
 *
 * <p><b>职责分离</b>：本工厂只负责"接 LLM"；业务不变量（六项强规则：每日午晚餐、景点不重复、
 * 时间窗 8:00-20:00、餐厅≤5km、交通耗时≤2h、评分≥3.5）仍由确定性 {@link ItineraryDesignService}
 * 经 {@link ItineraryDesignTools} 的 {@code @Tool} 强制。{@link #run} 的权威行程取自
 * {@link ItineraryDesignTools#lastResult()}（确定性产物），不解析 LLM 自由文本——LLM 非确定性不污染行程数据。</p>
 *
 * <p><b>Skill 方式</b>：经 {@link ClasspathSkillRepository}("skills") 加载
 * {@code resources/skills/itinerary-design/SKILL.md}（AgentScope 原生渐进式披露）。</p>
 *
 * <p><b>无状态最小配置</b>：不接 sandbox/Redis session/subagent；每次 {@link #run} 构造独立 Toolkit +
 * {@link ItineraryDesignTools}（请求间隔离 {@code lastResult}）+ 短生命周期 HarnessAgent。密钥走环境变量
 * （禁硬编码）。该类涉及真实 LLM/网络，单测不可离线覆盖，集成验证经 env-gated 测试，JaCoCo 排除。</p>
 */
public final class ItineraryHarnessAgentFactory implements ItineraryAgentRunner, AutoCloseable {

    private static final String SKILLS_RESOURCE_DIR = "skills";

    private static final String SYS_PROMPT =
            "你是智能旅游规划系统的行程编排专家。优先加载并遵循 itinerary_design 技能；"
                    + "行程编排必须经 design_itinerary 工具产出行程数据，业务不变量（每日午晚餐、"
                    + "景点不重复、时间窗8:00-20:00、餐厅≤5km、交通耗时≤2h、评分≥3.5）由工具强制，"
                    + "禁止在工具外伪造、放宽或篡改行程数据。";

    private final Model primaryModel;
    private final Model fallbackModel;
    private final ItineraryDesignService service;
    private final ClasspathSkillRepository skillRepository;
    private final Path workspace;

    /**
     * @param primaryModel  主 LLM 模型（如 deepseek-v4-pro），非 null
     * @param fallbackModel 降级 LLM 模型（如 qwen3-max），可为 null（无二级降级）
     * @param service       确定性行程编排 Skill（承载全部业务不变量），非 null
     * @param workspace     HarnessAgent 本地 workspace 目录，非 null
     */
    public ItineraryHarnessAgentFactory(Model primaryModel, Model fallbackModel,
                                        ItineraryDesignService service, Path workspace) {
        if (primaryModel == null || service == null || workspace == null) {
            throw new IllegalArgumentException("primaryModel/service/workspace 均不可为 null");
        }
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.service = service;
        this.workspace = workspace;
        try {
            this.skillRepository = new ClasspathSkillRepository(SKILLS_RESOURCE_DIR);
        } catch (Exception ex) {
            throw new IllegalStateException("加载 classpath skills 失败: " + SKILLS_RESOURCE_DIR, ex);
        }
    }

    /**
     * 用环境变量构造工厂（密钥禁硬编码，ADR-009）。
     *
     * <p><b>两者皆缺 → 返回 {@code null}</b>（不构建原生 Agent，
     * 由 {@code ItineraryAgent} 走纯确定性直路）。</p>
     *
     * @param service   确定性行程编排 Skill
     * @param workspace 本地 workspace 目录
     * @return 工厂实例，或 {@code null}（密钥全缺失时）
     */
    /** @deprecated 请使用 {@link #fromEnvironment(ItineraryDesignService, Path, EnvAccessor)} */
    @Deprecated
    public static ItineraryHarnessAgentFactory fromEnvironment(ItineraryDesignService service,
                                                                Path workspace) {
        return fromEnvironment(service, workspace, EnvAccessor.fromSystemEnv());
    }

    /**
     * 用环境变量构造工厂（支持 {@link EnvAccessor}，密钥禁硬编码，ADR-009）。
     *
     * <p><b>密钥全缺失 → 返回 {@code null}</b>（由调用方降级）。</p>
     *
     * @param service   设计服务，非 null
     * @param workspace HarnessAgent 本地 workspace 目录，非 null
     * @param env       环境变量访问器（支持 .env 加载），非 null
     * @return 工厂实例，或 {@code null}（密钥全缺失时）
     */
    public static ItineraryHarnessAgentFactory fromEnvironment(ItineraryDesignService service,
                                                                Path workspace, EnvAccessor env) {
        LlmModelFactory.ResolvedModels models = LlmModelFactory.fromEnvironment(env);
        if (!models.hasAny()) {
            return null;
        }
        return new ItineraryHarnessAgentFactory(models.preferred(), models.runtime(),
                service, workspace);
    }

    @Override
    public ItineraryDesignResult run(ItineraryDesignQuery query) throws Exception {
        ItineraryDesignTools tools = new ItineraryDesignTools(service);
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("itinerary-designer")
                .sysPrompt(SYS_PROMPT)
                .model(primaryModel)
                .toolkit(toolkit)
                .skillRepository(skillRepository)
                .workspace(workspace)
                .disableSubagents();
        if (fallbackModel != null) {
            builder.fallbackModel(fallbackModel);
        }
        try (HarnessAgent agent = builder.build()) {
            agent.call(buildPrompt(query)).block();
        }
        return tools.lastResult();
    }

    @Override
    public void close() throws Exception {
        skillRepository.close();
    }

    private static Msg buildPrompt(ItineraryDesignQuery query) {
        String requestJson = JsonUtils.toJson(query.request());
        List<Attraction> attractions = query.candidateAttractions();
        String attractionsJson = JsonUtils.toJson(attractions);
        String attractionNames = attractions.stream()
                .map(Attraction::name)
                .collect(Collectors.joining("、"));

        StringBuilder sb = new StringBuilder();
        sb.append("请为目的地「").append(query.request().destination()).append("」编排")
                .append(query.request().days()).append("天行程。");
        sb.append("候选景点：").append(attractionNames).append("。");
        sb.append("请调用 design_itinerary 工具，传入规划请求 JSON：").append(requestJson);
        sb.append("，景点列表 JSON：").append(attractionsJson);
        if (query.routeResult() != null) {
            sb.append("，路线数据 JSON：").append(JsonUtils.toJson(query.routeResult()));
        }
        sb.append("，并据其结果作答。");

        return Msg.builder().role(MsgRole.USER).textContent(sb.toString()).build();
    }
}
