import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DefaultLayout from '@/views/layouts/DefaultLayout.vue'

// 需要认证才能访问的路由名称集合（AC-4）
const AUTH_REQUIRED = new Set(['intent', 'planning', 'notes', 'plan', 'result', 'history'])
// 已认证后不应访问的路由（AC-4）
const GUEST_ONLY = new Set(['login', 'register'])
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 认证页（无 DefaultLayout 框架）
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/pages/LoginView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/pages/RegisterView.vue'),
    },
    {
      path: '/',
      component: DefaultLayout,
      children: [
        { path: '', redirect: '/intent' },
        // ① 意图输入 —— 触发起点（POST /api/v1/trip-plan）
        {
          path: 'intent',
          name: 'intent',
          component: () => import('@/views/pages/IntentView.vue'),
        },
        // ② 规划进行中 —— 轮询 GET /api/v1/trip-plan/{planId}
        {
          path: 'planning/:planId',
          name: 'planning',
          component: () => import('@/views/pages/PlanningView.vue'),
          // 组件内通过 detail.status 按状态分发：
          //   plan_review → PlanReviewPanel
          //   review → ItineraryTimeline + HitlSheet
          //   confirmed → ItineraryTimeline + FeedbackExport
        },
        // ③ 小红书笔记溯源 —— XHSAnalysisAgent 分析过的笔记瀑布流
        {
          path: 'notes/:planId',
          name: 'notes',
          component: () => import('@/views/pages/NotesView.vue'),
        },
        // ④⑤ 行程结果 + 预算面板 + HITL 介入
        {
          path: 'plan/:planId',
          name: 'plan',
          component: () => import('@/views/pages/ItineraryView.vue'),
        },
        // ⑥ 确认 · 导出 · 反馈（POST /{planId}/feedback）
        {
          path: 'result/:planId',
          name: 'result',
          component: () => import('@/views/pages/ResultView.vue'),
        },
        // ⑦ 历史方案列表 —— GET /api/v1/trip-plan/history
        {
          path: 'history',
          name: 'history',
          component: () => import('@/views/pages/HistoryView.vue'),
        },
        // 效果图墙 —— 6 个模块并排展示
        {
          path: 'gallery',
          name: 'gallery',
          component: () => import('@/views/GalleryView.vue'),
        },
      ],
    },
  ],
})

// AC-4 路由守卫：访问受保护路由前先尝试恢复会话（refresh token → 新 access token）
router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const name = String(to.name ?? '')

  if (AUTH_REQUIRED.has(name)) {
    // 无 token 时快速失败（避免无谓 refresh 请求）
    if (!authStore.accessToken && !authStore.refreshToken) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    const token = await authStore.ensureSession()
    if (!token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (GUEST_ONLY.has(name) && authStore.isAuthenticated) {
    return { path: '/intent' }
  }
  return true
})

export default router
