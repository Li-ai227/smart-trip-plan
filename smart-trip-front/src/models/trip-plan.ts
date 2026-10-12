/**
 * 行程规划领域模型 —— 字段对齐：
 *   .harness/wiki/接口协议.md（REST 契约）
 *   .harness/wiki/业务模型.md（核心业务实体）
 *   .harness/wiki/数据模型.md（字段级定义）
 */

// 旅行风格（业务模型 §5）
export type TravelStyle = 'FAMILY' | 'COUPLE' | 'SOLO' | 'TEAM'

// 规划生命周期状态（接口协议 §1.2 / 业务模型 §6.1）
export type PlanStatus =
  | 'analyzing'
  | 'planning'
  | 'plan_review'
  | 'assembling'
  | 'budgeting'
  | 'review'
  | 'confirmed'
  | 'failed'

// 人工介入动作（接口协议 §1.3）
export type InterveneAction = 'CONFIRM' | 'MODIFY' | 'REJECT'

// 反馈评分
export type FeedbackRating = 'UP' | 'DOWN'

// ① 提交规划请求体（POST /api/v1/trip-plan）
export interface TripPlanRequest {
  destination: string
  startDate: string
  days: number
  budget: number
  headcount: number
  travelStyle: TravelStyle
  preferences: string[]
  /** C-018 AC-1：开启计划模式（先起草计划等待确认，再执行） */
  planMode?: boolean
}

// 提交响应（202 Accepted）
export interface TripPlanCreated {
  planId: string
  status: PlanStatus
}

// 地理位置
export interface GeoLocation {
  longitude: number
  latitude: number
  address: string
  city: string
}

// 景点
export interface Attraction {
  id: string
  name: string
  location?: GeoLocation
  rating: number
  durationMinutes: number
  ticket: number
  tags?: string[]
  emoji?: string
}

// 餐饮安排
export interface Meal {
  name: string
  mealType: '早' | '午' | '晚'
  perCapita: number
  recommend?: string
  distanceKm?: number
}

// 两点间路线
export interface Route {
  fromName: string
  toName: string
  distanceKm: number
  durationMinutes: number
  mode: '步行' | '驾车' | '骑行' | '公交'
  fallback?: boolean
}

// 单日行程
export interface TripDay {
  date: string
  index: number
  cityLabel: string
  attractions: Attraction[]
  meals: Meal[]
  routes: Route[]
  dailyCost: number
}

// 预算与费用拆分（四类：交通/住宿/餐饮/门票）
export interface BudgetBreakdown {
  transport: number
  hotel: number
  food: number
  ticket: number
}

export interface Budget {
  total: number
  used: number
  overrunRate: number
  breakdown: BudgetBreakdown
}

// 完整行程方案（最终交付物）
export interface TripPlan {
  days: TripDay[]
  totalCost: number
  budget: Budget
}

// 查询规划进度/结果响应（GET /api/v1/trip-plan/{planId}）
export interface TripPlanDetail {
  planId: string
  status: PlanStatus
  plan?: TripPlan
  requiresHumanIntervention: boolean
  // HITL 提示信息（review 阶段）
  interventionReason?: string
  interventionAdvice?: string
  request?: TripPlanRequest
  /** C-018 AC-3：计划草案 Markdown（plan_review 阶段） */
  planDraft?: string
}
  /** C-018 AC-3：计划草案 Markdown（plan_review 阶段） */

// 小红书笔记（XHSAnalysisAgent 分析输入 → 候选 Attraction）
export interface XHSNote {
  id: string
  title: string
  author: string
  likes: number
  mention?: string
  emoji: string
  coverClass: 'c1' | 'c2' | 'c3' | 'c4'
}

// 单个 Agent 在规划过程中的进度（用于「规划进行中」可视化）
export interface AgentProgressItem {
  key: 'xhs' | 'route' | 'itinerary' | 'budget'
  name: string
  emoji: string
  desc: string
  state: 'done' | 'running' | 'waiting'
}

// 方案摘要（历史列表卡片用）
export interface PlanSummary {
  planId: string
  destination: string
  startDate: string
  days: number
  budget: number
  totalCost: number | null
  headcount: number
  travelStyle: TravelStyle | null
  status: PlanStatus
  createdAt: number
}

// 历史列表分页响应
export interface PlanHistoryResponse {
  items: PlanSummary[]
  total: number
  page: number
  pageSize: number
}
