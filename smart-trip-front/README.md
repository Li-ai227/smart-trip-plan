# smart-trip-front

智能旅游规划多智能体系统 **前端**（Web 端规划工作台）。

由 `前端效果图-小红书风格.html` 单页效果图**按模块拆分**而来，技术栈与目录约定参考 `agent-front`：
**Vite + Vue 3 + TypeScript + vue-router + Pinia + Tailwind CSS**，设计语言为小红书风格（品牌红 `#FF2442`）。

> 消费 `smart-trip-server` 的 REST API（对齐 `.harness/wiki/接口协议.md`），不直接调用 Agent（符合工程结构 R3 分层）。详见《AgentScope 自主规划实战-重构大纲》第 25 章。

## 快速开始

```bash
npm install
npm run dev        # 启动开发服务器
npm run build      # 类型检查 + 生产构建
```

后端（`smart-trip-server`）尚未实现，默认 `src/config/index.ts` 中 `useMock = true`，
前端用 `src/constants/mock.ts` 的「云南 5 日 · 情侣 · ¥8000」示例数据完整跑通流程与效果图。
后端就绪后将 `useMock` 改为 `false`，并把 `apiPrefix` 指向服务地址即可。

## 路由与页面（交互流程）

| 路由 | 页面 | 触发/接口 |
|------|------|-----------|
| `/intent` | 意图输入 | `POST /api/v1/trip-plan` |
| `/planning/:planId` | 规划进行中（轮询） | `GET /api/v1/trip-plan/{planId}` |
| `/notes/:planId` | 小红书笔记溯源 | XHSAnalysisAgent 产出 |
| `/plan/:planId` | 行程结果 + HITL 介入 | `POST /{planId}/intervene` |
| `/result/:planId` | 确认 · 导出 · 反馈 | `POST /{planId}/feedback` |
| `/gallery` | **效果图墙**（6 模块并排） | — |

## 模块 → 组件映射（按模块拆分放置）

| # | 模块 | 组件 |
|---|------|------|
| 1 | 意图输入页 | `components/intent/IntentForm.vue` |
| 2 | 规划进行中（5 Agent 协作） | `components/planning/AgentProgress.vue` |
| 3 | 小红书笔记溯源（瀑布流） | `components/notes/NoteWaterfall.vue` + `NoteCard.vue` |
| 4 | 行程结果页（时间轴） | `components/itinerary/ItineraryTimeline.vue` |
| 5 | 预算面板 + HITL | `components/budget/BudgetPanel.vue` + `components/hitl/HitlSheet.vue` |
| 6 | 确认 · 导出 · 反馈 | `components/feedback/FeedbackExport.vue` |
| — | 手机外壳 | `components/PhoneFrame.vue` |

## 目录结构

```
src/
├── main.ts / App.vue
├── assets/styles/main.css     # 小红书设计系统（design tokens + 组件类）
├── config/index.ts            # apiPrefix / useMock / 轮询间隔
├── router/index.ts
├── models/                    # base.ts / trip-plan.ts（对齐接口协议+业务模型）
├── services/trip-plan.ts      # REST API 封装（submit/get/intervene/feedback）
├── utils/request.ts           # fetch 封装（前缀/超时/JSON）
├── stores/trip-plan.ts        # Pinia：提交、轮询状态机、HITL、反馈
├── constants/mock.ts          # Mock 数据（云南 5 日示例）
├── components/                # 6 模块组件 + PhoneFrame
└── views/
    ├── layouts/DefaultLayout.vue
    ├── pages/                 # IntentView/PlanningView/NotesView/ItineraryView/ResultView
    └── GalleryView.vue        # 效果图墙
```
