/**
 * RED 先行 — 页面级真实主流程守护（C-013 后续验收关键点）
 * 禁止 /plan、/result 页面在 store 缺失时回退到静态 mock 方案冒充真实联调成功。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHashHistory } from 'vue-router'
import { reactive } from 'vue'

const state = vi.hoisted(() => ({
  fetchOnce: vi.fn(),
  intervene: vi.fn(),
  feedback: vi.fn(),
  store: null as any,
}))

vi.mock('@/stores/trip-plan', () => ({
  useTripPlanStore: () => state.store,
}))

function makeRouter(path: '/plan/:planId' | '/result/:planId') {
  return createRouter({
    history: createWebHashHistory(),
    routes: [
      { path: '/planning/:planId', name: 'planning', component: { template: '<div>planning</div>' } },
      { path: '/plan/:planId', name: 'plan', component: { template: '<div>plan</div>' } },
      { path: '/result/:planId', name: 'result', component: { template: '<div>result</div>' } },
      { path, component: { template: '<div />' } },
    ],
  })
}

describe('页面级真实主流程守护', () => {
  beforeEach(() => {
    state.fetchOnce.mockReset()
    state.intervene.mockReset()
    state.feedback.mockReset()

    state.store = reactive({
      request: {
        destination: '杭州',
        startDate: '2026-10-01',
        days: 3,
        budget: 5432,
        headcount: 2,
        travelStyle: 'COUPLE',
        preferences: ['citywalk'],
      },
      detail: null,
      fetchOnce: state.fetchOnce,
      intervene: state.intervene,
      feedback: state.feedback,
    })
  })

  it('ItineraryView: 直达 /plan/:planId 且 store.detail 缺失时，应按真实 planId 拉取详情，而不是回退静态 mock 草案', async () => {
    state.fetchOnce.mockImplementation(async (planId: string) => {
      state.store.detail = {
        planId,
        status: 'review',
        requiresHumanIntervention: true,
        interventionReason: '真实链路需要确认',
        interventionAdvice: '建议保留西湖夜游',
        plan: {
          totalCost: 5432,
          budget: {
            total: 6000,
            used: 5432,
            overrunRate: -0.09,
            breakdown: { transport: 800, hotel: 2200, food: 1200, ticket: 1232 },
          },
          days: [
            {
              date: '2026-10-01',
              index: 1,
              cityLabel: 'Day1 杭州',
              dailyCost: 1800,
              attractions: [{ id: 'A1', name: '西湖', rating: 4.9, durationMinutes: 180, ticket: 0 }],
              meals: [{ name: '楼外楼', mealType: '午', perCapita: 120 }],
              routes: [],
            },
          ],
        },
      }
    })

    const router = makeRouter('/plan/:planId')
    await router.push('/plan/REAL-PLAN-123')
    await router.isReady()

    const { default: ItineraryView } = await import('@/views/pages/ItineraryView.vue')
    const wrapper = mount(ItineraryView, { global: { plugins: [router] } })
    await flushPromises()

    expect(state.fetchOnce).toHaveBeenCalledWith('REAL-PLAN-123')
    expect(wrapper.text()).toContain('Day1 杭州')
    expect(wrapper.text()).toContain('草案待确认')
    // 点击“确认方案”呼出 HITL 确认弹窗
    const confirmBtn = wrapper.find('.btn-open-confirm-mobile')
    expect(confirmBtn.exists()).toBe(true)
    await confirmBtn.trigger('click')
    expect(wrapper.text()).toContain('真实链路需要确认')
    expect(wrapper.text()).not.toContain('预计费用 ¥9,300')
  })

  it('ResultView: 直达 /result/:planId 且 store.detail 缺失时，应按真实 planId 拉取 confirmed 结果，而不是回退静态 mock 成功页', async () => {
    state.fetchOnce.mockImplementation(async (planId: string) => {
      state.store.detail = {
        planId,
        status: 'confirmed',
        requiresHumanIntervention: false,
        plan: {
          totalCost: 5432,
          budget: {
            total: 6000,
            used: 5432,
            overrunRate: -0.09,
            breakdown: { transport: 800, hotel: 2200, food: 1200, ticket: 1232 },
          },
          days: [
            {
              date: '2026-10-01',
              index: 1,
              cityLabel: 'Day1 杭州',
              dailyCost: 1800,
              attractions: [{ id: 'A1', name: '西湖', rating: 4.9, durationMinutes: 180, ticket: 0 }],
              meals: [{ name: '楼外楼', mealType: '午', perCapita: 120 }],
              routes: [],
            },
          ],
        },
      }
    })

    const router = makeRouter('/result/:planId')
    await router.push('/result/REAL-PLAN-123')
    await router.isReady()

    const { default: ResultView } = await import('@/views/pages/ResultView.vue')
    const wrapper = mount(ResultView, { global: { plugins: [router] } })
    await flushPromises()

    expect(state.fetchOnce).toHaveBeenCalledWith('REAL-PLAN-123')
    expect(wrapper.text()).toContain('杭州 3 日')
    expect(wrapper.text()).toContain('¥5,432')
    expect(wrapper.text()).not.toContain('¥7,820')
  })
})
