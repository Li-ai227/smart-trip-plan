# smart-trip-plan

> 基于 **AgentScope Java 2.0** 的小红书旅游规划**多智能体系统** —— 5 个专业 Agent 自主协作，把"想去哪玩"变成一份可执行、可控费的旅行方案。
>
> 本项目同时是一次 **Harness Engineering（约束工程）+ SDD + TDD** 的工程实践：人类设计约束，AI 沿流水线生产，机器验证每一步。

[![JDK](https://img.shields.io/badge/JDK-21_LTS-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.x-green)]()
[![AgentScope](https://img.shields.io/badge/AgentScope-2.0.0--RC1-blue)]()
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-lightgrey)]()

---

## ✨ 这是什么

输入一句话需求（如「五一去成都玩 4 天，人均 3000，喜欢美食和小众景点」），系统会自主完成：

1. **笔记分析** —— 检索并解析小红书旅游笔记，提炼真实玩法与避坑点（RAG）；
2. **路线规划** —— 结合地图能力规划城市间 / 城市内动线，控制通勤成本；
3. **行程编排** —— 按天编排景点、餐厅、住宿，平衡节奏与偏好；
4. **费用统筹** —— 拆解预算、实时核算，超支自动预警；
5. **人机协同** —— 关键节点（超预算 ≥15%、方案确认）触发 **Human-in-the-Loop**，由你拍板。

最终交付一份结构化、可调整的旅行计划。

---

## 🧠 多 Agent 架构（AgentScope v2）

```
SupervisorAgent  ── 主管：ReAct 循环 + agent_spawn 委派
   ├─→ XHSAnalysisAgent   小红书笔记分析 + RAG
   ├─→ RouteAgent         路线规划 + 百度/高德双地图 MCP
   ├─→ ItineraryAgent     行程编排 + 景点/餐厅推荐
   └─→ BudgetAgent        费用统筹 + 预算管控（BigDecimal 精确核算）
```

- **通信**：原生路经 `agent_spawn`（AgentScope 框架内部机制）；确定性兜底路经 A2A `Msg` 协议 + `SubAgentDispatcher`
- **编排**：HarnessAgent ReAct 循环，LLM 自然顺序调用工具；复杂需求走 Plan Mode + Task List 分解
- **决策**：LLM 负责意图理解 + 委派顺序 + 上下文传递，不直接产出行程数据；业务不变量由确定性 Service 强制
- **收口**：`orchestrate_trip` @Tool → `TripOrchestrationService` → `OutputQualityGate` 复核
- **审计**：MySQL `smart_trip_plan_trace` 三表（delegation/llm_call/summary）异步追踪全链路
- **安全**：子 Agent 强制叶子不可递归 spawn（框架硬上限 3 层）；关键节点 HITL 兜底

---

## 🛠 技术栈

| 层 | 选型 | 版本 |
|----|------|------|
| 语言 | JDK | 21 LTS |
| 框架 | Spring Boot | 4.0.x |
| Agent | AgentScope Java | 2.0.0-RC1 |
| LLM | DeepSeek `deepseek-v4-pro`（主）/ DashScope `qwen3-max`（降级） | ADR-009 |
| 构建 | Maven | 3.9+ |
| 服务发现 | Nacos | 3.2.x |
| 缓存/记忆 | Redis | 7.x |
| 审计追踪 | MySQL 8.0 + HikariCP | `smart_trip_plan_trace` |
| 前端 | Vue 3 + Vite + TypeScript + Tailwind CSS 4 | — |

> **铁律**：选"无聊"技术，不引入未经评审的依赖，重新实现优于包装第三方。

---

## 📁 项目结构

```
smart-trip-plan/
├── README.md              ← 当前文件（面向人类的项目说明）
├── CLAUDE.md / AGENTS.md  ← 面向 AI 协作者的入口地图
├── .harness/              ← 🔗 Harness 约束体系（工程真相来源）
│   ├── agents/            ←   Owner Agent 定义（总编排者）
│   ├── rules/             ←   工程结构 / 编码规范 / 开发流程 / SDD-TDD / 运行时可靠性
│   ├── skills/            ←   6 个开发技能（流水线 ①→⑥）
│   ├── wiki/              ←   业务模型 / 接口协议 / 数据模型 / 架构决策
│   └── changes/           ←   变更追踪（按需求 ID 归档，状态机驱动）
├── smart-trip-front/     ← 前端（Vue 3，小红书风格 UI）✅ 已搭建
│
│   ── 以下后端模块已按流水线全部落地 ──
├── smart-trip-common/    ← 公共模块：model / util / exception / DTO / 枚举
├── smart-trip-agent-supervisor/ ← SupervisorAgent（主管编排 + trace 审计）
├── smart-trip-agent-xhs/      ← XHSAnalysisAgent（小红书笔记分析）
├── smart-trip-agent-route/    ← RouteAgent（双厂商地图路线规划）
├── smart-trip-agent-itinerary/ ← ItineraryAgent（行程编排 + 高德美食）
├── smart-trip-agent-budget/   ← BudgetAgent（费用四类拆分核算）
├── smart-trip-skills/    ← 业务 Skills（@Tool 类 / Service / trace / 治理 / 可观测）
├── smart-trip-server/    ← Spring Boot Web 服务（REST + 配置装配）
├── smart-trip-tests/     ← E2E + ArchUnit 架构约束
└── docker/                ← Docker Compose（Nacos + Redis + MySQL）
```

> ⚠️ 两类"Skills"不要混淆：`.harness/skills/` 是**开发技能**（驱动"怎么造"）；`smart-trip-skills/` 是**业务 Skills**（Agent 运行时能力）。

---

## ⚙️ 开发模式：SDD + TDD + Harness

本项目采用三层协同的开发模式：

- **SDD**（规格驱动）：先把需求落到 `change.md` —— 用户故事 + 验收标准 + 边界 + 设计约束 + 测试策略；
- **TDD**（测试先行）：按 `Red → Green → Refactor` 实现核心业务逻辑；
- **Harness**（约束工程）：流程门禁、全程留档、机器验证收口。

开发流水线由 Owner Agent 编排，**严格按序、不可跳步**，任一门禁不通过即退回或请示人类：

```
人类意图
  ① request-analysis  规格构建 → change.md         gate: AC 可测试 / ≥3 边界 / 测试策略清晰
  ② coding-skill      测试先行实现（先红后绿）      gate: 失败测试先存在 / mvn compile 通过
  ③ unit-test-write   补齐边界 / 降级 / 回归测试     gate: 测试通过 / 核心覆盖率 ≥80%
  ④ expert-reviewer   专家评审（含 SDD/TDD 合规）    gate: 0 个 🔴 严重问题
  ⑤ unit-test-ci      CI 门禁（静态分析+架构约束）   gate: CI 全绿
  ⑥ deploy-verify     部署验证（冒烟+健康+回滚）     gate: 验证通过
交付（change 状态 = done，同步相关 wiki）
```

详见 [.harness/rules/开发流程规范.md](.harness/rules/开发流程规范.md)。

---

## 🚀 快速开始

### 前端

```bash
cd smart-trip-front
npm install
npm run dev        # 启动开发服务器
npm run build      # 类型检查 + 生产构建
```

### 后端

```bash
# 启动基础设施
docker compose up -d    # Nacos + Redis + MySQL（可选）

# 初始化审计数据库（使用 MySQL trace 功能时）
mysql -h 127.0.0.1 -u root -p < \
  smart-trip-agent-supervisor/src/main/resources/sql/schema.sql

# 编译 + 全量测试
mvn clean verify        # 563+ tests, all green

# 配置 API Key（禁止硬编码，走环境变量）
export DEEPSEEK_API_KEY=...       # 主 LLM deepseek-v4-pro
export DASHSCOPE_API_KEY=...      # 降级 LLM qwen3-max
export BAIDU_MAP_AK=...           # 百度地图
export TRACE_DB_PASSWORD=...      # MySQL 审计追踪密码

# 启动服务
mvn -pl smart-trip-server spring-boot:run
```

---

## 📌 关键约束速记

| 约束 | 规则 |
|------|------|
| 代码规范 | Alibaba Java Coding Guidelines |
| Javadoc | 所有 public API 必须有 |
| 测试覆盖 | Agent 核心逻辑 ≥ 80%（JaCoCo + ArchUnit 门禁） |
| LLM 调用 | 超时 30s + 重试 3 次 + 降级默认值 |
| API 限频 | 小红书 ≤ 10/min，百度地图 ≤ 30/min |
| 安全 | API Key → 环境变量（禁止硬编码，CI 密钥扫描 0 命中） |
| 审计追踪 | `SensitiveDataMasker` 脱敏后异步落 MySQL，失败不阻塞主链路 |
| 递归保护 | 子 Agent 强制叶子不可再 spawn（框架硬上限 3 层） |
| 提交粒度 | 一个任务一次 commit，message 含变更 ID（C-NNN） |

---

## 📊 进度概览

| Phase | 变更 | 状态 |
|-------|------|------|
| 0 工程地基 | C-001~C-004 | ✅ done |
| 1 协同基座 | C-005（A2A/治理/Gate） | ✅ done |
| 2 专业 Agent | C-006(XHS) / C-007(Route) / C-008(Itinerary) / C-009(Budget) | ✅ done |
| 3 编排与对外 | **C-010(Supervisor)** | ✅ **done** |
| 3 编排与对外 | C-011(Server REST) | analyzing |
| 4 端到端 | C-012(E2E) / C-013(前端) / C-014(PDF) | analyzing |

---

## 📚 文档索引

| 文档 | 阅读时机 |
|------|----------|
| [CLAUDE.md](CLAUDE.md) / [AGENTS.md](AGENTS.md) | AI 协作入口 — 项目地图与约束总览 |
| [.harness/agents/owner.md](.harness/agents/owner.md) | 任何任务开始 — Owner Agent 职责与决策边界 |
| [.harness/rules/工程结构.md](.harness/rules/工程结构.md) | 涉及目录/模块/架构 — 依赖方向 + ArchUnit 铁律 |
| [.harness/rules/编码规范.md](.harness/rules/编码规范.md) | 写代码前 — 命名/异常/LLM 调用/安全 |
| [.harness/rules/开发流程规范.md](.harness/rules/开发流程规范.md) | 推进任务 — 六阶段流水线 + 门禁 + 状态机 |
| [.harness/rules/SDD-TDD模式.md](.harness/rules/SDD-TDD模式.md) | 写核心业务前 — 规格真相、测试先行 |
| [.harness/rules/运行时可靠性.md](.harness/rules/运行时可靠性.md) | 上线前 — 熔断/降级/容量/回滚 |
| [.harness/wiki/](.harness/wiki/) | 业务模型 / 接口协议 / 数据模型 / 架构决策 |

---

## 📄 License

本项目基于 [Apache License 2.0](LICENSE) 开源。
