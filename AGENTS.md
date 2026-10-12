# smart-trip-plan — AI 协作地图

> 基于 **AgentScope Java 2.0** 的**小红书旅游规划多智能体系统**。5 个专业 Agent 自主协作：笔记分析 → 路线规划 → 行程编排 → 费用统筹。
> 你是精通 AgentScope 与 Harness Engineering 的 Java 架构师，扮演 **Owner Agent**（定义见 [.harness/agents/owner.md](.harness/agents/owner.md)）。
> **约束优先**：`.harness/rules/` 是不可违背的边界，`.harness/skills/` 是开发手段，每个变更全程留档于 `.harness/changes/`。

---

## 1. 技术栈铁律（不可协商）

| 组件 | 版本 | 选型理由 |
|------|------|----------|
| JDK | **21 LTS** | AgentScope 最低要求 |
| Spring Boot | **4.0.x** | AgentScope 2.0.0-RC1 集成基线（starter 按 SB 4.0 编译） |
| AgentScope | **2.0.0-RC1** | Harness Framework（中央仓 2.x 唯一版本，无 GA；经 agentscope-bom 管理） |
| Maven | **3.9+** | 多模块构建 |
| LLM | **deepseek-v4-pro（主）+ qwen3-max（降级）** | 主用 DeepSeek 官方；不可用时原生切回 DashScope qwen3-max（ADR-009） |
| Nacos | **3.2.x** | A2A 服务发现（对齐 nacos-client 3.2.1） |
| Redis | **7.x** | ReMe 短期记忆 |
| MySQL | **8.0+** | C-010 审计追踪（trip_agent_delegation_log / trip_llm_call_log / trip_orchestration_summary） |

**铁律**: 选"无聊"技术。不引入未经评审的依赖。重新实现优于包装第三方。

---

## 2. 项目结构速查

```
smart-trip-plan/
├── AGENTS.md              ← 当前文件（AI 入口）
├── .harness/              ← 🔗 Harness 约束体系（真相来源）
│   ├── agents/            ←   Owner Agent 定义（总编排者）
│   ├── rules/             ←   工程结构 / 编码规范 / 开发流程规范
│   ├── skills/            ←   6 个开发技能（流水线 ①→⑥）
│   ├── wiki/              ←   业务模型 / 接口协议 / 数据模型 / 架构决策
│   └── changes/           ←   变更追踪（按需求 ID 归档，状态机驱动）
├── smart-trip-common/    ← 公共模块: model, util, exception, DTO
├── smart-trip-agent-supervisor/ ← SupervisorAgent（主管编排 + trace 审计）
├── smart-trip-agent-xhs/      ← XHSAnalysisAgent（小红书笔记分析）
├── smart-trip-agent-route/    ← RouteAgent（双厂商地图路线规划）
├── smart-trip-agent-itinerary/ ← ItineraryAgent（行程编排 + 高德美食）
├── smart-trip-agent-budget/   ← BudgetAgent（费用四类拆分核算）
├── smart-trip-skills/    ← 业务 Skills（@Tool 类 / Service / trace / 治理 / 可观测）
├── smart-trip-server/    ← Spring Boot Web 服务（REST + DataSource 装配）
├── smart-trip-tests/     ← E2E + ArchUnit 架构约束
├── docker/                ← Docker Compose（Nacos + Redis + MySQL）
└── .Codex/               ← Codex 配置（settings.json 权限 + Hook）
```

> ⚠️ 两类"Skills"不要混淆：`.harness/skills/` 是**开发技能**（驱动"怎么造"）；`smart-trip-skills/` 是**业务 Skills**（Agent 运行时能力）。

---

## 3. 工作原则与开发流水线

**核心理念（Harness Engineering）**: 人类设计约束 → AI 沿流水线生产 → 机器验证每一步。Owner Agent 编排技能，严格按序、不可跳步，任一门禁不通过即退回或请示人类。

```
人类意图
   │  ① request-analysis   需求分析 → change.md（需求卡 + AC + 边界 + 影响面）
   │      └─ gate: 需求明确、AC 可测试、≥3 边界
   │  ② coding-skill       编码实现（小步可编译，遵守 .harness/rules/）
   │      └─ gate: mvn compile 通过 + 符合编码规范
   │  ③ unit-test-write    单测编写
   │      └─ gate: 测试通过 + 核心覆盖率 ≥80%
   │  ④ expert-reviewer    专家评审（7 维度）→ review.md
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

## 4. 多 Agent 架构（AgentScope v2）

```
SupervisorAgent（主管: ReAct 循环 + agent_spawn 委派）
  ├─→ XHSAnalysisAgent（小红书笔记分析 + RAG）
  ├─→ RouteAgent（路线规划 + 百度/高德双地图 MCP）
  ├─→ ItineraryAgent（行程编排 + 景点/餐厅推荐）
  └─→ BudgetAgent（费用统筹 + BigDecimal 精确核算）
```
- **原生路**: `agent_spawn` 框架机制（`workspace/subagents/<id>.md` 声明 → 父 Toolkit 白名单过滤 → 临时 HarnessAgent → ReAct → TOOL_RESULT）
- **兜底路**: A2A `Msg` 协议 + `SubAgentDispatcher`（确定性顺序编排）
- **收口**: `orchestrate_trip` @Tool → `TripOrchestrationService` → `OutputQualityGate`
- **双路一致**: 原生路与兜底路最终均走 `TripOrchestrationService.orchestrate()` 产出 `TripPlanResult`
- **审计**: MySQL `smart_trip_plan_trace` 异步追踪（SensitiveDataMasker 脱敏 + 有界线程池）
- **安全**: 子 Agent 强制叶子不可再 spawn（框架硬上限 3 层）；HITL 关键节点兜底

---

## 5. 关键约束速记

| 约束 | 规则 |
|------|------|
| 代码规范 | Alibaba Java Coding Guidelines |
| Javadoc | 所有 public API 必须有 |
| 测试覆盖 | Agent 核心逻辑 ≥ 80% |
| LLM 调用 | 超时 30s + 重试 3 次 + 降级默认值 |
| API 限频 | 小红书 ≤ 10/min，百度地图 ≤ 30/min |
| 安全 | API Key → 环境变量/配置中心（禁止硬编码，CI 密钥扫描 0 命中） |
| 审计追踪 | `SensitiveDataMasker` 脱敏 → 异步 MySQL，失败不阻塞主链路 |
| 递归保护 | 子 Agent 强制叶子不可 spawn（框架硬上限 3 层） |
| 提交粒度 | 一个任务一次 commit，message 含变更 ID（C-NNN） |

---

## 6. 常用命令与流水线技能

```
开发流水线（.harness/skills/，由 Owner Agent 编排，按序执行）
  ① request-analysis    需求分析 → .harness/changes/C-NNN/change.md
  ② coding-skill        编码实现
  ③ unit-test-write     单元测试编写（覆盖率 ≥80%）
  ④ expert-reviewer     专家评审（7 维度，0 严重问题）
  ⑤ unit-test-ci        CI 与质量门禁（Checkstyle/PMD/ArchUnit/测试）
  ⑥ deploy-verify       部署验证（冒烟 + 健康 + 回滚）

新变更建档:  cp -r .harness/changes/_TEMPLATE .harness/changes/C-NNN-<slug>
docker compose up -d    启动 Nacos + Redis + MySQL
mvn clean verify        编译 + 全量测试（563+ tests）

# 配置 API Key（禁止硬编码）
export DEEPSEEK_API_KEY=...       # 主 LLM
export DASHSCOPE_API_KEY=...      # 降级 LLM
export TRACE_DB_PASSWORD=...      # MySQL 审计追踪
```

---

## 7. 详细文档索引

| 文档 | 阅读时机 |
|------|----------|
| [.harness/agents/owner.md](.harness/agents/owner.md) | 任何任务开始 — Owner Agent 职责、决策边界、上下文策略 |
| [.harness/rules/工程结构.md](.harness/rules/工程结构.md) | 涉及目录/模块/架构 — 依赖方向 + ArchUnit 铁律 |
| [.harness/rules/编码规范.md](.harness/rules/编码规范.md) | 写代码前 — 命名/异常/LLM调用/安全/质量指标 |
| [.harness/rules/开发流程规范.md](.harness/rules/开发流程规范.md) | 推进任务时 — 六阶段流水线 + 门禁 + 变更状态机 |
| [.harness/skills/](.harness/skills/) | 进入对应阶段 — 各技能的 SKILL.md |
| [.harness/wiki/业务模型.md](.harness/wiki/业务模型.md) | 需求分析 — 实体/关系/业务不变量/HITL |
| [.harness/wiki/接口协议.md](.harness/wiki/接口协议.md) | 设计接口 — REST/A2A/MCP 契约 |
| [.harness/wiki/数据模型.md](.harness/wiki/数据模型.md) | 设计数据 — 领域模型 + Redis/ReMe/MySQL Schema |
| [.harness/wiki/架构决策.md](.harness/wiki/架构决策.md) | 做架构决策 — ADR-001~010 |
| [.harness/changes/](.harness/changes/) | 每个变更 — 建档、追踪、断点续传 |
