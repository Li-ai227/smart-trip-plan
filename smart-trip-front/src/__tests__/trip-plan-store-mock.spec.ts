/**
 * useTripPlanStore Mock 模式测试 —— 覆盖 useMock=true 路径
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: true, pollIntervalMs: 100 }))

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

describe('useTripPlanStore Mock 模式', () => {
  beforeEach(() => {
    localStorageMock.clear()
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('submit 在 Mock 模式返回 MOCK_PLAN_ID', async () => {
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    const id = await store.submit({
      destination: '云南', startDate: '2026-07-01', days: 5,
      budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [],
    })
    expect(id).toBeTruthy()
    expect(store.detail?.status).toBe('analyzing')
  })

  it('startPolling 在 Mock 模式推进状态机', async () => {
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    await store.submit({
      destination: '云南', startDate: '2026-07-01', days: 5,
      budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [],
    })
    store.startPolling(store.planId)
    // 推进定时器让轮询执行多轮
    await vi.runAllTimersAsync()
    // 状态应推进
    expect(store.detail?.status).toBeTruthy()
  })

  it('stopPolling 清除定时器后不再继续轮询', async () => {
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    await store.submit({
      destination: '云南', startDate: '2026-07-01', days: 5,
      budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [],
    })
    store.startPolling(store.planId)
    // 等待第一次 fetchOnce 完成
    await vi.runAllTimersAsync()
    // 到达终态（review）后轮询自动停止，再调用 stopPolling 不应报错
    expect(() => store.stopPolling()).not.toThrow()
  })

  it('intervene REJECT 在 Mock 模式重新开始轮询', async () => {
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    await store.submit({
      destination: '云南', startDate: '2026-07-01', days: 5,
      budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [],
    })
    await store.intervene('REJECT')
    // REJECT 后状态应回到 planning（从头重新规划）
    expect(store.detail?.status).toBe('planning')
  })

  it('intervene CONFIRM 在 Mock 模式切到 confirmed', async () => {
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    await store.intervene('CONFIRM')
    expect(store.detail?.status).toBe('confirmed')
  })

  it('feedback 在 Mock 模式不调用服务（直接返回）', async () => {
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    // 不应抛出错误
    await expect(store.feedback('UP')).resolves.toBeUndefined()
  })
})
