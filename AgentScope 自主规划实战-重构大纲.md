# AgentScope 自主规划实战 — 重构大纲

> **副标题**: 用 SDD 规范精神 + Harness 约束 + Owner Agent + Skills 自动化流水线，构建小红书旅游规划多智能体系统
>
> **技术基线**: AgentScope Java 2.0.0-RC1 · Spring Boot 4.0.x · JDK 21 LTS · Maven 3.9+ · DashScope qwen3-max · Nacos 3.2.x · Redis 7.x
>
> **版本**: 3.1.0（Harness 版 · 校准对齐 `.harness/` 真相来源） · **最后更新**: 2026-06-05 · **状态**: draft
>
> **重构依据**: 原大纲 `AgentScope 自主规划实战.txt`（六章原始大纲）与 `AgentScope 自主规划实战梳理.txt`（梳理版）。重构目标——**以"Harness 六阶段自动化流水线"驱动产品轴全量交付，用 Owner Agent 编排 `.harness/skills/`，每步有机器的机械化门禁**；本版（v3.1）进一步将大纲与已落地的 `.harness/` 真相来源逐字校准（A2A 标识、核心 Skill 命名、变更 ID、目录现状）。

---

## 开篇 · 重构思路与双轴模型

### 0.1 为什么要重构

| 维度 | 原大纲（实战.txt / 梳理.txt） | 重构后（本文档 v3） |
|------|------------------------------|---------------------|
| 组织主线 | 按"功能模块"线性堆叠 | 按"**Harness 流水线**"组织——先建约束和技能，再用它们生产每个 feature |
| 规范落地 | SDD 四阶段（spec→plan→tasks→implement）+ `.claude/commands/` | **SDD 精神（规范即真相、AC 驱动）固化进 Harness**：需求卡 `change.md` = 可执行 spec，六阶段流水线 = plan+tasks+implement+review+verify |
| Skills 定位 | 仅指**业务技能**（路线/预算/PDF…） | 区分 **开发 Skills**（`.harness/skills/` 流水线六步）与 **业务 Skills**（`smart-trip-skills/` Agent 运行时能力） |
| 开发流程 | 隐含的人工流程 | **显式的、六阶段门禁流水线**，Owner Agent 编排，状态机驱动 + 断点续传 |
| 交付方式 | 一次性讲完所有功能 | 每个 feature（C-NNN）走同一条流水线，可复制可度量 |

> **一句话**: 原大纲讲"做什么"，重构版讲"用 Harness 六阶段流水线，按什么约束，把它们一个个造出来，每步都机器验证"。

### 0.2 SDD 与 Harness 的关系（消除"规范"二义）

本项目宣称"基于最新 SDD 规范 + Harness Engineering"，二者并非两套流程，而是**同一规范精神的两个层次**：

| SDD 原始阶段 | 在 Harness 流水线中的落地载体 | 真相来源 |
|-------------|------------------------------|---------|
| SPECIFY（规范） | ① `request-analysis` → `change.md`（用户故事 + AC + 边界 + 非功能 + 影响面） | `.harness/skills/request-analysis/SKILL.md` |
| PLAN（方案） | ② `coding-skill` 前置的"实现上下文构造"（对照 `工程结构.md` 定位模块/包/类） | `.harness/rules/工程结构.md` |
| TASKS（任务） | 变更状态机的逐阶段流转 + 一 task 一 commit（含 C-NNN） | `.harness/changes/README.md` |
| IMPLEMENT（实现） | ② `coding-skill` + ③ `unit-test-write` | 对应 SKILL.md |
| REVIEW（评审） | ④ `expert-reviewer` → `review.md`（7 维度） | `.harness/skills/expert-reviewer/SKILL.md` |
| —（SDD 无） | ⑤ `unit-test-ci` + ⑥ `deploy-verify`（机械化门禁 + 上线把关） | 对应 SKILL.md |

> **结论**: "SDD 规范" = **规范即真相、AC 驱动、文档先行**的精神；"Harness Engineering" = **把规范变成机器可验证的门禁流水线**的工程手段。本项目已用 `.harness/` 取代旧 `specs/ + .claude/commands/`（见 ADR-007），SDD 的"规范驱动"由 `change.md` 承载，不再依赖特定 AI 工具的命令体系。

### 0.3 双轴模型（贯穿全书）

```
                      方法轴（HOW —— 怎么造）
                              ▲
                              │  ┌─────────────────────────────────────────────────┐
   Harness 约束 ──────────────┼─→│ .harness/rules/  (工程结构 / 编码规范 / 开发流程)    │
   Owner Agent 编排 ──────────┼─→│ .harness/agents/owner.md                        │
   六阶段 Skills ─────────────┼─→│ request-analysis → coding-skill → unit-test-write │
                              │  │   → expert-reviewer → unit-test-ci → deploy-verify│
   Changes 状态机 ────────────┼─→│ .harness/changes/C-NNN/  (断点续传)               │
   Wiki 领域知识 ─────────────┼─→│ 业务模型 / 接口协议 / 数据模型 / 架构决策              │
                              │  └─────────────────────────────────────────────────┘
   ───────────────────────────┼────────────────────────────────────────────────────▶
                              │                            产品轴（WHAT —— 造什么）
                  Supervisor / XHS / Route / Itinerary / Budget
                  + 业务 Skills + A2A/Nacos + MCP + HITL
```

- **产品轴（WHAT）**: 5 个自主协作 Agent 的旅游规划系统 —— 源自原大纲一~五章。
- **方法轴（HOW）**: Harness 约束 + Owner Agent + 六阶段 Skills 自动化流水线 —— **重构新增的主干**。
- **交汇点**: 产品轴上的每个 feature，都必须沿方法轴的六阶段流水线被"生产"出来，每个变更留档于 `.harness/changes/`。

### 0.4 核心创新：两类 Skills，一条流水线，全链可追溯

| Skills 类型 | 载体 | 作用域 | 例子 | 涉及大纲位置 |
|------------|------|--------|------|-------------|
| **开发 Skills**（Dev Skills） | `.harness/skills/*/SKILL.md` | 用流水线构建项目 | request-analysis / coding-skill / unit-test-write / expert-reviewer / unit-test-ci / deploy-verify | 第一部分 · 第 4 章 ⭐ |
| **业务 Skills**（Domain Skills） | `smart-trip-skills/` Java 模块 | Agent 运行时能力 | 路线规划、费用优化、PDF 生成 | 第四部分 |

> **关键洞察**: "基于 Skills 构建自动化开发流水线" = Owner Agent 按 `.harness/rules/开发流程规范.md` 依次调度 6 个开发 Skills，每步经机械化门禁通过后方可进入下一步；全部产出（需求卡/评审报告/验证报告）落 `.harness/changes/<id>/`，commit message 含变更 ID，全链可追溯。

### 0.5 全书阅读地图

```
第一部分  方法论基座      —— Harness 约束 + Owner Agent + 六阶段流水线 + Rules + Wiki
第二部分  项目初始化      —— Maven 脚手架 + Docker 基础设施 + .harness/ + .claude/（流水线的"厂房"）
第三部分  多 Agent 核心   —— 产品轴主体：5 Agent + PlanNotebook + A2A/Nacos + MCP + HITL
第四部分  业务 Skills 体系 —— Agent 的可复用能力单元 + 渐进式加载 + 跨 Agent 复用
第五部分  流水线驱动交付   —— 把每个 feature 逐个走完六阶段流水线（实战展示）
第六部分  质量·可观测·上线 —— 门禁、追踪、熵管理、生产化
第七部分  实施路线图       —— Phase 0~4 里程碑与交付物
附录      速查与术语
```

---

# 第一部分 · 方法论基座（先建流水线，再造产品）

> **本部分目标**: 在写第一行业务代码之前，在 `.harness/` 下建立"约束被版本化、Owner Agent 编排技能、每一步被机器验证"的工作系统。
> **关联**: `.harness/agents/owner.md` · `.harness/rules/` · `.harness/skills/` · `.harness/wiki/`
> **现状核对**: ✅ `.harness/` 全套已落地（agents/rules×3/skills×6/wiki×4/changes 模板）。⚠️ 旧 `specs/` 目录已删除，迁移完成（见 ADR-007 与附录 E）。

## 第 1 章 Harness Engineering 约束体系

- 1.1 核心理念：**人类设计约束 → Owner Agent 沿流水线生产 → 机器验证每一步**
- 1.2 六大原则：仓库即记录系统 / 地图而非手册 / 机械化执行 / 智能体可读性 / 吞吐量优先 / 熵管理
- 1.3 三层约束落地
  - **`.harness/rules/`** —— 约束版本化的"法律条文"，CHECK 到 Git，所有 AI 生成代码必须遵守
  - **ArchUnit/Checkstyle/PMD/JaCoCo** —— 编译时机械化执行，不依赖人工记忆
  - **`.claude/settings.json` Hooks + CI 门禁** —— 运行时 + 提交时的最后防线
- 1.4 七层约束全景（从物理环境到生产可观测）
  - 层 1 **项目搭建层**: JDK 21/Maven/Wrapper 锁定 + .editorconfig + 禁 SNAPSHOT
  - 层 2 **上下文工程层**: CLAUDE.md（地图）→ `.harness/` 按需加载；ReMe 双层记忆（Redis + 向量库）
  - 层 3 **约束防护层**: ArchUnit + Guava RateLimiter + AgentScope 安全沙箱
  - 层 4 **多 Agent 架构层**: 调用链深度 ≤5 + Agent 状态机（IDLE→THINKING→ACTING→OBSERVING→DONE）+ A2A 唯一标识
  - 层 5 **评估反馈层**: OutputQualityGate 质量门禁 + 用户反馈 → ReMe 长期记忆
  - 层 6 **长任务层**: Session 持久化 + PlanNotebook 断点续传 + `.harness/changes/` 变更状态机
  - 层 7 **诊断工具层**: OpenTelemetry 链路 + Micrometer→Prometheus 指标 + 结构化 JSON 日志 + 告警阈值

## 第 2 章 Owner Agent —— 流水线的总编排者

> 真相来源: `.harness/agents/owner.md`

- 2.1 身份与使命：在不违背 `.harness/rules/` 任何约束的前提下，把模糊意图转化为高质量代码交付。**一句话准则**: 规范是真相，约束是边界，技能是手段，交付是结果。
- 2.2 核心职责七条：需求理解 / 技能编排 / 约束守护 / 上下文管理 / 变更追踪 / 质量兜底 / 人机协同
- 2.3 **决策边界**:
  - 可自主决定：命名 / 方法拆分 / 局部重构 / 测试用例 / 日志埋点 / Javadoc / import 整理
  - 必须请示人类：加依赖或升级版本 / 改 public API 或 A2A 协议 / 改架构或加设计模式 / 删功能或改 `.harness/rules/` / 触碰生产配置或密钥
  - 必须停下的 5 个信号：需求模糊 / 方案被证伪 / 约束冲突 / 安全疑虑 / 依赖阻塞
- 2.4 **上下文加载策略（地图而非手册）**: 任何任务起 → CLAUDE.md + owner.md + rules（约束边界）；需求分析 → `wiki/业务模型.md`；设计接口 → `wiki/接口协议.md` + `数据模型.md`；编码 → 当前 `changes/<id>/` + `编码规范.md`；架构 → `工程结构.md`。**禁止全量灌入**。
- 2.5 六阶段编排 → 见第 4 章

## 第 3 章 `.harness/rules/` —— 约束的真相来源

### 3.1 工程结构规范（`工程结构.md`）
- 顶层目录：9 子模块 + .harness/ + docker + .claude/
- 模块依赖方向：`common ← skills ← agents ← server ← tests`（ArchUnit 守护，单向不可逆）
- Agent 设计 5 原则：单一职责 / 显式通信（仅 A2A `Msg`）/ 可观测性 / 可中断性 / 无状态
- **5 个 Agent 的 A2A 唯一标识与核心业务 Skill**（与 `工程结构.md` §2.2 逐字对齐）：

  | Agent | A2A 标识 | 核心业务 Skill |
  |-------|----------|---------------|
  | SupervisorAgent | `agent://supervisor` | plan-notebook |
  | XHSAnalysisAgent | `agent://xhs-analyst` | xhs-note-analysis |
  | RouteAgent | `agent://route-planner` | route-planning |
  | ItineraryAgent | `agent://itinerary-designer` | itinerary-design |
  | BudgetAgent | `agent://budget-controller` | cost-calculation |

- 5 条架构铁律（R1~R5，ArchUnit `@ArchTest` 守护）：R1 Agent 间禁止直接依赖 / R2 Skill 禁止依赖任何 Agent / R3 Controller 禁止直调 Agent / R4 common 禁止依赖业务模块 / R5 禁止跨 Agent 引用内部包
- 文件组织约束（≤500 行 / ≤50 行 / ≤10 圈复杂度 / 一文件一 public 类 / 禁 SNAPSHOT）

### 3.2 编码规范（`编码规范.md`）
- 命名约定（Agent→Tool→Service→Test 后缀 + 禁止拼音/缩写/魔法值）
- Javadoc 与注释："为什么"而非"做什么"；TODO/FIXME 必须转 `changes/` 任务
- Lombok 策略：✅ Builder/Slf4j/RequiredArgsConstructor · ❌ @Data
- 异常处理：不吞异常 / BaseException 体系 / try-with-resources
- LLM/外部调用：超时 30s + 重试 3 次 + 限频（Guava RateLimiter）+ 降级必须有
- 空安全与并发：不返回 null 集合 / Agent 调用链深度 ≤5 / ThreadLocal 用完 remove
- **安全红线**: 密钥→环境变量、日志脱敏、用户数据不落盘、输入校验、沙箱限定路径
- **绝对禁止事项**: 禁止反射绕过类型安全 / 禁止 `Thread.sleep()` / 禁止非 Apache2.0/MIT 依赖
- 日志级别约定：DEBUG（调试）→ INFO（关键节点）→ WARN（降级重试）→ ERROR（需人工介入）
- 提交规范：`<type>(<scope>): C-NNN <描述>`

### 3.3 开发流程规范（`开发流程规范.md`）
- **六阶段流水线全景图**（①~⑥ 严格按序，不可跳步）
- 每阶段输入/产出/出口门禁（Gate）
- `.harness/changes/` 变更状态机：`draft → analyzing → coding → testing → reviewing → ci → verifying → done`（任一阶段可"打回上游"）
- 人机协同协议：人类职责（意图指挥官）vs Owner Agent 职责（实现执行者）vs 必须停下的 5 个触发点
- 特殊场景裁剪：Hotfix（简化①，④⑤不可省）/ Spike（结论沉淀为 ADR/wiki）/ 棕地修改（先更 wiki 再走②~⑥）/ 纯文档变更（仅①+④）
- 流程合规检查清单 + 迭代节奏（每日/每周）
- **技术债管理**: P0 一周 / P1 一月 / P2 下迭代；每迭代至少处理 1 个 P1+；每周扫异味、每月架构漂移、每季度宪章审查
- **语义化版本策略**: MAJOR（架构/破坏性 API）.MINOR（新 Agent/Skill）.PATCH（修复/优化/文档）

## 第 4 章 ⭐ 基于 Skills 的六阶段自动化开发流水线（重构核心）

> **本章是重构版的灵魂**。它定义了 Owner Agent 如何编排 `.harness/skills/` 下 6 个开发技能，把产品轴上的每个 feature 生产出来。

### 4.1 流水线全景图

```
   人类意图
      │  "我要做一个 XXX"
      ▼
┌──────────────────┐   gate: change.md approved   ┌──────────────────┐   gate: mvn compile + 规范合规
│ ① request-analysis│ ──────────────────────────▶ │ ② coding-skill    │ ──────────────────────────▶
│   需求分析          │                             │   编码实现          │
│ → change.md        │                             │ → 可编译代码        │
└──────────────────┘                             └──────────────────┘
                                                            │ gate: 测试通过 + 覆盖率≥80%
                                                            ▼
                                                 ┌──────────────────┐   gate: 0 个 🔴 严重问题
                                                 │ ③ unit-test-write │ ──────────────────────────▶
                                                 │   单元测试编写      │
                                                 └──────────────────┘
                                                            │
                                                            ▼
┌──────────────────┐   gate: CI 全绿   ┌──────────────────┐   gate: 0 个 🔴（7 维度）
│ ⑤ unit-test-ci    │ ◀────────────── │ ④ expert-reviewer  │ ◀──────────────
│   CI 与质量门禁     │                  │   专家评审          │
│   Checkstyle/PMD/  │                  │   7 维度 → review.md│
│   ArchUnit/测试     │                  └──────────────────┘
└────────┬─────────┘
         │
         ▼
┌──────────────────┐   gate: 冒烟 + 健康 + 回滚预案
│ ⑥ deploy-verify   │ ──────────────────────────▶  change 状态 = done
│   部署验证          │                              (回写 wiki，提交)
│ → verify.md        │
└──────────────────┘
```

### 4.2 开发 Skills 清单（`.harness/skills/`，6 个，严格按序）

> 每个 Skill 都有 YAML frontmatter（`name / stage / description / owner`），且在"完成标志"中**显式更新 change.md 的 status**，驱动状态机流转——这是流水线自动化的关键。

| 阶段 | Skill（name） | 文件 | 输入 | 产出 | 出口门禁 |
|------|-------|------|------|------|---------|
| ① | request-analysis | `skills/request-analysis/SKILL.md` | 人类意图 | `.harness/changes/C-NNN/change.md` | 需求明确、AC 可测试、≥3 边界 |
| ② | coding-skill | `skills/coding-skill/SKILL.md` | change.md approved | 可编译代码 | `mvn compile` + 符合编码规范 |
| ③ | unit-test-write | `skills/unit-test-write/SKILL.md` | 实现代码 | 单元测试 | 测试通过 + 核心覆盖率 ≥80% |
| ④ | expert-reviewer | `skills/expert-reviewer/SKILL.md` | 代码 + 测试 | `review.md`（7 维度） | 0 个 🔴 严重问题 |
| ⑤ | unit-test-ci | `skills/unit-test-ci/SKILL.md` | 完整变更 | CI 报告 | 静态分析 + 架构约束 + 全量测试全绿 |
| ⑥ | deploy-verify | `skills/deploy-verify/SKILL.md` | 通过 CI 的构建 | `verify.md` | 冒烟 + 健康检查 + 回滚预案 |

### 4.3 ⭐ 状态机 × Skill × 门禁 —— 自动化流水线的"三联表"（真相对齐）

> Owner Agent 读 `change.md` 的 `status` 字段即可定位"当前在哪一步、该调哪个 Skill、过哪道门禁"——这是 Skills 自动化与断点续传的核心机制。下表与 `开发流程规范.md` §2 + `changes/README.md` §2 逐字对齐。

| status | 阶段 | 责任 Skill | 出口门禁（Gate） | 机械化手段 | 不通过 |
|--------|------|-----------|-----------------|-----------|--------|
| analyzing | ① 需求分析 | request-analysis | 需求明确 + AC 可测试 + ≥3 边界 | 人审（输出后暂停） | 留在 ① 追问 |
| coding | ② 编码实现 | coding-skill | `mvn compile` + 编码规范合规 | Maven + Checkstyle | 退回 ② |
| testing | ③ 单测编写 | unit-test-write | 测试全过 + 核心覆盖率 ≥80% | JUnit5 + JaCoCo | 退回 ③ 补测试 |
| reviewing | ④ 专家评审 | expert-reviewer | 0 个 🔴（7 维度） | expert-reviewer | 退回 ② 修复 |
| ci | ⑤ CI 门禁 | unit-test-ci | 静态分析 + 架构约束 + 全量测试全绿 | Checkstyle/PMD/ArchUnit/JaCoCo/git-secrets | 退回 ②/③，阻断 CI |
| verifying | ⑥ 部署验证 | deploy-verify | 冒烟 + 健康 + 回滚预案 | deploy-verify | 退回 ② |
| done | 交付完成 | — | — | — | — |

### 4.4 阶段门禁矩阵（Gate Matrix）—— 流水线的"防呆"

| 门禁 | 位置 | 检查内容 | 机械化手段 | 不通过处理 |
|------|------|---------|-----------|-----------|
| G1 需求门 | ①→② | change.md approved；AC 可测试；≥3 边界 | 人审（Owner Agent 输出后暂停） | 退回 ① |
| G2 编译门 | ②→③ | `mvn compile` 0 error + 编码规范合规 | Maven + Checkstyle | 退回 ② |
| G3 测试门 | ③→④ | 单测全过 + 核心覆盖率 ≥80% | JUnit5 + JaCoCo | 退回 ③ 补测试 |
| G4 评审门 | ④→⑤ | 0 个 🔴 严重问题（7 维度） | expert-reviewer | 退回 ② 修复 |
| G5 架构门 | ⑤ 内 | ArchUnit 5 规则全过 | `mvn test -Dtest=ArchitectureConstraints` | 阻断 CI |
| G6 风格门 | ⑤ 内 | Checkstyle + PMD 0 violation | `mvn checkstyle:check pmd:check` | 阻断 CI |
| G7 安全门 | ⑤ 内 | 依赖无漏洞、无硬编码密钥、禁 SNAPSHOT | enforcer + dependency-check + git-secrets | 阻断 CI |
| G8 验证门 | ⑥→done | 冒烟 + 健康 + 回滚预案 | deploy-verify | 退回 ② |

### 4.5 Hooks —— 机械化执行的"最后一厘米"

> 在 `.claude/settings.json` 中用 Hook 把门禁前移到每次工具调用。

- 现状（已有）: `PostToolUse(Write|Edit)` → 提醒"关联 change.md 是否已更新"
- 建议增强:
  - `PreToolUse(Write **/*.java)` → 检查当前 change 是否已 approved（呼应 G1）
  - `PostToolUse(Write **/*.java)` → 自动后台 `mvn -q compile checkstyle:check`（G2/G6 即时反馈）
  - `Stop` Hook → 提示"当前 task 是否已 commit、change.md 状态是否更新"

### 4.6 单变更子循环（流水线最小生产单元）

```
1. Owner Agent 读 change.md 定位当前阶段（status 字段）→ 断点续传
2. 按阶段加载上下文：rules（约束边界）+ wiki（领域知识）+ 对应 SKILL.md
3. 执行技能：α 提示词（注入 AC + 边界 + 规范）→ 编码/测试/评审/验证
4. 门禁检查：机械化验证（编译/Checkstyle/PMD/ArchUnit/JaCoCo/git-secrets）
5. 修复：修复门禁发现的问题
6. 提交：git commit -m "feat(<scope>): C-NNN <描述>"（一 task 一 commit）
7. 回写 change.md：更新 status、勾选 checklist
8. 循环：回到第 1 步，直到 status=done
```

### 4.7 流水线可度量性（吞吐量优先落地）

- 每个变更的可度量指标：阶段耗时、门禁拦截次数、🔴/🟡 问题数、覆盖率
- 度量来源：git log（commit 含变更 ID）+ change.md 状态 + review.md + JaCoCo
- 目标：同一条流水线把"造一个 feature"变成**可复制、可预测、可优化**的标准动作

---

# 第二部分 · 项目初始化与基础设施（流水线的"厂房"）

> **本部分目标**: 搭好流水线能跑的物理环境与目录骨架。对应原大纲第二章"基于 AgentScope 的基础框架搭建"。
> **驱动方式**: 本部分自身作为变更 `C-000-bootstrap` 走流水线（需求=搭好脚手架），产出物即为后续 feature 的基座。
> **现状核对**: ✅ `.harness/`、`.claude/` 已就绪。⬜ 9 个 Maven 子模块尚未落地（`smart-trip-*` 目录待 C-000 创建）。

## 第 5 章 Maven 多模块脚手架（ADR-002 + 工程结构规范）

- 5.1 项目顶层目录全览（9 子模块 + .harness/ + docker + .claude/）
- 5.2 根 POM 版本统一管理：JDK 21 LTS / Spring Boot 4.0.x / AgentScope 2.0.0-RC1（agentscope-bom）/ Nacos 3.2.x / Redis 7.x / 禁 SNAPSHOT
- 5.3 9 子模块划分原则：**一个 Agent = 一个模块**，Skills 独立共享，Server 统一入口，Tests 端到端
- 5.4 模块依赖方向：`common ← skills ← agents ← server ← tests`（单向不可逆，ArchUnit 守护）
- 5.5 Maven Wrapper + `.java-version`（锁定构建工具和 JDK 版本）

### 九子模块速查

| 模块 | 职责 | 关键类 |
|------|------|--------|
| `smart-trip-common` | model/dto/enums/exception/util/constant | TripPlan, Budget, AgentStatus, BaseException |
| `smart-trip-agent-supervisor` | 主管：PlanNotebook 分解 + A2A 委派 + 汇总 | SupervisorAgent, PlanNotebookMode, HookInterceptor |
| `smart-trip-agent-xhs` | 小红书：搜索→提取→情感→RAG | XHSAnalysisAgent, NoteSearchTool |
| `smart-trip-agent-route` | 路线：百度/高德 MCP → 距离 → 排序 | RouteAgent, BaiduMapTool, BaiduMcpAdapter |
| `smart-trip-agent-itinerary` | 行程：景点→时间槽→餐饮→PDF | ItineraryAgent, TimeSlotOptimizer |
| `smart-trip-agent-budget` | 费用：估算→检查→预警(≥15% HITL) | BudgetAgent, BudgetAlertService |
| `smart-trip-skills` | 业务 Skills 技能包（跨 Agent 复用） | Skill, SkillRegistry, SkillLoader |
| `smart-trip-server` | Spring Boot Web + 门面 | TripPlanController, TripPlanFacadeService |
| `smart-trip-tests` | E2E + 集成 + ArchUnit 约束 | ArchitectureConstraints, *E2ETest |

## 第 6 章 标准化开发环境（Harness 层 1）

- 6.1 `.java-version` + `.editorconfig` + Maven Wrapper
- 6.2 Checkstyle（Alibaba Java Coding Guidelines）：FileLength≤500 / MethodLength≤50 / CyclomaticComplexity≤10 / IllegalImport
- 6.3 PMD 静态分析 + `mvn enforcer:enforce` 依赖一致性
- 6.4 Git 提交规范（Conventional Commits）：`<type>(<scope>): C-NNN <描述>`

## 第 7 章 Docker 本地基础设施

- 7.1 `docker-compose.yml`：Nacos 3.2.x + Redis 7.x 一键启动
- 7.2 Nacos 配置（A2A 注册中心，ADR-003）
- 7.3 Redis 配置（短期状态 + 会话 + 限频计数，ADR-006）

## 第 8 章 `.harness/` 约束体系（Harness 层 2）✅ 已落地

- 8.1 `.harness/rules/` 三规范：工程结构 / 编码规范 / 开发流程规范（项目的"法律"）
- 8.2 `.harness/skills/` 六技能：①~⑥ 流水线步骤定义（含 frontmatter + status 流转）
- 8.3 `.harness/wiki/` 四文档：业务模型 / 接口协议 / 数据模型 / 架构决策（领域真相来源）
- 8.4 `.harness/changes/` + `_TEMPLATE/`：变更追踪约定 + 三件套模板（change/review/verify）+ 状态机
- 8.5 `.harness/agents/owner.md`：Owner Agent 的定义（流水线总编排者）
- 8.6 `CLAUDE.md` 作为 AI 协作入口（地图模式，按需加载 `.harness/` 各组件）
- 8.7 上下文分层模型：Layer0 系统级 / Layer1 项目级(CLAUDE.md + owner.md) / Layer2 约束级(rules) / Layer3 领域级(wiki) / Layer4 运行时(ReMe)
- 8.8 `.claude/settings.json` 权限策略与 Hooks（Allow Maven/Docker/Git/Java；Deny 危险操作；PostToolUse Hook）

---

# 第三部分 · 多 Agent 协同规划核心技术（产品轴主体）

> **本部分目标**: 定义产品轴核心能力。对应原大纲第一章（角色拆解）、第二章（搭建）、第五章（MCP+A2A）。
> **注意**: 此处只定义"目标形态"与约束——每个能力在第五部分通过流水线逐 feature 落地。

## 第 9 章 5 个 Agent 角色设计与职责（原大纲一）

- 9.1 **SupervisorAgent**（智能汇总大管家，`agent://supervisor`）— PlanNotebook 任务分解 + A2A 委派 + 结果汇总→最终 TripPlan（原大纲一·5）
- 9.2 **XHSAnalysisAgent**（小红书笔记分析，`agent://xhs-analyst`）— 笔记搜索→内容提取→情感分析→RAG 增强（原大纲一·1）
- 9.3 **RouteAgent**（路线规划专员，`agent://route-planner`）— 百度/高德 MCP → 距离计算 → 路线排序 → 交通优化（原大纲一·2）
- 9.4 **ItineraryAgent**（行程编排经理，`agent://itinerary-designer`）— 景点推荐 → 时间槽分配 → 餐饮安排 → PDF 导出（原大纲一·3）
- 9.5 **BudgetAgent**（费用统筹管家，`agent://budget-controller`）— 费用估算 → 预算检查 → 超支预警 ≥15% 触发 HITL（原大纲一·4）
- 9.6 创建不同节点的 **ReActAgent**（推理-行动循环骨架）（原大纲二·3）

## 第 10 章 主管 Agent 自主分解复杂任务（原大纲二·4）

- 10.1 三种调度模式自适应切换（ADR-005）：Pipeline（简单标准）→ StateGraph（有分支逻辑）→ PlanNotebook（复杂自主规划）
- 10.2 **PlanNotebook 自主规划机制**（原大纲二·4.1）：任务拆解 → 步骤生成 → 并行/串行调度
- 10.3 **Hook 拦截技术**（原大纲二·4.2）：关键节点人工介入（HITL）— 超预算≥15% / 天数不匹配 / 评分<3.5
- 10.4 **任务分发**（原大纲二·4.3）：A2A 协议 + Msg 不可变消息 + 超时 30s + 重试 3 次 + 降级默认值
- 10.5 结果汇总与冲突仲裁：多 Agent 产出物合并 → `OutputQualityGate` 质量门禁
- 10.6 **自主决策边界**（Harness 层 4 约束）
  - Agent 调用链深度 ≤5 / 最大推理步数 20 / 最大子任务数 10 / 最大执行时间 300s
  - 产品运行时 Agent 自主 vs 必人确认的边界（搜索策略→自主 / 最终方案→人确认）

## 第 11 章 A2A 协议 + Nacos 服务注册与发现（原大纲五·1~3）

- 11.1 A2A（Agent-to-Agent）协议：消息格式 · `Msg` 不可变 · 同步/异步调用模式 · 消息类型（TASK_ASSIGN / TASK_RESULT / TASK_ERROR / HEARTBEAT / BROADCAST）
- 11.2 Agent 注册到 Nacos（原大纲二·5）：启动自动注册 → 健康检查 → 心跳保活
- 11.3 主管通过 `AgentRegistry` 动态获取可用子 Agent 列表（原大纲五·2）
- 11.4 `MsgHub` 消息总线：一对多广播 + 点对点请求/响应
- 11.5 Agent 下线处理：Nacos 健康检查失败 → 自动摘除 → 任务重新分发

## 第 12 章 MCP 外部工具接入（原大纲五·4）

- 12.1 MCP（Model Context Protocol）概念与适配层
- 12.2 RouteAgent 挂载百度/高德地图 MCP：`BaiduMcpAdapter` / `GaodeMcpAdapter`
- 12.3 MCP Tool 封装：`BaiduMapTool`（路线）/ `DistanceTool`（距离）/ `TrafficTool`（路况）
- 12.4 限频与降级：地图 API ≤30 req/min；失败时 `fallback: true` 切换离线直线距离估算

## 第 13 章 人工介入（HITL）（原大纲五·6）

- 13.1 HITL 检查点：超预算≥15% / 行程天数≠需求 / 景点评分<3.5
- 13.2 人工修改主管制定的计划：`POST /api/v1/trip-plan/{planId}/intervene`（CONFIRM / MODIFY / REJECT）
- 13.3 HITL 与 PlanNotebook 断点续传的结合（Harness 层 6）

---

# 第四部分 · 业务 Skills 技能体系（Agent 运行时能力单元）

> **本部分目标**: 实现产品轴可复用能力层。对应原大纲第三、四章。
> ⚠️ **再次强调**: 此处 Skills = **业务 Skills**（`smart-trip-skills/` Java 模块），与开发 Skills（`.harness/skills/`）同名不同层。

## 第 14 章 Skills 设计与实现（ADR-004 + 原大纲三）

- 14.1 Skill 标准契约：`Skill` 接口 + `@SkillInfo` 元数据注解 + `@SkillParam` 参数（原大纲三·3）
- 14.2 Skill 专属能力工作流：每个 Skill 封装完整工作流（输入→处理→输出）（原大纲三·2）
- 14.3 Skill 注册与发现：`SkillRegistry` 全局注册 + `SkillLoader` 按需加载
- 14.4 Skill 文件结构标准（原大纲三·3）
- 14.5 Agent 声明式挂载 Skills：配置文件声明所需 Skills 列表（原大纲三·4 / 四·1）

## 第 15 章 渐进式加载机制（原大纲四·4）

- 15.1 **ToolGroup 机制**: Agent 按需动态加载 Skill，避免一次性加载全部
- 15.2 加载时机与卸载策略
- 15.3 Spring AI / AgentScope 实现 Agent 装载 Skills（原大纲三·4）

## 第 16 章 按 Agent 划分的 Skills 分配表（原大纲四·1~5）

> 核心业务 Skill 命名与 `工程结构.md` §2.2 对齐（plan-notebook / xhs-note-analysis / route-planning / itinerary-design / cost-calculation）。

| Agent | 核心 Skill | 挂载的业务 Skills（含跨 Agent 复用） |
|-------|-----------|------------------------------------|
| XHSAnalysisAgent | xhs-note-analysis | NoteAnalysisSkill / KeywordExtractionSkill / ContentSummarySkill |
| RouteAgent | route-planning | RoutePlanningSkill / TrafficOptimizationSkill / WeatherQuerySkill（跨 Agent 复用） |
| ItineraryAgent | itinerary-design | ItineraryDesignSkill / AttractionRankingSkill / MealArrangementSkill / PdfGenerationSkill |
| BudgetAgent | cost-calculation | BudgetCalculationSkill / CostOptimizationSkill / TableGenerationSkill |
| SupervisorAgent | plan-notebook | PlanNotebook（内置）+ HookInterceptor（自定义） |

- 16.5 景点推荐 SubAgent 执行景点推荐任务（原大纲四·5）
- 16.6 跨 Agent 复用 Skills：天气查询、PDF 生成、表格制作、翻译（原大纲三·2）

---

# 第五部分 · 流水线驱动的功能交付（方法轴 × 产品轴真实交汇）

> **本部分目标**: 把第三、四部分定义的每一个产品能力，用第一部分的六阶段流水线**真实地逐个生产出来**。
> **统一动作**: 每个 feature = 一个 `C-NNN-<slug>` 变更，建档 → 走完 ①~⑥ 六阶段技能 → done → 回写 wiki。
> **变更 ID 约定**: `C-NNN` 全局递增唯一，slug 用 kebab-case（如 `C-001-xhs-note-search`，见 `changes/README.md`）。下表编号为建议交付顺序，实际 ID 由 ① request-analysis 在建档时分配。

## 第 17 章 流水线交付总览

- 17.1 feature 分组：公共基座 / xhs-analysis / route-planning / itinerary / budget / supervisor / server-integration
- 17.2 每个 feature 映射为一个或多个 `C-NNN` 变更，全程留档于 `.harness/changes/C-NNN-<slug>/`
- 17.3 交付顺序与依赖：common 基座 → xhs/route/itinerary/budget（可部分并行）→ supervisor 主管编排（依赖前四者就绪）→ server 端到端集成
- 17.4 每个 feature 的"流水线档案"：change.md + review.md + verify.md + commit 序列 + wiki 更新

## 第 18 章 Feature：公共基座（common）

> 驱动变更: `C-NNN-common-base` · 流水线: `request-analysis → coding-skill → unit-test-write → expert-reviewer → unit-test-ci → deploy-verify`

- 18.1 ① request-analysis → `change.md`：数据模型、异常体系、工具类、枚举、常量的需求卡与 AC
- 18.2 ② coding-skill → 关键交付：TripPlan/TripDay/Attraction/Route/Budget/GeoLocation 等 model；BaseException 异常体系；JsonUtils/GeoUtils 等 util；AgentStatus/TravelStyle/PlanMode/BudgetLevel 等枚举
- 18.3 ③ unit-test-write → 核心覆盖率 ≥80%
- 18.4 ④ expert-reviewer → `review.md`（0 严重问题） → ⑤ unit-test-ci → 全绿 → ⑥ deploy-verify → done

## 第 19 章 Feature：小红书笔记分析（xhs-analysis）

> 驱动变更: `C-NNN-xhs-analysis` · 对应原大纲一·1 · A2A `agent://xhs-analyst`

- 19.1 ① 需求分析 → AC：搜索/提取/情感分析的验收标准 + 边界（API 无结果、限频触发降级返回缓存、笔记为 null/空内容）
- 19.2 ② 编码 → XHSAnalysisAgent + NoteSearchTool + NoteExtractTool + SentimentAnalysisTool + NoteCacheRepository（Redis 24h TTL）
- 19.3 ③~⑥ 走完流水线

## 第 20 章 Feature：路线规划（route-planning）

> 驱动变更: `C-NNN-route-planning` · 对应原大纲一·2 + 五·4 · A2A `agent://route-planner`

- 20.1 ① 需求分析 → AC：路线规划验收标准 + 边界（地图 API 不可用→离线估算、步行/驾车模式切换、路况不可用→平均速度估算）
- 20.2 ② 编码 → RouteAgent + BaiduMapTool + GaodeMapTool + DistanceTool + BaiduMcpAdapter + RoutePlanningService + GeoCodeService
- 20.3 ③~⑥ 走完流水线

## 第 21 章 Feature：行程编排（itinerary）

> 驱动变更: `C-NNN-itinerary` · 对应原大纲一·3 · A2A `agent://itinerary-designer`

- 21.1 ① 需求分析 → AC：景点推荐/餐饮安排/时间槽验收标准 + 边界（景点评分<3.5→触发 HITL、单日无餐厅→警告、时间槽超限→自动调整）
- 21.2 ② 编码 → ItineraryAgent + AttractionSearchTool + RestaurantSearchTool + TimeAllocationTool + PdfExportTool + TimeSlotOptimizer + AttractionRankService
- 21.3 ③~⑥ 走完流水线

## 第 22 章 Feature：费用统筹（budget）

> 驱动变更: `C-NNN-budget` · 对应原大纲一·4 · A2A `agent://budget-controller`

- 22.1 ① 需求分析 → AC：费用估算/检查/预警验收标准 + 边界（超支≥15%→触发 HITL、数据缺失→保守上界估算、费用模型四类任意一项缺失→告警）
- 22.2 ② 编码 → BudgetAgent + CostEstimateTool + BudgetCheckTool + CostBreakdownTool + CostModelService（交通/住宿/餐饮/门票四类模型）+ BudgetAlertService
- 22.3 ③~⑥ 走完流水线

## 第 23 章 Feature：主管编排（supervisor）

> 驱动变更: `C-NNN-supervisor` · 对应原大纲一·5 + 二·4 · A2A `agent://supervisor`

- 23.1 ① 需求分析 → AC：三模式自适应 + PlanNotebook 自主规划 + HITL 检查点的验收标准 + 边界（依赖子 Agent 就绪、PlanNotebook 步骤超限、模式选择错误场景）
- 23.2 ② 编码 → SupervisorAgent + PlanOrchestrator + PipelineMode + StateGraphMode + PlanNotebookMode + TaskDispatcher + A2AClientService + AgentRegistry
- 23.3 ③~⑥ 走完流水线

## 第 24 章 Feature：Server 与端到端集成

> 驱动变更: `C-NNN-server-integration`

- 24.1 `smart-trip-server`：TripPlanController + TripPlanFacadeService + SessionService + FeedbackService
- 24.2 REST API 契约 → 查阅 `.harness/wiki/接口协议.md`（POST/GET/干预/反馈/管理）
- 24.3 E2E 场景（`smart-trip-tests`）：简单 1 日游 / 多日深度游 / 超预算触发 HITL / 地图 API 故障降级

## 第 25 章 Feature：前端体验与触发流（web-frontend）⭐

> 驱动变更: `C-NNN-web-frontend` · 消费 `smart-trip-server` REST API（不直接碰 Agent，符合 R3 分层）
> 设计语言: **小红书（RED）风格** —— 品牌红 `#FF2442`、白底圆角卡片、瀑布流、标签胶囊、底部 Tab。
> 配套效果图: 项目根目录 `前端效果图-小红书风格.html`（可直接浏览器打开，按模块拆分）。

### 25.1 定位与边界（源自 `业务模型.md §1.1`）
- **是什么**: 一个 Web 端"旅游规划工作台"——填一次意图，看 5 个 Agent 在线协作，拿到可确认/微调/导出的行程。
- **不是什么**（明确排除）: 移动端 App、在线支付、机酒预订、实时导航、社交/用户体系。纯规划工具。

### 25.2 核心机制：异步状态机驱动一切
- 接口异步：`POST` 返回 `202 + planId`（非直出结果），前端进入 **提交 → 轮询/订阅 → 阶段渲染 → 人工确认** 循环。
- `status` 枚举驱动界面切换：`analyzing → planning → assembling → budgeting → review → confirmed | failed`（对齐 `接口协议.md §1.2` + `业务模型.md §6.1`）。

### 25.3 四类触发点

| 触发 | 发起方 | 接口 |
|------|--------|------|
| ① 主动提交（主触发） | 用户 | `POST /api/v1/trip-plan` |
| ② 进度追踪（被动轮询/SSE） | 前端自动 | `GET /api/v1/trip-plan/{planId}` |
| ③ HITL 中断（系统→用户） | 系统 | `POST /{planId}/intervene`（CONFIRM/MODIFY/REJECT） |
| ④ 反馈/导出（收尾） | 用户 | `POST /{planId}/feedback` + PDF 导出 |

> ③ HITL 触发条件（`业务模型.md §6.2`）：超预算 ≥15% / 行程天数 ≠ 需求 / 景点评分 < 3.5。

### 25.4 status → 界面状态映射（前端"主开关"）

| `status` | 界面形态 | 用户可做 |
|----------|---------|---------|
| analyzing / planning / assembling / budgeting | 进度页（5 Agent 协作可视化 + 预算条填充） | 等待 / 取消 |
| review | 行程页（草案态）+ HITL 提示条 | 确认 / 修改 / 拒绝 |
| confirmed | 行程页（终态） | 导出 PDF / 反馈 👍👎 |
| failed | 错误页（带 `traceId`） | 重试 / 调整意图 |

### 25.5 视图模块拆分（效果图按此分块）
1. **意图输入页**：字段 1:1 映射 request body（目的地/日期/天数/预算/人数/风格/偏好标签）
2. **规划进行中页**：进度节点映射 5 Agent + status 机，"让用户看见 AI 在干活"
3. **行程结果页**：按 `TripPlan→TripDay[]→Attraction/Meal/Route` 组织，左侧按天时间轴 + 右侧 `Budget.breakdown` 四类拆分面板
4. **小红书笔记溯源页**：XHSAnalysisAgent 分析过的笔记瀑布流（小红书风格最强的一屏）
5. **HITL 介入弹窗**：3 动作映射 `intervene` 的 CONFIRM/MODIFY/REJECT
6. **反馈与导出**：PDF 导出（PdfGenerationSkill 产物）+ 👍👎（→ ReMe 长期记忆）
7. **（可选）运维看板**：`/admin/agent/status` + `/actuator/*`，面向开发/演示

### 25.6 必须处理的非快乐路径
- `400 INVALID_REQUEST` → 表单内联校验；`429 RATE_LIMITED` → 倒计时禁用；`503` → 降级页 + `traceId`
- 结果含 `fallback:true` → 卡片标注"⚠ 离线估算，仅供参考"（诚实暴露后端降级，不假装正常）
- `status=failed` → 错误页带 `traceId` 引导重提

### 25.7 与流水线衔接
- 前端本身作为独立变更 `C-NNN-web-frontend` 走六阶段流水线：① 把"轮询超时 60s 后提示""fallback 必须标注"等写成可测 AC，再走 ②~⑥。

---

# 第六部分 · 质量门禁、可观测性与上线（Harness 层 5 + 7）

## 第 26 章 评估与反馈闭环（层 5）

- 26.1 `OutputQualityGate` 质量门禁四校验：dayCount / dailyHasMeals / budgetWithinRange / noDuplicateAttractions
- 26.2 评估三维度：准确性（天数一致+地理位置正确+费用无遗漏）/ 合理性（时间 8-20点+交通≤2h+餐厅≤5km）/ 个性化（风格+预算+季节）
- 26.3 用户反馈闭环：👍/👎 + 手动修改内容 → ReMe 长期记忆更新 → 下次规划自动纳入

## 第 27 章 可观测性（层 7）

- 27.1 OpenTelemetry 全链路：Supervisor → SubAgent → Tool → API
- 27.2 Micrometer→Prometheus 关键指标与告警阈值：
  - `agent.call.latency` P95 > 10s / `agent.call.error.rate` > 5% / `api.rate_limit.hit` > 0 / `memory.cache.hit.rate` < 80%
- 27.3 结构化 JSON 日志（`timestamp / level / agent / traceId / message / context`）：每个 Agent 输入/输出、每个 Tool 调用、PlanNotebook 每步、HITL 结果
- 27.4 AgentScope Studio 可视化调试：回放执行 + 查看 Prompt/Response + 消息流转图

## 第 28 章 CI/CD 门禁与熵管理

- 28.1 CI 五阶段流水线（对齐 `unit-test-ci/SKILL.md`）：编译检查(stage-1) → 架构约束(stage-2) → 单元测试+覆盖率(stage-3) → 安全扫描(stage-4) → 集成测试(stage-5, PR 时)
- 28.2 PR 门禁：④ expert-reviewer（AI 审查）+ 人工 ≥1 Approve
- 28.3 熵管理：每周扫码异味 / 每月架构漂移分析（跑全部 ArchUnit）+ 依赖升级评估 / 每季度全面架构审查 + 规则修订
- 28.4 熵检测告警信号：循环依赖 / ArchUnit 被注释绕过 / Agent 直调内部方法 / 超长类方法 / P1+ 超 3 月未解决
- 28.5 技术债登记流程：P0 一周 / P1 一月 / P2 下迭代；每个迭代至少处理 1 个 P1+

---

# 第七部分 · 实施路线图

> 每个 Phase 的每个交付物，都作为 `.harness/changes/` 下的一个变更，通过第五部分的流水线产出。

| Phase | 里程碑 | 关键交付物 | 方法轴落地 |
|-------|--------|-----------|-----------|
| **Phase 0** 项目初始化 | Maven 可编译 + Docker 可启动 + `.harness/` 体系就绪 | 根 POM + 9 子模块；Docker Compose；`.harness/` 全套（owner/rules 3/skills 6/wiki 4/changes 模板）✅；Checkstyle/PMD/EditorConfig；`CLAUDE.md` 地图 ✅；Common 公共模块 | 第一部分 4 章全部；Harness 层 1~2 |
| **Phase 1** 基础设施 | Agent 通信就绪 + 约束自动化 | ArchUnit 架构约束测试；A2A 通信层 + Nacos 注册；AgentScope Harness 集成；Guava RateLimiter；安全沙箱；Redis+ReMe；RateLimitInterceptor + TraceInterceptor | Harness 层 3~4；门禁 G5~G7 |
| **Phase 2** MVP | 5 Agent + 核心业务 Skills 跑通一条完整旅游规划 | 5 个 Agent 核心（ReAct + ReActAgent）；≥10 个业务 Skills；3 种调度模式；OutputQualityGate；REST API；≥3 E2E 场景 | 第五部分流水线全开 |
| **Phase 3** 协作增强 | 自主决策完善 + 体验优化 | PlanNotebook 断点续传；HITL 完善；PDF/表格导出；用户反馈闭环→ReMe；Skills 渐进式加载调试 | Harness 层 5~6 |
| **Phase 4** 完善上线 | 生产级可观测 + 性能优化 | OpenTelemetry 全链路；AgentScope Studio；缓存策略 + 并行调用优化；技术债清理；文档完善 | Harness 层 7 + 第 27 章熵管理 |

---

# 附录

## 附录 A · 不可协商的技术铁律

| 组件 | 版本 | 选型理由 |
|------|------|----------|
| JDK | 21 LTS | AgentScope 最低要求 17 LTS |
| Spring Boot | 4.0.x | AgentScope 2.0.0-RC1 集成基线 |
| AgentScope | 2.0.0-RC1 | Harness Framework |
| Maven | 3.9+ | 多模块构建 |
| LLM | DashScope qwen3-max | 原生集成最优 |
| Nacos | 3.2.x | A2A 服务发现 |
| Redis | 7.x | ReMe 短期记忆 |

**铁律**: 选"无聊"技术。不引入未经评审的依赖。重新实现优于包装第三方。LTS 优先。可替换性（每个外部依赖有接口抽象层，切换成本 ≤1 天）。

## 附录 B · API 限频与 Agent 协作约束

| API | 限频 | 超时 | 重试 | 降级 |
|-----|------|------|------|------|
| 小红书 | ≤10 req/min | 30s | 3 | 返回缓存（Redis 24h TTL） |
| 百度/高德地图 | ≤30 req/min | 30s | 3 | 离线直线距离估算 |
| 通义千问 qwen3-max | ≤60 req/min | 30s | 3 | 默认模板 |

| Agent 协作约束 | 规则 | 守护手段 |
|---------------|------|---------|
| 调用链深度 | ≤5（防死锁） | `AgentCallGuard` ThreadLocal |
| 最大推理步数 | 20 步（PlanNotebook） | AgentScope 运行时 |
| 最大子任务数 | 10 个 | SupervisorAgent 分解上限 |
| 最大执行时间 | 300s | AgentScope 超时配置 |
| 核心逻辑覆盖率 | ≥80% | JaCoCo |
| 文件/方法长度 | ≤500 行 / ≤50 行 | Checkstyle |
| 圈复杂度 | ≤10 | Checkstyle |
| 提交粒度 | 一 task 一 commit，含变更 ID（C-NNN） | 人工 + CI |

## 附录 C · 开发 Skills 速查（`.harness/skills/`）

| 阶段 | Skill | 文件 | status 流转 | 产出 |
|------|-------|------|------------|------|
| ① | request-analysis | `.harness/skills/request-analysis/SKILL.md` | analyzing → coding | `changes/C-NNN/change.md` |
| ② | coding-skill | `.harness/skills/coding-skill/SKILL.md` | coding → testing | 可编译代码 |
| ③ | unit-test-write | `.harness/skills/unit-test-write/SKILL.md` | testing → reviewing | 单元测试（覆盖率 ≥80%） |
| ④ | expert-reviewer | `.harness/skills/expert-reviewer/SKILL.md` | reviewing → ci | `review.md`（7 维度） |
| ⑤ | unit-test-ci | `.harness/skills/unit-test-ci/SKILL.md` | ci → verifying | CI 报告（全绿） |
| ⑥ | deploy-verify | `.harness/skills/deploy-verify/SKILL.md` | verifying → done | `verify.md` |

## 附录 D · `.harness/` 结构速查（与实际目录一致）

```
.harness/
├── agents/owner.md                    Owner Agent 定义（总编排者）
├── rules/
│   ├── 工程结构.md                      模块/包/依赖方向/Agent 5 原则/架构铁律/A2A 标识
│   ├── 编码规范.md                      命名/注释/异常/LLM/安全/质量指标/绝对禁止
│   └── 开发流程规范.md                  六阶段流水线 + 门禁 + 变更状态机 + 技术债
├── skills/
│   ├── request-analysis/SKILL.md      ① 需求分析
│   ├── coding-skill/SKILL.md          ② 编码实现
│   ├── unit-test-write/SKILL.md       ③ 单测编写
│   ├── expert-reviewer/SKILL.md       ④ 专家评审
│   ├── unit-test-ci/SKILL.md          ⑤ CI 与质量门禁
│   └── deploy-verify/SKILL.md         ⑥ 部署验证
├── wiki/
│   ├── 业务模型.md                      实体/关系/业务不变量/HITL/合规
│   ├── 接口协议.md                      REST/A2A/MCP/外部API约束/告警阈值
│   ├── 数据模型.md                      领域模型字段 + Redis/ReMe Schema
│   └── 架构决策.md                      7 个 ADR 记录
└── changes/
    ├── README.md                       变更追踪约定 + 状态机
    └── _TEMPLATE/                      change.md / review.md / verify.md
```

## 附录 E · 与原大纲的章节映射

| 原大纲（实战.txt） | 原章节标题 | 重构后位置 |
|-------------------|-----------|-----------|
| 一 | 多 Agent 协同规划（角色拆解） | 第三部分 · 第 9 章 |
| 二·1 | 分布式 Agent 架构设计 | 第二部分 · 第 5~7 章 + `.harness/wiki/架构决策.md` |
| 二·2 | 基础框架搭建 | 第二部分 · 第 5~6 章 |
| 二·3 | 创建不同节点的 ReActAgent | 第三部分 · 第 9.6 节 |
| 二·4 | 主管 Agent 自主分解复杂任务（PlanNotebook/Hook/分发） | 第三部分 · 第 10 章 |
| 二·5 | Agent 注册到 Nacos & 封装成工具 | 第三部分 · 第 11.2 节 |
| 三 | Skills 技能（分工/工作流/文件结构/装载） | 第四部分 · 第 14~15 章 |
| 四 | 专属 SubAgents（挂载/有限预算/汇总表/渐进加载/景点推荐） | 第四部分 · 第 15~16 章 |
| 五·1~3 | A2A 协议注册/获取/调度 | 第三部分 · 第 11 章 |
| 五·4 | 路线规划 Agent 挂载百度/高德 MCP | 第三部分 · 第 12 章 |
| 五·5 | 自主分发任务给子 Agent | 第三部分 · 第 10.4 节 |
| 五·6 | 人工介入修改主管计划 | 第三部分 · 第 13 章 |
| 梳理版·实施路线图 | 分阶段交付计划 | 第七部分 |
| 梳理版·SDD 四阶段 + `.claude/commands/` | spec/plan/tasks/implement/review | **已迁移**: 融入 Harness 六阶段流水线（见 0.2 + ADR-007），旧 `specs/` 目录已删除 |
| — | **新增**: Harness + Owner Agent + 六阶段流水线 | 第一部分（方法论基座）+ 第五部分（流水线驱动交付） |

## 附录 F · 术语表

| 术语 | 英文 | 定义 |
|------|------|------|
| 开发 Skills | Dev Skills | `.harness/skills/` 中驱动流水线的六阶段技能 |
| 业务 Skills | Domain Skills | `smart-trip-skills/` 中 Agent 运行时的可复用能力单元 |
| Owner Agent | Owner Agent | `.harness/agents/owner.md` 定义的总编排者 |
| A2A | Agent-to-Agent | Agent 间通信协议 |
| ReAct | Reasoning + Acting | 推理-行动循环 |
| PlanNotebook | PlanNotebook | AgentScope 任务自主规划组件 |
| Msg | Message | AgentScope 不可变消息对象 |
| ReMe | Retrieval Memory | 双层记忆（短期 Redis + 长期向量库） |
| MCP | Model Context Protocol | 外部工具接入协议 |
| HITL | Human-in-the-Loop | 人工介入确认点 |
| Harness | Harness Engineering | 约束工程（人设计约束→AI 写码→机器验证） |
| SDD | Specification-Driven Dev | 规范驱动开发；本项目以 `change.md`（AC 驱动）承载其"规范即真相"精神 |
| Gate | Quality Gate | 流水线阶段门禁 |
| ADR | Architecture Decision Record | 架构决策记录 |
| AC | Acceptance Criteria | 可测试的验收标准 |
| Change | Change | `.harness/changes/C-NNN/` 下的需求变更单元 |

---

> **重构总结**: 本大纲 v3.1 以 Harness Engineering 为方法论底座、Owner Agent 为编排者、`.harness/skills/` 六阶段流水线为自动化装配线、`.harness/rules/` 为不可违背约束、`.harness/changes/` 为全链追溯日志，把产品轴（5 Agent 旅游规划系统）的每一个 feature 都变成这条流水线的标准化产物。
>
> **v3.1 相对 v3.0 的变化**: ①新增 0.2 节澄清 SDD 与 Harness 的关系（消除"规范"二义）；②新增 4.3 节"状态机 × Skill × 门禁三联表"，把 Skills 自动化的核心机制显式化；③A2A 标识与核心 Skill 命名逐字对齐 `工程结构.md`；④变更 ID 改为 `C-NNN-<slug>` 占位（由 ① 建档分配），对齐 `changes/README.md`；⑤标注 `specs/` 已删除、迁移完成，并在现状核对中区分"已落地 ✅ / 待落地 ⬜"。
>
> **与旧 SDD 体系的关系**: 不再基于旧 SDD 四阶段（spec→plan→tasks→implement→review）和 `.claude/commands/` 体系，而是把 SDD 的"规范即真相、AC 驱动"精神固化进 `.harness/`：Harness 约束 + Owner Agent + 六技能流水线 + Changes 状态机（见 ADR-007）。
