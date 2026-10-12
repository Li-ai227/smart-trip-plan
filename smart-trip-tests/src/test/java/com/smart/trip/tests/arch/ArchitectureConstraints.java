package com.smart.trip.tests.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 架构铁律机械化守护（C-002 AC-3）。
 *
 * <p>实现 {@code .harness/rules/工程结构.md} §3 的六条架构铁律 R1~R6，违反即测试失败，
 * 取代 C-001 中对依赖方向的一次性手工反例验证。规则随各模块代码逐步落地而自然激活；
 * 在目标包尚为空时以 {@code allowEmptyShould(true)} 允许空判定，避免对空骨架误报。</p>
 *
 * <p>分析范围为生产代码（{@link ImportOption.DoNotIncludeTests}），不纳入测试类自身。</p>
 */
@AnalyzeClasses(packages = "com.smart.trip", importOptions = ImportOption.DoNotIncludeTests.class)
public final class ArchitectureConstraints {

    private ArchitectureConstraints() {
    }

    /** R1：Agent 之间禁止直接依赖，只能通过 A2A 协议（Msg）通信。 */
    @ArchTest
    static final ArchRule r1_agents_must_not_depend_on_each_other =
            slices().matching("..smart_trip.trip.agent.(*)..")
                    .should().notDependOnEachOther()
                    .as("R1: Agent 之间禁止直接依赖（只能通过 A2A 协议通信）")
                    .because("工程结构.md §3 R1：Agent 松耦合，跨 Agent 仅经 A2A")
                    .allowEmptyShould(true);

    /**
     * R2：Skill 禁止依赖任何 Agent —— Skill 是无状态可复用单元。
     *
     * <p>"Agent" 限定为本项目 Agent 模块 {@code com.smart.trip.agent..}；不含第三方框架中
     * 恰好以 {@code agent} 命名的包（如 {@code io.agentscope.harness.agent}）——Skill 依赖
     * AgentScope 框架是 ADR-008 明确许可的正向依赖，非反向耦合。R3/R4/R6 同此口径。</p>
     */
    @ArchTest
    static final ArchRule r2_skills_must_not_depend_on_agents =
            noClasses().that().resideInAPackage("..skills..")
                    .should().dependOnClassesThat().resideInAPackage("..smart_trip.trip.agent..")
                    .as("R2: Skill 禁止依赖任何 Agent")
                    .because("工程结构.md §3 R2：业务 Skill 跨 Agent 复用，不得反向耦合 Agent")
                    .allowEmptyShould(true);

    /** R3：Controller 禁止直接调用 Agent —— 必须经 Service/Facade。 */
    @ArchTest
    static final ArchRule r3_controllers_must_not_depend_on_agents =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..smart_trip.trip.agent..")
                    .as("R3: Controller 禁止直接调用 Agent（必须经 Service/Facade）")
                    .because("工程结构.md §3 R3 / §2.3 Server 与 Agent 边界")
                    .allowEmptyShould(true);

    /** R4：common 禁止依赖任何业务模块 —— 它是最底层基座。 */
    @ArchTest
    static final ArchRule r4_common_must_not_depend_on_business_modules =
            noClasses().that().resideInAPackage("..common..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..smart_trip.trip.agent..", "..skills..", "..server..")
                    .as("R4: common 禁止依赖任何业务模块")
                    .because("工程结构.md §3 R4：common 为最底层基座，依赖方向单向不可逆")
                    .allowEmptyShould(true);

    /** R5：禁止跨模块引用内部实现包（{@code ..internal..} / {@code ..impl..} 私有类）。 */
    @ArchTest
    static final ArchRule r5_no_cross_module_internal_access =
            noClasses().that().resideInAnyPackage("..server..", "..skills..", "..common..")
                    .should().dependOnClassesThat().resideInAnyPackage("..internal..", "..impl..")
                    .as("R5: 禁止跨模块引用 internal/impl 私有实现包")
                    .because("工程结构.md §3 R5：私有实现包不对外暴露；跨 Agent 引用由 R1 切片守护")
                    .allowEmptyShould(true);

    /** R6：Server 禁止复制 Agent 业务规则 —— 仅做入口/门面/编排，不依赖 Agent 内部业务实现。 */
    @ArchTest
    static final ArchRule r6_server_must_not_depend_on_agent_business_internals =
            noClasses().that().resideInAPackage("..server..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..smart_trip.trip.agent..service..",
                            "..smart_trip.trip.agent..tool..", "..smart_trip.trip.agent..rule..")
                    .as("R6: Server 不得依赖 Agent 内部业务实现（service/tool/rule）")
                    .because("工程结构.md §3 R6 / §2.3：server 只做入口与编排，不复制规划规则")
                    .allowEmptyShould(true);
}
