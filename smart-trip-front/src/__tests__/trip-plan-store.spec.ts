/**
 * RED 先行 — useTripPlanStore 核心状态机测试（AC-7/AC-9/Case-9/Case-11）
 * 轮询逻辑、HITL 操作、状态流转
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 100 }))

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

const mockSubmitTripPlan = vi.fn()
const mockGetTripPlan = vi.fn()
const mockIntervenePlan = vi.fn()
const mockSubmitFeedback = vi.fn()

vi.mock('@/services/trip-plan', () => ({
  submitTripPlan: (...args: unknown[]) => mockSubmitTripPlan(...args),
  getTripPlan: (...args: unknown[]) => mockGetTripPlan(...args),
  intervenePlan: (...args: unknown[]) => mockIntervenePlan(...args),
  submitFeedback: (...args: unknown[]) => mockSubmitFeedback(...args),
}))

describe('useTripPlanStore（非 Mock 模式）', () => {
  beforeEach(() => {
    localStorageMock.clear()
    setActivePinia(createPinia())
    vi.useFakeTimers()
    mockSubmitTripPlan.mockReset()
    mockGetTripPlan.mockReset()
    mockIntervenePlan.mockReset()
    mockSubmitFeedback.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('AC-7: submit 返回 planId，detail.status 为 analyzing', async () => {
    mockSubmitTripPlan.mockResolvedValue({ planId: 'P-001', status: 'analyzing' })
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    const id = await store.submit({
      destination: '云南', startDate: '2026-07-01', days: 5,
      budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [],
    })
    expect(id).toBe('P-001')
    expect(store.detail?.status).toBe('analyzing')
  })

  it('AC-7: startPolling 轮询直到 review 态停止', async () => {
    const statuses = ['analyzing', 'planning', 'review']
    let callIdx = 0
    mockGetTripPlan.mockImplementation(() =>
      Promise.resolve({ planId: 'P-001', status: statuses[callIdx++] ?? 'review',
        requiresHumanIntervention: statuses[callIdx - 1] === 'review' }),
    )
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    store.startPolling('P-001')

    // 推进定时器（3 轮足以到达 review）
    await vi.runAllTimersAsync()

    expect(store.detail?.status).toBe('review')
  })

  it('AC-9: intervene CONFIRM → 调用 intervenePlan', async () => {
    mockIntervenePlan.mockResolvedValue({
      planId: 'P-001', status: 'confirmed', requiresHumanIntervention: false,
    })
    mockSubmitTripPlan.mockResolvedValue({ planId: 'P-001', status: 'analyzing' })
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    // submit 先设好 planId
    await store.submit({
      destination: '云南', startDate: '2026-07-01', days: 5,
      budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [],
    })
    await store.intervene('CONFIRM')
    expect(mockIntervenePlan).toHaveBeenCalledWith('P-001', 'CONFIRM', undefined)
    expect(store.detail?.status).toBe('confirmed')
  })

  it('AC-10: feedback 调用 submitFeedback', async () => {
    mockSubmitFeedback.mockResolvedValue({ ok: true })
    mockSubmitTripPlan.mockResolvedValue({ planId: 'P-001', status: 'analyzing' })
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    await store.submit({
      destination: '云南', startDate: '2026-07-01', days: 5,
      budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [],
    })
    await store.feedback('UP', [], '很好')
    expect(mockSubmitFeedback).toHaveBeenCalledWith('P-001', 'UP', [], '很好')
  })

  it('submit 失败时 error 有值，抛出异常', async () => {
    mockSubmitTripPlan.mockRejectedValue(new Error('服务暂时不可用'))
    const { useTripPlanStore } = await import('@/stores/trip-plan')
    const store = useTripPlanStore()
    await expect(store.submit({
      destination: '云南', startDate: '2026-07-01', days: 5,
      budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [],
    })).rejects.toThrow('服务暂时不可用')
    expect(store.error).toBeTruthy()
  })
})
