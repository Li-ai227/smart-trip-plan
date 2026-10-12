/**
 * RED 先行 — trip-plan service 契约镜像测试（T-4 字段对齐）
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

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

const fetchMock = vi.fn()
globalThis.fetch = fetchMock

describe('trip-plan service 契约', () => {
  beforeEach(() => {
    localStorageMock.clear()
    fetchMock.mockReset()
    vi.resetModules()
    vi.doMock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: true, pollIntervalMs: 2500 }))
  })

  afterEach(() => { vi.restoreAllMocks() })

  it('submitTripPlan: POST /api/v1/trip-plan，body 含必要字段', async () => {
    fetchMock.mockResolvedValue({
      ok: true, status: 202,
      json: async () => ({ planId: 'P-001', status: 'analyzing' }),
    })
    const { setActivePinia, createPinia } = await import('pinia')
    setActivePinia(createPinia())
    const { submitTripPlan } = await import('@/services/trip-plan')
    const result = await submitTripPlan({
      destination: '云南', startDate: '2026-07-01', days: 5,
      budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: ['古镇'],
    })
    expect(result.planId).toBe('P-001')
    expect(result.status).toBe('analyzing')
    const [url] = fetchMock.mock.calls[0] as [string]
    expect(url).toContain('/api/v1/trip-plan')
  })

  it('getTripPlan: GET /api/v1/trip-plan/{planId}，返回含 requiresHumanIntervention', async () => {
    fetchMock.mockResolvedValue({
      ok: true, status: 200,
      json: async () => ({
        planId: 'P-001', status: 'review',
        requiresHumanIntervention: true,
        interventionReason: '超预算',
        interventionAdvice: '改民宿',
      }),
    })
    const { setActivePinia, createPinia } = await import('pinia')
    setActivePinia(createPinia())
    const { getTripPlan } = await import('@/services/trip-plan')
    const result = await getTripPlan('P-001')
    expect(result.requiresHumanIntervention).toBe(true)
    expect(result.interventionReason).toBe('超预算')
    const [url] = fetchMock.mock.calls[0] as [string]
    expect(url).toContain('/trip-plan/P-001')
  })

  it('intervenePlan: POST body 含 action 字段', async () => {
    fetchMock.mockResolvedValue({
      ok: true, status: 200,
      json: async () => ({ planId: 'P-001', status: 'confirmed', requiresHumanIntervention: false }),
    })
    const { setActivePinia, createPinia } = await import('pinia')
    setActivePinia(createPinia())
    const { intervenePlan } = await import('@/services/trip-plan')
    const result = await intervenePlan('P-001', 'CONFIRM')
    expect(result.status).toBe('confirmed')
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const body = JSON.parse(init.body as string) as Record<string, unknown>
    expect(body.action).toBe('CONFIRM')
  })
})
