import type {
  AgentProgressItem,
  PlanSummary,
  TripPlanDetail,
  TripPlanRequest,
  XHSNote,
} from '@/models/trip-plan'

/**
 * 本地 Mock 数据 —— 在 smart-trip-server 未实现时复现「效果图」与交互流。
 * 数据取自 前端效果图-小红书风格.html 的「云南 5 日 · 情侣 · ¥8000」示例。
 */

export const MOCK_PLAN_ID = 'P-20260701-001'

export const mockRequest: TripPlanRequest = {
  destination: '云南',
  startDate: '2026-07-01',
  days: 5,
  budget: 8000,
  headcount: 2,
  travelStyle: 'COUPLE',
  preferences: ['古镇', '美食', '小众'],
}

// 「规划进行中」5 Agent 协作进度（Supervisor 不在列，由进度整体体现）
export const mockAgentProgress: AgentProgressItem[] = [
  { key: 'xhs', name: '笔记分析', emoji: '📕', desc: 'XHS 已读 32 篇小红书笔记', state: 'done' },
  { key: 'route', name: '路线规划', emoji: '🗺️', desc: 'Route 已排 8 个景点交通', state: 'done' },
  {
    key: 'itinerary',
    name: '行程编排',
    emoji: '📅',
    desc: 'Itinerary 正在编排 Day3…',
    state: 'running',
  },
  { key: 'budget', name: '费用统筹', emoji: '💰', desc: 'Budget 待开始', state: 'waiting' },
]

// 「小红书笔记溯源」瀑布流
export const mockNotes: XHSNote[] = [
  { id: 'N1', title: '大理必去！洱海边这家民宿绝了', author: '旅行的鱼', likes: 3200, mention: '提及·洱海', emoji: '🏞️', coverClass: 'c1' },
  { id: 'N2', title: '大理古城宝藏小吃地图', author: '吃货阿May', likes: 1800, mention: '提及·大理古城', emoji: '🍜', coverClass: 'c3' },
  { id: 'N3', title: '玉龙雪山一日游超全攻略', author: '山野', likes: 5600, mention: '提及·玉龙雪山', emoji: '⛰️', coverClass: 'c2' },
  { id: 'N4', title: '情侣去丽江，这样安排最浪漫', author: '小鹿', likes: 2100, mention: '情侣友好', emoji: '🌅', coverClass: 'c4' },
  { id: 'N5', title: '洱海骑行路线＋拍照机位', author: '阿橙', likes: 980, emoji: '🛶', coverClass: 'c2' },
  { id: 'N6', title: '丽江古城夜景太美了', author: '夜行者', likes: 1200, mention: '提及·丽江古城', emoji: '🏮', coverClass: 'c1' },
]

// 「计划模式草案」——plan_review 态，展示草案内容供用户确认
export const mockPlanDraft: TripPlanDetail = {
  planId: MOCK_PLAN_ID,
  status: 'plan_review',
  requiresHumanIntervention: false,
  planDraft: `# 云南 5 日行程计划草案

## 阶段一：出发准备（Day 1 大理）
- 上午抵达大理古城，入住古城附近民宿
- 下午游览 **大理古城**（免费，约 2.5h）
- 傍晚前往 **洱海生态廊道**，骑行看日落
- 晚餐：段记饵丝（地方小吃，约 ¥120/人）

## 阶段二：深度体验（Day 2 丽江）
- 上午驱车前往丽江古城（约 2h 车程）
- 游览 **丽江古城**（免费，约 3h）
- 下午前往 **玉龙雪山**（门票 ¥100，含索道 ¥280）
- 晚餐：腊排骨火锅（约 ¥150/人）

## 阶段三：自然风光（Day 3 香格里拉）
- 上午前往香格里拉（约 3h 车程）
- 游览 **松赞林寺**（门票 ¥90，约 2h）
- 下午前往 **普达措国家公园**（门票 ¥138，约 3h）
- 注意高原反应，备好氧气瓶

## 阶段四：自由探索（Day 4 大理周边）
- 返回大理
- 骑行 **洱海东岸**（双廊→小普陀，约 4h）
- 下午 **喜洲古镇**（免费，约 2h）
- 晚餐：白族酸辣鱼（约 ¥100/人）

## 阶段五：返程（Day 5）
- 上午自由活动，采购伴手礼
- 下午返程

## 预算预估
| 项目 | 预估费用 |
|------|---------|
| 交通 | ¥2,400 |
| 住宿 | ¥3,300 |
| 餐饮 | ¥2,100 |
| 门票 | ¥1,500 |
| **合计** | **¥9,300** |

> 注：以上为初步草案，实际费用可能因行程调整而变动。`,
  request: mockRequest,
}

// 「行程结果」+「预算面板/HITL」——草案态（review，超预算 16.3% 触发 HITL）
export const mockPlanReview: TripPlanDetail = {
  planId: MOCK_PLAN_ID,
  status: 'review',
  requiresHumanIntervention: true,
  interventionReason: '预计费用 ¥9,300，超出预算 16.3%（红线 ≥15%）',
  interventionAdvice: 'Day4 五星酒店 → 精品民宿，可省 ¥1,500，调整后约 ¥7,800（达标）',
  request: mockRequest,
  plan: {
    totalCost: 9300,
    budget: {
      total: 8000,
      used: 9300,
      overrunRate: 0.163,
      breakdown: { transport: 2400, hotel: 3300, food: 2100, ticket: 1500 },
    },
    days: [
      {
        date: '2026-07-01',
        index: 1,
        cityLabel: 'Day1 大理',
        dailyCost: 1450,
        attractions: [
          { id: 'A-001', name: '大理古城', rating: 4.7, durationMinutes: 150, ticket: 0 },
          { id: 'A-002', name: '洱海生态廊道', rating: 4.5, durationMinutes: 180, ticket: 0 },
        ],
        meals: [
          { name: '人和园（午餐）', mealType: '午', perCapita: 80, recommend: '白族菜', distanceKm: 1.2 },
          { name: '段记饵丝（晚餐）', mealType: '晚', perCapita: 120, recommend: '地方小吃' },
        ],
        routes: [
          { fromName: '大理古城', toName: '人和园', distanceKm: 6.2, durationMinutes: 15, mode: '驾车' },
          { fromName: '人和园', toName: '洱海生态廊道', distanceKm: 8.0, durationMinutes: 20, mode: '驾车', fallback: true },
        ],
      },
      { date: '2026-07-02', index: 2, cityLabel: 'Day2 丽江', dailyCost: 1620, attractions: [], meals: [], routes: [] },
      { date: '2026-07-03', index: 3, cityLabel: 'Day3 香格里拉', dailyCost: 1880, attractions: [], meals: [], routes: [] },
      { date: '2026-07-04', index: 4, cityLabel: 'Day4', dailyCost: 2300, attractions: [], meals: [], routes: [] },
      { date: '2026-07-05', index: 5, cityLabel: 'Day5', dailyCost: 2050, attractions: [], meals: [], routes: [] },
    ],
  },
}

// 「确认 · 导出 · 反馈」——终态（confirmed，预算内 ¥7,820）
export const mockPlanConfirmed: TripPlanDetail = {
  ...mockPlanReview,
  status: 'confirmed',
  requiresHumanIntervention: false,
  interventionReason: undefined,
  interventionAdvice: undefined,
  plan: {
    ...mockPlanReview.plan!,
    totalCost: 7820,
    budget: {
      total: 8000,
      used: 7820,
      overrunRate: -0.02,
      breakdown: { transport: 2200, hotel: 3000, food: 1620, ticket: 1000 },
    },
  },
}

/**
 * Mock 标签数据 —— Top 30 高频标签（按频次降序）
 * 模拟从 knowledge-base.json 提取并排序后的结果
 */
export const mockTags: string[] = [
  '古镇',
  '美食',
  '小众',
  '摄影',
  '海滩度假',
  '亲子友好',
  '情侣浪漫',
  '徒步登山',
  '文化历史',
  '夜景',
  '民宿推荐',
  '自由行',
  '自驾游',
  '探险',
  '休闲放松',
  '购物',
  '宗教文化',
  '自然风光',
  '民族风情',
  '露营',
  '滑雪',
  '温泉',
  '潜水',
  '骑行',
  '观鸟',
  '星空观测',
  '地质奇观',
  '非遗体验',
  '季节性景点',
  '免费景点',
]

// 历史方案摘要（Mock 模式下历史列表展示用）
export const mockHistoryItems: PlanSummary[] = [
  {
    planId: 'P-20260701-001',
    destination: '云南',
    startDate: '2026-07-01',
    days: 5,
    budget: 8000,
    totalCost: 7820,
    headcount: 2,
    travelStyle: 'COUPLE',
    status: 'confirmed',
    createdAt: 1751328000000,
  },
  {
    planId: 'P-20260615-002',
    destination: '杭州',
    startDate: '2026-06-20',
    days: 3,
    budget: 5000,
    totalCost: 4200,
    headcount: 4,
    travelStyle: 'FAMILY',
    status: 'confirmed',
    createdAt: 1749945600000,
  },
  {
    planId: 'P-20260610-003',
    destination: '成都',
    startDate: '2026-06-15',
    days: 4,
    budget: 6000,
    totalCost: null,
    headcount: 1,
    travelStyle: 'SOLO',
    status: 'failed',
    createdAt: 1749513600000,
  },
  {
    planId: 'P-20260605-004',
    destination: '北京',
    startDate: '2026-06-10',
    days: 4,
    budget: 10000,
    totalCost: 8900,
    headcount: 3,
    travelStyle: 'FAMILY',
    status: 'confirmed',
    createdAt: 1749081600000,
  },
]
