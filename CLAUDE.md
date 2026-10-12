# smart-trip-plan — AI 协作地图

> 基于 **AgentScope Java** 的**小红书旅游规划多智能体系统**。5 个专业 Agent 自主协作：笔记分析 → 路线规划 → 行程编排 → 费用统筹。
> 你是精通 AgentScope 与 Harness Engineering 的 Java 架构师，扮演 **Owner Agent**（定义见 [.harness/agents/owner.md](.harness/agents/owner.md)）。
> **约束优先**：`.harness/rules/` 是不可违背的边界，`.harness/skills/` 是开发手段，每个变更全程留档于 `.harness/changes/`。
> **冲突处理优先级**：当文档之间出现冲突时，按 `rules > wiki > changes > CLAUDE.md > 历史文档` 处理；技术版本以 `.harness/rules/工程结构.md` 为唯一真相源。
> **开发模式**：本项目采用 `SDD + TDD + Harness` 三层模式：`SDD` 负责规格真相，`TDD` 负责测试先行，`Harness` 负责流程门禁、留档与验证。

---

## 1. 技术栈铁律（不可协商）

| 组件 | 版本 | 选型理由 |
|------|------|----------|
| JDK | **21 LTS** | AgentScope 最低要求 |
| Spring Boot | **4.0.x** | AgentScope 2.0.0-RC1 集成基线（starter 按 SB 4.0 编译；JDK 21 满足要求） |
| AgentScope | **2.0.0-RC1** | Harness Framework（中央仓 2.x 唯一可用版本，无 GA；经 `agentscope-bom` import 统一管理） |
| Maven | **3.9+** | 多模块构建 |
| LLM | **deepseek-v4-pro（主）+ qwen3-max（降级）** | 主用 DeepSeek 官方；不可用时原生切回 DashScope qwen3-max（ADR-009） |
| Nacos | **3.2.x** | A2A 服务发现（对齐 AgentScope 自带 nacos-client 3.2.1） |
| Redis | **7.x** | ReMe 短期记忆 |

**铁律**: 选"无聊"技术。不引入未经评审的依赖。重新实现优于包装第三方。

---

## 2. 项目结构速查

```
smart-trip-plan/
├── CLAUDE.md              ← 当前文件（AI 入口）
├── .harness/              ← 🔗 Harness 约束体系（真相来源）
│   ├── agents/            ←   Owner Agent 定义（总编排者）
│   ├── rules/             ←   工程结构 / 编码规范 / 开发流程规范
│   ├── skills/            ←   6 个开发技能（流水线 ①→⑥）
│   ├── wiki/              ←   业务模型 / 接口协议 / 数据模型
│   └── changes/           ←   变更追踪（按需求 ID 归档，状态机驱动）
├── smart-trip-common/    ← 公共模块: model, util, exception
├── smart-trip-agent-*/   ← 5 个 Agent 子模块（supervisor/xhs/route/itinerary/budget）
├── smart-trip-skills/    ← 业务 Skills 技能包（Agent 运行时能力，跨 Agent 复用）
├── smart-trip-server/    ← Spring Boot Web 服务
├── smart-trip-tests/     ← E2E + 集成 + ArchUnit 架构约束
├── docker/                ← Docker Compose（Nacos + Redis）
└── .claude/               ← Claude Code 配置（settings.json 权限 + Hook）
```

> ⚠️ 两类"Skills"不要混淆：`.harness/skills/` 是**开发技能**（驱动"怎么造"）；`smart-trip-skills/` 是**业务 Skills**（Agent 运行时能力）。

---

## 3. 工作原则与开发流水线

**核心理念（Harness Engineering）**: 人类设计约束 → AI 沿流水线生产 → 机器验证每一步。Owner Agent 编排技能，严格按序、不可跳步，任一门禁不通过即退回或请示人类。
**落地方式**: 先用 `SDD` 把需求落到 `change.md`，再用 `TDD` 按 `Red → Green → Refactor` 实现核心业务逻辑，最后由 `Harness` 规则、评审、CI、验证统一收口。

```
人类意图
   │  ① request-analysis   规格构建 → change.md（用户故事 + AC + 边界 + 设计约束 + 测试策略）
   │      └─ gate: 规格明确、AC 可测试、≥3 边界、测试策略清晰
   │  ② coding-skill       测试先行实现（先写失败测试，再最小实现）
   │      └─ gate: 失败测试先存在，mvn compile 通过 + 符合编码规范
   │  ③ unit-test-write    完善边界 / 降级 / 回归测试
   │      └─ gate: 测试通过 + 核心覆盖率 ≥80%
   │  ④ expert-reviewer    专家评审（含 SDD/TDD 合规）→ review.md
   │      └─ gate: 0 个 🔴 严重问题
   │  ⑤ unit-test-ci       CI 门禁（静态分析 + 架构约束 + 全量测试）
   │      └─ gate: CI 全绿
   │  ⑥ deploy-verify      部署验证（冒烟 + 健康 + 回滚）→ verify.md
   │      └─ gate: 验证通过
   ▼
交付（change 状态 = done，同步相关 wiki）
```

详见 [.harness/rules/开发流程规范.md](.harness/rules/开发流程规范.md)。

---

## 4. 多 Agent 架构

```
SupervisorAgent（主管: PlanNotebook 任务分解）
  ├─→ XHSAnalysisAgent（小红书笔记分析 + RAG）
  ├─→ RouteAgent（路线规划 + 百度地图 MCP）
  ├─→ ItineraryAgent（行程编排 + 景点/餐厅推荐）
  └─→ BudgetAgent（费用统筹 + 预算管控）
```
- 通信: **A2A 协议 + Nacos** 注册发现，**Msg** 不可变消息
- 决策: 主管自主选择 Pipeline / StateGraph / PlanNotebook 三种模式
- 安全: 关键节点 **Human-in-the-Loop**（超预算≥15%、方案确认）

---

## 5. 关键约束速记

| 约束 | 规则 |
|------|------|
| 代码规范 | Alibaba Java Coding Guidelines |
| 开发模式 | SDD 先写规格，TDD 先写失败测试 |
| Javadoc | 所有 public API 必须有 |
| 测试覆盖 | Agent 核心逻辑 ≥ 80% |
| LLM 调用 | 超时 30s + 重试 3 次 + 降级默认值 |
| API 限频 | 小红书 ≤ 10/min，百度地图 ≤ 30/min |
| 安全 | API Key → 环境变量/配置中心（禁止硬编码） |
| 用户数据 | 不落盘，Redis + 内存 |
| 提交粒度 | 一个任务一次 commit，message 含变更 ID（C-NNN） |

> 运行时可靠性、熔断/降级、容量验证、回滚与生产级门禁详见 `.harness/rules/运行时可靠性.md`。

---

## 6. 常用命令与流水线技能

```
开发流水线（.harness/skills/，由 Owner Agent 编排，按序执行）
  ① request-analysis    规格分析 → .harness/changes/C-NNN/change.md
  ② coding-skill        TDD 实现（先写失败测试，再最小实现）
  ③ unit-test-write     补齐边界 / 降级 / 回归测试（覆盖率 ≥80%）
  ④ expert-reviewer     专家评审（7 维度 + SDD/TDD 合规，0 严重问题）
  ⑤ unit-test-ci        CI 与质量门禁（Checkstyle/PMD/ArchUnit/测试）
  ⑥ deploy-verify       部署验证（冒烟 + 健康 + 回滚）

新变更建档:  cp -r .harness/changes/_TEMPLATE .harness/changes/C-NNN-<slug>
docker compose up -d    启动 Nacos + Redis
mvn clean verify        编译 + 测试全部模块
```

---

## 7. 详细文档索引

| 文档 | 阅读时机 |
|------|----------|
| [.harness/agents/owner.md](.harness/agents/owner.md) | 任何任务开始 — Owner Agent 职责、决策边界、上下文策略 |
| [.harness/rules/工程结构.md](.harness/rules/工程结构.md) | 涉及目录/模块/架构 — 依赖方向 + ArchUnit 铁律 |
| [.harness/rules/编码规范.md](.harness/rules/编码规范.md) | 写代码前 — 命名/异常/LLM调用/安全/质量指标 |
| [.harness/rules/开发流程规范.md](.harness/rules/开发流程规范.md) | 推进任务时 — 六阶段流水线 + 门禁 + 变更状态机 |
| [.harness/rules/SDD-TDD模式.md](.harness/rules/SDD-TDD模式.md) | 写核心业务前 — 规格真相、测试先行、Red-Green-Refactor 约束 |
| [.harness/skills/](.harness/skills/) | 进入对应阶段 — 各技能的 SKILL.md |
| [.harness/wiki/业务模型.md](.harness/wiki/业务模型.md) | 需求分析 — 实体/关系/业务不变量/HITL |
| [.harness/wiki/接口协议.md](.harness/wiki/接口协议.md) | 设计接口 — REST/A2A/MCP 契约 |
| [.harness/wiki/数据模型.md](.harness/wiki/数据模型.md) | 设计数据 — 领域模型 + Redis/ReMe Schema |
| [.harness/wiki/架构决策.md](.harness/wiki/架构决策.md) | 做架构决策 — 7 个 ADR 记录 |
| [.harness/changes/](.harness/changes/) | 每个变更 — 建档、追踪、断点续传 |
| `specs/features/` | 历史参考（仅目录实际存在时使用；若不存在则忽略，不作为真相来源） |
