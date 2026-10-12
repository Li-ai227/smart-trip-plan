/**
 * RED 先行 — 真实主流程契约守护（C-013 后续验收关键点）
 * 证明前端主流程消费真实后端返回的 planId / requiresHumanIntervention / confirmed 结果，
 * 不允许用静态假 planId、静态假数据或 auth-only 冒充联调成功。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v },
    removeItem: (k: string) => { delete store[k] },
    clear: () => { store = {} },
  }
})()
Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock, writable: true })

describe('真实主流程契约守护', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.resetModules()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('submitTripPlan 必须透传后端真实返回的 planId，而不是静态 MOCK_PLAN_ID', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 202,
      json: async () => ({ planId: 'REAL-PLAN-789', status: 'analyzing' }),
    })
    globalThis.fetch = fetchMock
    vi.doMock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 2500 }))

    const { submitTripPlan } = await import('@/services/trip-plan')
    const res = await submitTripPlan({
      destination: '杭州',
      startDate: '2026-10-01',
      days: 3,
      budget: 6000,
      headcount: 2,
      travelStyle: 'COUPLE',
      preferences: ['citywalk'],
    })

    expect(res.planId).toBe('REAL-PLAN-789')
    expect(res.planId).not.toBe('P-20260701-001')
  })

  it('useTripPlanStore.startPolling 获取 review 态时必须保留后端 requiresHumanIntervention / interventionReason / interventionAdvice', async () => {
    vi.doMock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 1 }))
    vi.doMock('@/services/trip-plan', () => ({
      submitTripPlan: vi.fn(),
      getTripPlan: vi.fn().mockResolvedValue({
        planId: 'REAL-PLAN-789',
        status: 'review',
        requiresHumanIntervention: true,
        interventionReason: '后端 SupervisorAgent 判定超预算',
        interventionAdvice: '建议降低住宿档次',
      }),
      intervenePlan: vi.fn(),
      submitFeedback: vi.fn(),
    }))

    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    store.startPolling('REAL-PLAN-789')
    await Promise.resolve()
    await Promise.resolve()

    expect(store.detail?.planId).toBe('REAL-PLAN-789')
    expect(store.detail?.status).toBe('review')
    expect(store.detail?.requiresHumanIntervention).toBe(true)
    expect(store.detail?.interventionReason).toContain('SupervisorAgent')
    expect(store.detail?.interventionAdvice).toContain('降低住宿档次')
  })

  it('useTripPlanStore.intervene(CONFIRM) 后必须保留后端 confirmed 结果，不得继续停留在 review 草案', async () => {
    vi.doMock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 2500 }))
    const mockIntervenePlan = vi.fn().mockResolvedValue({
      planId: 'REAL-PLAN-789',
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
        days: [],
      },
    })
    vi.doMock('@/services/trip-plan', () => ({
      submitTripPlan: vi.fn().mockResolvedValue({ planId: 'REAL-PLAN-789', status: 'review' }),
      getTripPlan: vi.fn(),
      intervenePlan: mockIntervenePlan,
      submitFeedback: vi.fn(),
    }))

    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    await store.submit({
      destination: '杭州',
      startDate: '2026-10-01',
      days: 3,
      budget: 6000,
      headcount: 2,
      travelStyle: 'COUPLE',
      preferences: ['citywalk'],
    })
    await store.intervene('CONFIRM')

    expect(mockIntervenePlan).toHaveBeenCalledWith('REAL-PLAN-789', 'CONFIRM', undefined)
    expect(store.detail?.status).toBe('confirmed')
    expect(store.detail?.requiresHumanIntervention).toBe(false)
    expect(store.detail?.plan?.totalCost).toBe(5432)
  })
})
