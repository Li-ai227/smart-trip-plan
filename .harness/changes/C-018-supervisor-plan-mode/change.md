---
id: C-018
slug: supervisor-plan-mode
status: verified
created: 2026-07-29
updated: 2026-07-29
owner: Owner Agent
---

# C-018 Supervisor 计划模式（AgentScope Plan Mode + 前端人工确认闭环）

## 用户故事

作为**旅游规划系统的用户**，我提交行程规划请求时可以**显式开启「计划模式」**：
Supervisor 先起草一份执行计划（Markdown 草案），前端暂停并展示草案供我确认/拒绝，
我确认后系统再委派四个子 Agent 执行完整规划，并在执行过程中实时展示进度。

> 本卡基于 **AgentScope Java Harness 官方 Plan Mode**
> (`HarnessAgent.builder().enablePlanMode()` + `enterPlanMode(ctx)`/`exitPlanMode(ctx)` 程序化 API，
> 参见 [官方文档](https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html))。
> 与 ADR-005 已废弃的旧 `PlanMode` 枚举（`PIPELINE/STATE_GRAPH/PLAN_NOTEBOOK`）**同名异物**，
> 本卡顺带清理该死代码。

## 非目标（Out of Scope）

- 不做复杂度自动判定（仅用户显式开启 `planMode=true`）。
- 不开 `allowShellInPlanMode()`（计划阶段 shell 默认被拒）。
- 不支持人工修改计划草案内容（仅 CONFIRM/REJECT 两种操作，MODIFY 返回 400）。
- 不改变现有 `review` HITL 语义（原有 `/intervene` 的 CONFIRM/MODIFY/REJECT 三动作不受影响）。
- 不做前端富文本展示（仅 Markdown 纯文本草案）。
- 不重新引入文件系统工具（`disableFilesystemTools()` 始终生效）。
- 子 Agent 不继承 Plan Mode 只读限制（AgentScope 已知缺口，当前无影响——计划阶段空 Toolkit 无子 Agent 可 spawn）。

## 验收标准（AC）

> ⚠️ 本变更的 AC 覆盖完整前端人工确认闭环，按用户交互流程组织：
> `后端生成草案 → 前端暂停展示草案 → 用户确认/拒绝 → 确认后执行规划调度（含进度展示） + 错误处理`

### 第一阶段：草案生成（后端 → 前端展示）

- **AC-1（草案展示）**: `TripPlanRequest` 新增可选字段 `planMode`（`Boolean`，默认 `false`，向后兼容）。
  仅当 `planMode=true` 时 `SupervisorHarnessAgentFactory` 进入规划态分支；`false`/未传时行为不变。
  `GET /api/v1/trip-plan/{planId}` 返回 `planMode` 字段供前端判断是否为计划模式请求。

- **AC-2（Plan Mode 草案起草）**: 规划态调用 `agent.enterPlanMode(ctx)` 程序化进入 Plan Mode，
  LLM 仅调 `plan_write` 起草计划后停止（不调 `plan_exit`）。Toolkit 仅含框架自动注册的
  `plan_enter/plan_write/plan_exit`，不注册任何业务 `@Tool` 类。同时保留 `disableFilesystemTools()`。

- **AC-3（草案持久化与前端展示）**: LLM 写入计划草案后，从 workspace `plans/PLAN.md` 读取全文。
  草案非空时进入 `PlanStatus.PLAN_REVIEW` 状态，同步写入：
  - **Redis**（快速查询）：`trip:plan:{planId}:draft`（String 类型，30d TTL，草案 Markdown 全文）
  - **MySQL**（持久化，重启不丢失）：`trip_plan_draft_record` 表，字段见 §6
  `GET /api/v1/trip-plan/{planId}` 读取优先 Redis（Redis 可用时），Redis 不可用时降级读 MySQL：
  - `status: "plan_review"` — 前端据此路由到草案展示面板
  - `planDraft: "<Markdown 草案全文>"` — 前端直接渲染
  - `plan: null` — 草案阶段无完整方案

### 第二阶段：用户确认/拒绝

- **AC-4（确认/拒绝操作 - intervene）**: `POST /api/v1/trip-plan/{planId}/intervene` 在
  `status=plan_review` 态下支持：
  - `action: "CONFIRM"`：立即返回 `status: "processing"`，以同一 `sessionId=planId` 重建 `HarnessAgent`（完整业务 Toolkit），
    调用 `agent.exitPlanMode(ctx)` 程序化退出 Plan Mode 后，在 `CompletableFuture.runAsync` 中异步执行四步编排
    （XHS→Route→Itinerary→Budget）。前端收到 `processing` 后立即跳转 `/planning/:planId` 进度页，
    通过轮询获取执行结果。
  - `action: "REJECT"`：状态置 `failed`，`failureReason: "用户拒绝执行计划"`，返回 `status: "failed"`，
    前端跳转 `/intent` 重新提交规划。
  - `action: "MODIFY"`：返回 `400 INVALID_REQUEST`，错误信息 `"plan_review 态不支持 MODIFY"`。

### 第三阶段：确认后异步执行与进度展示

- **AC-5（确认后异步执行进度展示）**: CONFIRM 后编排在 `CompletableFuture.runAsync` 中异步执行，
  前端通过 `GET /api/v1/trip-plan/{planId}` 轮询（建议 2s 间隔）获取执行进度：
  - `status: "planning"` → 正在执行子 Agent 编排
  - `status: "assembling"` → 行程编排中
  - `status: "budgeting"` → 预算核算中
  - `status: "review"` → 编排完成，等待最终 HITL 确认（若 `requiresHumanIntervention=true`）
  - `status: "confirmed"` → 编排完成，方案已确认可交付
  - `status: "failed"` → 执行失败，`failureReason` 字段说明原因
  - 草案执行期间 `planDraft` 字段保留原始草案（供前端对比查看）

### 第四阶段：错误处理

- **AC-6（草案生成失败 → 降级）**: LLM 未产出有效草案（`PLAN.md` 缺失或为空）→ 降级确定性直路，
  `fallback=true`，跳过 `plan_review` 态直接走原四步编排流程。前端通过 `fallback: true` 感知降级。

- **AC-7（恢复执行异常 → failed）**: CONFIRM 后恢复执行（`exitPlanMode` + 四步编排）过程中抛出异常，
  状态置 `failed`，`failureReason` 包含异常信息，`planDraft` 保留原始草案供排查。

- **AC-8（重复 CONFIRM 幂等）**: 重复 CONFIRM 请求在 `plan_review` 态以外返回 `400 INVALID_REQUEST`
  （"仅 plan_review 态可执行 intervene"），不会导致重复执行。

- **AC-9（Modify 拒绝）**: plan_review 态下 `action=MODIFY` 返回 `400 INVALID_REQUEST`，
  错误信息 `"plan_review 态不支持 MODIFY"`。

## 边界情况（≥3）

- **边界-1: `planMode=true` 但请求简单** → 仍遵循用户意图进入计划模式起草草案，不作复杂度预判。
- **边界-2: 服务重启后 `plan_review` 恢复** → Redis 丢失但 MySQL 中的 `trip_plan_draft_record` 记录完好，
  `loadDetail()` 从 MySQL 读取 `draft_status='plan_review'` 的草案，重建 `planDraft` 和 `plan_review` 状态。
- **边界-3: Redis 不可用（降级读 MySQL）** → Redis 宕机或 TTL 到期时，`GET /api/v1/trip-plan/{planId}`
  自动降级读 MySQL `trip_plan_draft_record` 表，草案展示不受影响（仅性能略降）。
- **边界-4: ReMe 记忆召回失败** → 计划草案不受影响，降级为无记忆草案生成。
- **边界-5: 子 Agent 委派降级** → 与 `planMode=false` 一致，降级后继续后续步骤，最终 `fallback=true`。
- **边界-6: 并发用户对同一 planId 操作** → `checkOwnership` 在 intervene 入口校验 userId，
  不同用户无权操作；同一用户重复 CONFIRM 走幂等守卫（AC-8）。
- **边界-7: 草案含敏感信息** → 草案经 `SensitiveDataMasker` 脱敏后写入 Redis 和 MySQL
  `trip_plan_draft_record.draft_markdown`，确保两端均无敏感信息落入持久层。
- **边界-8: MySQL 写入失败** → 草案写入 MySQL 失败时，仅 Redis 副本可用，不阻塞主链路；
  日志 WARNING 记录，草案仍可在当前会话中正常展示（重启后丢失）。

## 非功能需求

| 维度 | 指标 |
|------|------|
| 性能 | 草案生成 P95 < 20s（仅一次 LLM `plan_write` 调用，无业务工具调用）；`planMode=false` 路径 P95 < 30s（无新增开销） |
| 可靠性 | 草案生成全链路失败降级确定性直路（`fallback=true`）；CONFIRM 后恢复执行异常走 `failed` 态（`failureReason` 带异常信息）；MySQL 写入失败不阻塞主链路 |
| 持久性 | 草案同时写入 Redis（30d TTL）+ MySQL `trip_plan_draft_record`；服务重启后 Redis 丢失时从 MySQL 自动恢复草案，`plan_review` 态不受影响 |
| 安全 | 计划阶段禁用文件系统 (`disableFilesystemTools()`) + 不开启 shell (`allowShellInPlanMode` 不调用)；userId 隔离（`checkOwnership` 守卫 intervene/query）；`sessionId=planId` 隔离跨请求会话 |
| 可观测 | `plan.mode.*` 四项指标（`plan.mode.enter` / `plan.mode.draft` / `plan.mode.confirm` / `plan.mode.reject`）；`traceId`/`planId` 贯穿两阶段；`plan.mode.draft.empty` 降级计数 |

## 设计约束

- 必须放在 `smart-trip-skills` 模块（`SupervisorPlanModeSupport`/`PlanDraftPause`）和 `smart-trip-server` 模块（`PlanModeFacadeHelper`）。
- 不允许破坏 `planMode=false` 路径的向后兼容性（`Boolean.TRUE.equals()` 守卫，false/unset 行为不变）。
- 必须复用已有 `PlanCacheService`（Redis 草案存储）、`TripPlanResult`（`planPause` 字段）、`SupervisorAgentRunner`（`resume` 方法签名）。
- 不允许引入新的枚举或状态——复用 `PlanStatus.PLAN_REVIEW`（已在 C-018 之前预定义）。
- 必须删除旧 `com.smart.trip.common.enums.PlanMode` 枚举及其 `EnumContractTest` 断言（消除 ADR-005 残留命名歧义）。
- 计划阶段 Toolkit 必须为空（仅框架自动注册的工具），不注册任何业务 `@Tool` 类。
- 草案必须同时写入 Redis（快速查询）和 MySQL（持久化，重启不丢失），MySQL 写入失败不阻塞主链路。
- 草案读取策略：优先 Redis → 降级 MySQL → 返回空（`loadDetail()` 兜底）。
- `trip_plan_draft_record` 表必须使用 `MEDIUMTEXT` 类型存储草案 Markdown 全文（最大 16MB）。

## 契约影响

- **REST**: `POST /api/v1/trip-plan` 请求体新增 `planMode` 可选字段（Boolean，默认 false）；
  `GET /api/v1/trip-plan/{planId}` 响应新增 `planDraft` 字段（status=plan_review 时）；
  `POST /api/v1/trip-plan/{planId}/intervene` 在 `plan_review` 态下支持 CONFIRM/REJECT 动作。
- **A2A**: 无影响（Plan Mode 仅在 SupervisorAgent 内部实现，不改变 A2A 消息契约）。
- **数据模型**: `TripPlanRequest` 新增 `planMode` 字段；`TripPlanResult` 新增 `planPause` 字段（`PlanDraftPause` record）；
  新增 `trip_plan_draft_record` 表（`plan_id` + `draft_markdown` + `draft_status`）。
- **Redis / ReMe**: 新增 Key `trip:plan:{planId}:draft`（String 类型，30d TTL，草案 Markdown 全文）。
- **MySQL**: 新增 `V003__plan_draft.sql` 迁移脚本（`trip_plan_draft_record` 表 DDL，库 `smart_trip_plan_trace`）。

## 影响面

- **模块**:
  - `smart-trip-skills/supervisor/` — 新增 `SupervisorPlanModeSupport`、`PlanDraftPause`；修改 `SupervisorHarnessAgentFactory`（新增 `runPlanningPhase`、`resume` 方法）
  - `smart-trip-server/` — 新增 `PlanModeFacadeHelper`；新增 `PlanDraftRepository`（MySQL 持久化读写）；修改 `PlanCacheService`（草案存取增加 MySQL 写入）；修改 `TripPlanFacade`（新增 `executeOrchestration` 中 `handlePlanPause` 分流）
  - `smart-trip-common/` — 修改 `TripPlanRequest`（新增 `planMode` 字段）；修改 `TripPlanResult`（新增 `planPause` 字段）；删除 `PlanMode` 枚举
  - `smart-trip-front/` — 新增 `PlanReviewPanel.vue` / `PlanReviewPanelDesktop.vue`（草案展示组件）；修改 `IntentForm.vue`（切换开关）；修改 `PlanningView.vue`（`plan_review` 路由跳转）
- **SQL 迁移**: 新增 `docker/mysql/init/V003__plan_draft.sql`（`trip_plan_draft_record` 表 DDL）；新增 `smart-trip-server/src/main/resources/sql/plan-draft-schema.sql`（同名 DDL 供本地开发）
- **外部 API**: 无新增外部 API 依赖
- **wiki 更新**: `数据模型.md`（`TripPlanRequest` 字段表、Redis Key Schema 新增 `trip:plan:{planId}:draft`、`trip_plan_draft_record` 表结构）；`接口协议.md`（`planMode` 请求字段、`planDraft` 响应字段、`plan_review` 态 intervene 语义）；`架构决策.md`（ADR-005 补充 C-018 说明）

## 测试策略

| 层级 | 覆盖范围 | 工具/方法 |
|------|----------|----------|
| 单元测试 | `SupervisorPlanModeSupport`（`PLAN_SYS_PROMPT`、`buildPlanPrompt`、`readPlanDraft`、`sessionContext`、`PlanDraftPause`） | JaCoCo ≥80% |
| 单元测试 | `PlanModeFacadeHelper`（`handlePlanPause`、`intervenePlanReview` 三种 action） | JaCoCo ≥80% |
| 单元测试 | `PlanCacheService.storePlanDraft/getPlanDraft` | JaCoCo ≥80% |
| 单元测试 | `TripPlanRequest.planMode` 字段（默认 false、序列化、向后兼容） | JaCoCo ≥80% |
| 单元测试 | `CacheKeys.planDraftKey` | JaCoCo ≥80% |
| 单元测试 | `PlanDraftRepository`（`insertDraft` / `updateStatus` / `findByPlanId` / 重启后恢复流程） | JaCoCo ≥80% |
| 单元测试 | 旧 `PlanMode` 枚举删除后编译验证 | `mvn compile` |
| 集成测试 | CONFIRM 完整恢复链路（真实 Redis + MySQL + SupervisorAgent 集成） | `SupervisorHarnessAgentFactoryIT` |
| 集成测试 | LLM 未产出草案 → 降级确定性直路（`runPlanningPhase` → `SupervisorAgent.resolve`） | `SupervisorHarnessAgentFactoryIT` |
| 集成测试 | 服务重启后草案恢复：模拟 Redis 丢失 → 从 MySQL 恢复 `plan_review` 态 | `PlanDraftRecoveryIT` |
| 回归测试 | `planMode=false` 行为不变（现有 4 个子 Agent 编排不受影响） | 现有全量单测 |
| 前端组件测试 | `PlanReviewPanel` 展示草案 / CONFIRM 按钮 / REJECT 按钮 / 空草案降级提示 | Vue Test Utils + Vitest |
| 前端集成测试 | `plan_review` 态路由跳转、CONFIRM 后轮询恢复、草案展示数据流 | Cypress / Playwright |

## 验收用例

| # | 用例 | 前置 | 操作 | 预期 | 测试类型 |
|---|------|------|------|------|----------|
| 1 | `planMode=false` 零回归 | 提交 `planMode=false` 请求 | 观察编排流程 | 无 `plan_review` 态，直接四步编排，行为不变 | 回归 |
| 2 | `planMode=true` → plan_review → CONFIRM → 执行 | 提交 `planMode=true` 请求 | ① 查询状态 → ② 前端展示草案 → ③ 用户 CONFIRM → ④ 轮询进度 | ① `status=plan_review` + `planDraft` 非空 → ② 草案渲染 → ③ `exitPlanMode` + 异步编排 → ④ 状态逐步推进至 `confirmed`/`review` | 端到端 |
| 3 | `planMode=true` → REJECT → failed | 同上 | 用户 REJECT | `status=failed` + `failureReason="用户拒绝执行计划"` | 单元 |
| 4 | LLM 未产出草案 → 降级 | 提交 `planMode=true` 请求，LLM 不调 `plan_write` | 观察编排流程 | 跳过 `plan_review`，`fallback=true` 确定性直路 | 集成 |
| 5 | `planMode=true` → MODIFY → 400 | 同上 | 用户 MODIFY | `400 InvalidRequestException` + `"plan_review 态不支持 MODIFY"` | 单元 |
| 6 | CONFIRM 后执行异常 → failed | 同上 | 用户 CONFIRM + 编排过程抛出异常 | `status=failed` + `failureReason` 含异常信息 + `planDraft` 保留 | 集成 |
| 7 | 重复 CONFIRM 幂等 | 已 CONFIRM 的方案 | 再次 CONFIRM | `400 INVALID_REQUEST` + `"仅 review/plan_review 态可执行 intervene"` | 单元 |
| 8 | 前端草案展示面板 | `plan_review` 响应 + `planDraft` 非空 | 前端渲染草案 | `PlanReviewPanel` 展示 Markdown 草案 + CONFIRM/REJECT 按钮可见 + 空草案降级提示可见 | 前端组件 |
| 9 | 前端 CONFIRM 后轮询恢复 | 草案展示页面 | 点击 CONFIRM 按钮 | 按钮 loading 态 → 调 `intervene("CONFIRM")` → 跳转 `PlanningView` → 开始轮询 | 前端集成 |
| 10 | 前端 REJECT 后失败页面 | 草案展示页面 | 点击 REJECT 按钮 | 确认弹窗 → 调 `intervene("REJECT")` → 跳转失败页面 + 重试按钮 | 前端集成 |

## 设计

### 1. 完整人工确认闭环流程

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ 后端: 提交请求 │ ──▶ │ 后端: Plan Mode   │ ──▶ │ 前端: 展示草案    │ ──▶ │ 用户: 确认/拒绝   │
│ planMode=true  │     │ plan_write 起草   │     │ plan_review 态   │     │ CONFIRM/REJECT   │
│ → 202 Accepted │     │ → 存 Redis 草案  │     │ planDraft 渲染   │     │                  │
└──────────────┘     └──────────────────┘     └──────────────────┘     └──────────────────┘
                                                                               │
                                                                               ▼
                                                                     ┌──────────────────┐
                                                                     │ ┌─ CONFIRM:       │
                                                                     │ │ exitPlanMode    │
                                                                     │ │ + 异步四步编排   │
                                                                     │ │ + 进度轮询       │
                                                                     │ │                 │
                                                                     │ └─ REJECT:        │
                                                                     │   status=failed   │
                                                                     │   failureReason   │
                                                                     └──────────────────┘
                                                                               │
                                                                               ▼
                                                                     ┌──────────────────┐
                                                                     │ 前端: 跳转进度页   │
                                                                     │ /planning/:planId  │
                                                                     │ 轮询 status →     │
                                                                     │ confirmed → result │
                                                                     │ failed → 失败页   │
                                                                     └──────────────────┘
```

### 2. Plan Mode 装配（官方 API）

采用 AgentScope 官方 programmatic API，不依赖 LLM 调用 `plan_exit`：

| 阶段 | API | 说明 |
|------|-----|------|
| 进入 Plan Mode | `agent.enterPlanMode(ctx)` | 程序化进入，等价于 LLM 调 `plan_enter` |
| LLM 起草计划 | `agent.call(planPrompt, ctx).block()` | 只读阶段，仅 `plan_write` 可用 |
| 读取草案 | `SupervisorPlanModeSupport.readPlanDraft(workspace)` | 直接读 `plans/PLAN.md` |
| 返回 plan_review | `TripPlanResult.planPause=(draftMarkdown, "")` | 含草案全文 |
| CONFIRM 恢复 | `agent.exitPlanMode(ctx)` + `agent.call(execPrompt, ctx).block()` | 程序化退出 + 完整四步编排 |

其中 `ctx = RuntimeContext.builder().sessionId(planId).userId(userId).build()`，
确保不同请求的 AgentState 互不串扰（`sessionId=planId` 隔离）。

### 3. Toolkit 两段式装配

| 阶段 | Toolkit | 说明 |
|------|---------|------|
| **规划态**（`runPlanningPhase`） | 空 `Toolkit` + `enablePlanMode()` | 框架自动注册 `plan_enter/plan_write/plan_exit`；业务 `@Tool` 不注册 → 即使 `agent_spawn` 被调用也无业务工具 |
| **执行态**（`resume`/`run`） | 完整业务 Toolkit（5 个 `@Tool` 类 + `TripOrchestrationTools`） | 不调 `enablePlanMode()`；通过 `agent.exitPlanMode(ctx)` 程序化退出 |

### 4. 进度追踪机制

CONFIRM 后编排在 `CompletableFuture.runAsync` 中异步执行，通过以下机制实现进度展示：

1. **`SupervisorNativeRunContext.ProgressListener`** — 子 Agent 每步完成时触发进度通知
2. **Redis 状态轮询** — `GET /api/v1/trip-plan/{planId}` 返回实时 `status` 字段
3. **状态转换序列**：`plan_review` → CONFIRM → `planning` → `assembling` → `budgeting` → `review`/`confirmed`/`failed`

### 5. 与 C-017 历史规划持久化的兼容性

| 阶段 | 状态 | 触发 C-017 持久化 | 说明 |
|------|------|-------------------|------|
| 草案生成 | `plan_review` | ❌ 不触发 | 草案存 Redis + MySQL `trip_plan_draft_record`，不写入 C-017 业务表 |
| CONFIRM 后编排完成 | `CONFIRMED` | ✅ 触发 | `PlanModeFacadeHelper.handlePlanReviewConfirm()` 恢复编排到达终态后写入；同时将 `trip_plan_draft_record.draft_status` 更新为 `confirmed` |
| REJECT | `failed` | ❌ 不触发 | REJECT 路径写 `trip_plan_draft_record.draft_status=rejected`，不写入 C-017 规划详情 |
| CONFIRM 后编排失败 | `FAILED` | ✅ 触发 | 写入 FAILED 记录，days 为空，费用零值；`trip_plan_draft_record.draft_status=failed` |

### 6. MySQL 持久化：trip_plan_draft_record

**DDL**（`docker/mysql/init/V003__plan_draft.sql` + `smart-trip-skills/src/main/resources/sql/plan-draft-schema.sql`）：

```sql
CREATE TABLE trip_plan_draft_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id         VARCHAR(64)    NOT NULL COMMENT '方案 ID',
    user_id         VARCHAR(64)    NOT NULL COMMENT '用户 ID',
    plan_mode       TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否开启计划模式',
    draft_markdown  MEDIUMTEXT     NULL COMMENT '计划草案 Markdown 全文',
    draft_status    VARCHAR(32)    NOT NULL DEFAULT 'plan_review' COMMENT '草案状态（plan_review/confirmed/rejected/failed）',
    fallback        TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否降级（LLM 未产出草案时=1）',
    failure_reason  VARCHAR(2000)  NULL COMMENT '失败原因',
    created_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    UNIQUE INDEX uk_plan_id (plan_id),
    INDEX idx_user_id (user_id),
    INDEX idx_draft_status (draft_status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='计划模式草案持久化表（C-018）';
```

**写入时机**：
| 时机 | 操作 | 字段变化 |
|------|------|---------|
| `plan_review` 草案生成 | `INSERT` | `plan_id`, `user_id`, `plan_mode=1`, `draft_markdown`, `draft_status=plan_review`, `fallback=0` |
| CONFIRM 后编排开始 | `UPDATE` | `draft_status=confirmed`（实际编排异步进行，状态由 C-017 业务表管理） |
| REJECT | `UPDATE` | `draft_status=rejected`, `failure_reason` |
| 编排失败 | `UPDATE` | `draft_status=failed`, `failure_reason` |
| 降级直路（无草案） | 不写入 | 无 `plan_review` 态，直接走 C-017 持久化 |

**读取策略**：
1. `GET /api/v1/trip-plan/{planId}` 优先读 Redis `trip:plan:{planId}:draft`
2. Redis 无数据（重启/过期）→ 降级读 MySQL `trip_plan_draft_record` 中 `draft_status='plan_review'` 的记录
3. Redis 和 MySQL 均无数据 → 返回空（按现有 `loadDetail()` 兜底）

**数据清理**：
- `trip_plan_draft_record` 记录在 CONFIRM 后不删除（保留历史追溯能力）
- 草案不做自动 TTL 清理（与 C-017 业务表一致，根据 `created_at` 按需归档）
- 草案 Markdown 全文存 `MEDIUMTEXT`（最大 16MB，远超单次草案所需）

### 7. 前端交互细节

> 前端项目位于 `smart-trip-front/`，使用 Vue 3 + Pinia + TypeScript + Vite。
> 以下规范前端交互行为，确保与后端 Plan Mode 实现一一对应。

#### 7.1 计划模式开关（IntentForm）

| 元素 | 行为 |
|------|------|
| 位置 | `IntentForm.vue` / `IntentFormDesktop.vue` 表单底部 |
| 控件 | 切换开关 `switch-horiz`，绑定 `form.planMode` |
| 文案 | 开→"先起草计划再执行"；关→"直接执行" |
| 默认 | `false`（不开启，向后兼容） |
| 数据流 | `form.planMode` 随 `submit()` 传入 `POST /api/v1/trip-plan` 请求体 |

#### 7.2 草案展示面板（PlanReviewPanel）

**新增组件**: `PlanReviewPanel.vue` / `PlanReviewPanelDesktop.vue`

当 `GET /api/v1/trip-plan/{planId}` 返回 `status=plan_review` 时，前端路由跳转到 **草案展示页面**（复用 `plan` 路由，以 `plan_review` 态区分渲染）：

| 区域 | 内容 | 说明 |
|------|------|------|
| 顶部标题栏 | 📋 计划草案 · 目的地 · 天数 · 预算 | 显示请求概要 |
| 草案内容区 | Markdown 渲染（`v-html` 或 `marked` 库） | 展示 `planDraft` 字段全文 |
| 底部操作栏 | 两个按钮：[确认执行] / [拒绝] | 对应 CONFIRM / REJECT |
| 额外信息 | 预计耗时提示"确认后将进入 AI 规划阶段，约 30s~60s" | 管理用户预期 |

**草案渲染规则**:
- 草案 Markdown 直接渲染，不做富文本编辑（非目标）
- 草案为空时显示"草案生成失败，将直接进入规划阶段"（降级提示）
- 草案过长时添加滚动容器（max-height: 60vh）
- 预留 `planDraftVersion` 字段（未来扩展草案版本对比）

**状态转换**:
- `plan_review` → 用户 `CONFIRM` → 状态变 `planning` → 跳转 `PlanningView`（进度展示页）
- `plan_review` → 用户 `REJECT` → 状态变 `failed` → 显示失败原因
- `plan_review` → CONFIRM 后异常 → 状态变 `failed` → 显示错误信息 + 保留草案

#### 7.3 确认/拒绝交互流程

| 操作 | 前端行为 | 后端 API | 后端响应 |
|------|----------|----------|---------|
| **CONFIRM** | ① 按钮变"执行中..."（loading 态，禁用重复点击）→ ② 调 `POST /intervene {action:"CONFIRM"}` → ③ 后端返回 `{status:"planning"}` → ④ 前端跳转 `PlanningView` 开始轮询进度 | `POST /api/v1/trip-plan/{planId}/intervene` | `202 {status: "planning"}` 或 `200 {status: "planning", planId}` |
| **REJECT** | ① 弹出确认弹窗"确定拒绝此计划？"→ ② 调 `POST /intervene {action:"REJECT"}` → ③ 后端返回 `{status:"failed", failureReason:"用户拒绝执行计划"}` → ④ 前端显示失败页面 + 重试入口 | 同上 | `200 {status: "failed", failureReason: "用户拒绝执行计划"}` |
| **MODIFY** | 按钮禁用/gray 态，hover 提示"计划草案暂不支持修改" | 同上 | `400 INVALID_REQUEST` |

**CONFIRM 按钮防重复**:
- 点击后立即置 `loading` 态，按钮文案变"确认中..."
- 请求完成后恢复或跳转
- 后端幂等守卫保证重复请求返回 400

#### 7.4 确认后异步执行进度展示（PlanningView 增强）

`planMode=true` 的 CONFIRM 后端异步执行编排，**复用现有 `PlanningView.vue` + `AgentProgress.vue` 组件**：

| 状态 | AgentProgress 渲染 | 说明 |
|------|-------------------|------|
| `planning` | XHS: 运行中 / Route: 等待 / Itinerary: 等待 / Budget: 等待 | 开始执行 |
| `assembling` | XHS: 完成 / Route: 完成 / Itinerary: 运行中 / Budget: 等待 | 行程编排中 |
| `budgeting` | XHS: 完成 / Route: 完成 / Itinerary: 完成 / Budget: 运行中 | 预算核算中 |
| `review` | 全部完成，跳转 ResultView + HITL 弹窗 | 执行完成，进入最终确认 |
| `confirmed` | 全部完成，跳转 ResultView 展示最终方案 | 规划完成 |
| `failed` | 显示失败状态 + 保留草案 + 重试/返回按钮 | 执行失败 |

**已实现的轮询机制**（`trip-plan.ts` store）：
```typescript
// status 驱动轮询终止条件
const stopStatuses = ['plan_review', 'review', 'confirmed', 'failed']
// 轮询间隔 2s（config.pollIntervalMs）
// 轮询 URL: GET /api/v1/trip-plan/{planId}
```

**plan_review 路径的轮询流程**:
1. 用户提交 `planMode=true` → 返回 `planId` → 前端 `startPolling(planId)`
2. 轮询到 `status=plan_review` → 停止轮询，跳转草案展示页
3. 用户 CONFIRM → 调 `intervene("CONFIRM")` → 后端返回 `status=planning`
4. 前端手动启动新轮询 → 轮询至终态（`review/confirmed/failed`）

#### 7.5 草案与最终结果对比

| 展示阶段 | 前端组件 | 展示内容 |
|----------|---------|---------|
| `plan_review` | PlanReviewPanel | 草案 Markdown（纯文本） |
| 执行中 `planning→budgeting` | AgentProgress | 4 Agent 进度条 |
| 执行完成 `review` | ItineraryTimeline + HitlSheet | 完整行程方案 + HITL 弹窗 |
| 终态 `confirmed` | FeedbackExport | 最终方案 + 导出/反馈 |

#### 7.6 前端错误处理 UI

| 错误场景 | 用户看到 | 操作选项 |
|----------|---------|---------|
| 草案生成失败（降级直路） | 无 `plan_review` 态，直接进入 `planning` 进度页 | 无特殊操作 |
| CONFIRM 后执行失败 | `plan_review` 草案展示 + 失败提示 + 错误原因 | ① 返回修改意图重新提交 ② 联系支持 |
| 草案为空（Redis 丢失） | "草案加载失败，请刷新重试" | 刷新页面 |
| 网络错误 | 错误提示 + 重试按钮 | 重试 / 返回首页 |
| 重复 CONFIRM | 提示"方案已在执行中" | 跳转进度页 |
| REJECT 后 | 失败页面 + 原因"已拒绝执行计划" + 重新规划按钮 | ① 重新提交（返回 IntentForm） ② 查看历史方案 |

#### 7.7 前端组件变更清单

| 组件 | 变更类型 | 变更内容 |
|------|---------|---------|
| `IntentForm.vue` | ✅ 已有 | 添加 `planMode` 开关（第 133-139 行） |
| `IntentFormDesktop.vue` | ✅ 已有 | 添加 `planMode` 开关 |
| `models/trip-plan.ts` | ✅ 已有 | `TripPlanRequest.planMode` + `PlanStatus.plan_review` + `TripPlanDetail.planDraft` |
| `services/trip-plan.ts` | ✅ 已有 | 无需变更（`intervenePlan` 已支持三种 action） |
| `stores/trip-plan.ts` | ✅ 已有 | `startPolling` 已处理 `plan_review` 终止条件；`intervene` 已支持三种 action |
| `constants/mock.ts` | ✅ 已有 | `mockPlanDraft` 含完整 Markdown 草案 |
| `PlanningView.vue` | ✅ 已有 | `watch` 中 `plan_review` 跳转处理（第 68 行） |
| `PlanReviewPanel.vue` | **🔶 新增** | 草案展示组件（Markdown 渲染 + CONFIRM/REJECT 按钮） |
| `PlanReviewPanelDesktop.vue` | **🔶 新增** | 草案展示组件桌面端 |

#### 7.8 路由变更

```
// 新增路由（或复用现有 plan 路由，以 status 区分）
{
  path: '/plan/:planId',
  name: 'plan',
  // 根据 status 动态渲染:
  //   plan_review → PlanReviewPanel
  //   review → ItineraryTimeline + HitlSheet
  //   confirmed → FeedbackExport
  //   planning/assembling/budgeting → AgentProgress
}
```

当前路由设计（`router/index.ts`）已按 `status` 分发：
- `planning` 态 → `PlanningView`（AgentProgress）
- `plan_review` 态 → `PlanReviewPanel`（草案展示）
- `review` 态 → `HitlSheet`（HITL 确认）
- `confirmed` 态 → `ResultView`（最终方案 + 导出）
- `failed` 态 → 失败页面

### 8. 错误处理矩阵

| 错误场景 | 兜底策略 | 前端表现 |
|----------|---------|---------|
| LLM 未产出草案 | 降级确定性直路，`fallback=true` | 无 `plan_review` 态，直接出结果 |
| CONFIRM 后恢复异常 | 状态置 `failed`，`failureReason` 带异常信息 | 展示失败页面 + 原始草案 + 错误原因 |
| 草案存储 Redis 失败 | 日志 WARNING，不阻塞主链路 | 草案丢失，前端展示空草案 |
| 草案存储 MySQL 失败 | 日志 WARNING，仅 Redis 副本可用，重启后不可恢复 | 草案在当前会话正常展示，重启后恢复 |
| 重启后恢复草案 | MySQL `trip_plan_draft_record` 自动恢复，重建 `plan_review` 态 | 草案正常展示，无异常感知 |
| 重复 CONFIRM | 幂等守卫，返回 400 | 前端提示操作无效 |
| userId 不匹配 | 403 FORBIDDEN | 前端提示无权限 |

### 9. 前端组件设计规范

#### 9.1 PlanReviewPanel.vue 组件接口

```typescript
// Props
interface PlanReviewPanelProps {
  planId: string          // 方案 ID
  planDraft: string       // 草案 Markdown 全文
  destination: string     // 目的地（用于标题）
  days: number           // 天数
  budget: number         // 预算
  loading?: boolean       // 操作中（CONFIRM/REJECT 请求中）
  error?: string          // 错误信息
}

// Emits
// 'confirm' — 用户点击确认执行 → 无参数
// 'reject' — 用户点击拒绝 → 无参数
// 'retry' — 用户点击重试（错误时）→ 无参数
```

#### 9.2 状态驱动的 UI 行为矩阵

| 状态 | PlanReviewPanel | CONFIRM 按钮 | REJECT 按钮 | 轮询行为 |
|------|----------------|-------------|-------------|---------|
| `plan_review`（草案就绪） | 展示草案 | 可用（文案:"确认执行"） | 可用（文案:"拒绝"） | 停止轮询 |
| `plan_review`（草案为空） | 展示降级提示 | 隐藏（已降级直路） | 隐藏 | 停止轮询 |
| CONFIRM 请求中 | 草案展示 + loading 遮罩 | 禁用（文案:"确认中..."） | 禁用 | 停止轮询 |
| CONFIRM 成功 → `planning` | 跳转 PlanningView | — | — | 开始新轮询 |
| CONFIRM 失败（400 幂等） | 草案展示 + 错误提示 | 可用（恢复） | 可用 | 停止轮询 |
| REJECT 成功 → `failed` | 跳转失败页面 | — | — | 停止轮询 |
| REJECT 失败 | 草案展示 + 错误提示 | 可用 | 可用 | 停止轮询 |

#### 9.3 草案 Markdown 渲染安全规则

- 使用 `marked` 库渲染 Markdown，禁止 `v-html` 直接插入未处理内容
- 渲染前对草案内容做 XSS 过滤（`DOMPurify.sanitize()`）
- 草案内容来自 LLM 产出，可能包含特殊字符或 HTML 标签

#### 9.4 加载状态与骨架屏

- 草案加载中：显示骨架屏（Skeleton）占位，标题栏 + 3 行文本骨架
- CONFIRM 请求中：草案内容保持可见，底部按钮置 loading 态，文案变"确认中..."
- REJECT 请求中：草案内容保持可见，底部按钮置 loading 态，文案变"拒绝中..."

## 任务拆解（TDD）

> 遵循 TDD 模式：先写失败测试 → 最小实现 → 重构。

- [ ] T-1: `TripPlanRequest` 新增 `planMode` 字段 + 旧 `PlanMode` 枚举删除 + 编译验证 · 优先级 P0 · 模块 common
- [ ] T-2: `SupervisorPlanModeSupport` 纯 Java 装配（`PLAN_SYS_PROMPT`、`buildPlanPrompt`、`readPlanDraft`、`sessionContext`） + `PlanDraftPause` record · 优先级 P0 · 模块 skills
- [ ] T-3: `SupervisorHarnessAgentFactory` 规划态分叉（`runPlanningPhase` + `enablePlanMode()` + 空 Toolkit） · 优先级 P0 · 依赖 T-2 · 模块 skills
- [ ] T-4: `PlanCacheService` 草案存取（`storePlanDraft`/`getPlanDraft`）+ `CacheKeys.planDraftKey` · 优先级 P0 · 模块 server
- [ ] T-5: `PlanModeFacadeHelper` 草案暂停 + 三种 action 响应（CONFIRM/REJECT/MODIFY） · 优先级 P0 · 依赖 T-3/T-4 · 模块 server
- [ ] T-6: `TripPlanFacade.executeOrchestration` 中 `handlePlanPause` 分流 + `planMode=true` 编排分叉 · 优先级 P0 · 依赖 T-5 · 模块 server
- [ ] T-7: 前端进度展示：CONFIRM 后异步编排状态轮询（`planning`→`assembling`→`budgeting`→`review`/`confirmed`/`failed`） · 优先级 P1 · 依赖 T-6 · 模块 server
- [ ] T-8: 错误处理与降级测试（LLM 未产出草案降级、恢复异常 failed、重复 CONFIRM 幂等） · 优先级 P1 · 依赖 T-6 · 模块 skills/server
- [ ] T-9: 专家评审修复（若有） · 优先级 P2 · 依赖 T-1~T-8
- [x] T-10: wiki 同步（数据模型 / 接口协议 / 架构决策 ADR-005 补充） · 优先级 P2 · 依赖 T-1~T-9
- [ ] T-11: `PlanReviewPanel.vue` / `PlanReviewPanelDesktop.vue` 草案展示组件（Markdown 渲染 + CONFIRM/REJECT 按钮 + 骨架屏 + 空草案降级提示） · 优先级 P1 · 依赖 T-6 · 模块 front
- [ ] T-12: 前端草案展示路由 + 草案展示页面集成（IntentForm 开关 + PlanningView plan_review 跳转 + 轮询流程） · 优先级 P1 · 依赖 T-11 · 模块 front
- [ ] T-13: `PlanDraftRepository` MySQL 持久化（`insertDraft` / `updateStatus` / `findByPlanId`）+ `V003__plan_draft.sql` 迁移脚本 · 优先级 P0 · 依赖 T-4 · 模块 server
- [ ] T-14: `PlanCacheService` 草案存取增加 MySQL 双层写入（Redis + MySQL 同步写入，MySQL 失败不阻塞） · 优先级 P0 · 依赖 T-13 · 模块 server
- [ ] T-15: 服务重启后草案恢复：Redis 丢失时从 MySQL 自动恢复 `plan_review` 态 + `PlanDraftRecoveryIT` 集成测试 · 优先级 P1 · 依赖 T-14 · 模块 server

## 流水线进度

- [ ] ① 需求分析（analyzing）
- [ ] ② 编码实现（coding）
- [ ] ③ 单测编写（testing，覆盖率 ≥80%）
- [x] ④ 专家评审（reviewing，0 🔴 严重问题）→ review.md
- [x] ⑤ CI 门禁（ci，全绿）
- [x] ⑥ 部署验证（verifying）→ verify.md
- [x] 交付（done，wiki 已同步）