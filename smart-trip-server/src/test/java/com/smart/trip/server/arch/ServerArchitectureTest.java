package com.smart.trip.server.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Server 模块架构铁律守护（C-011 AC-10）。
 *
 * <p>ArchUnit R3/R6 + 认证模块分层约束验证。</p>
 */
@AnalyzeClasses(
        packages = "com.smart.trip.server",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public final class ServerArchitectureTest {

    private ServerArchitectureTest() {
    }

    // ── 全局 R3/R6 ──

    /** R3: Controller 禁止直接调用 Agent（必须经 Facade）。 */
    @ArchTest
    static final ArchRule r3_controller_must_not_depend_on_agents =
            noClasses().that().resideInAPackage("..server..controller..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..smart_trip.trip.agent..")
                    .as("R3: Controller 禁止直接调用 Agent")
                    .because("C-011 AC-10 / 工程结构.md §3 R3")
                    .allowEmptyShould(true);

    /** R6: Server 不依赖 Agent 内部业务实现。 */
    @ArchTest
    static final ArchRule r6_server_must_not_depend_on_agent_internals =
            noClasses().that().resideInAPackage("..server..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..smart_trip.trip.agent..service..",
                            "..smart_trip.trip.agent..tool..",
                            "..smart_trip.trip.agent..rule..")
                    .as("R6: Server 不得依赖 Agent 内部业务实现")
                    .because("C-011 AC-10 / 工程结构.md §3 R6")
                    .allowEmptyShould(true);

    // ── 认证分层约束（强制） ──

    /** Controller 只能依赖 Facade，不得直接依赖 Service/Repository。 */
    @ArchTest
    static final ArchRule auth_controller_depends_only_on_facade =
            noClasses().that().resideInAPackage("..auth.controller..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..auth.service..", "..auth.repository..")
                    .as("auth.controller 只能依赖 auth.facade，不得直接依赖 service/repository")
                    .because("C-011 分层约束：Controller → Facade → Service → Repository");

    /** Facade 不得直接依赖 Repository。 */
    @ArchTest
    static final ArchRule auth_facade_must_not_depend_on_repository =
            noClasses().that().resideInAPackage("..auth.facade..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..auth.repository..")
                    .as("auth.facade 不得直接依赖 auth.repository")
                    .because("C-011 分层约束：Facade → Service（不绕过 Service 直接访问 Repository）");

    /** Repository 不得依赖 Service/Facade/Controller。 */
    @ArchTest
    static final ArchRule auth_repository_must_not_depend_on_upper_layers =
            noClasses().that().resideInAPackage("..auth.repository..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..auth.service..", "..auth.facade..", "..auth.controller..")
                    .as("auth.repository 不得依赖 service/facade/controller")
                    .because("C-011 分层约束：Repository 只负责数据访问");

    /** Filter 不得依赖 Controller/Facade/Repository。 */
    @ArchTest
    static final ArchRule auth_filter_must_not_depend_on_business_layers =
            noClasses().that().resideInAPackage("..auth.filter..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..auth.controller..", "..auth.facade..", "..auth.repository..")
                    .as("auth.filter 不得依赖 controller/facade/repository")
                    .because("C-011 分层约束：Filter 只依赖 service + model");

    /** Facade 不得直接依赖 Agent 业务实现。 */
    @ArchTest
    static final ArchRule facade_must_not_depend_on_agent_business_logic =
            noClasses().that().resideInAPackage("..server..facade..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..smart_trip.trip.agent..service..",
                            "..smart_trip.trip.agent..tool..",
                            "..smart_trip.trip.agent..rule..")
                    .as("Facade 禁止直接依赖 Agent 内部业务实现")
                    .because("C-011 AC-10")
                    .allowEmptyShould(true);

    // ── 规划分层约束（强制，C-011 T-9） ──

    /** plan.controller 只能依赖 plan.facade，不得直接依赖 plan.service。 */
    @ArchTest
    static final ArchRule plan_controller_depends_only_on_facade =
            noClasses().that().resideInAPackage("..plan.controller..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..plan.service..")
                    .as("plan.controller 只能依赖 plan.facade，不得直接依赖 plan.service")
                    .because("C-011 分层约束：Controller → Facade → Service");
}
