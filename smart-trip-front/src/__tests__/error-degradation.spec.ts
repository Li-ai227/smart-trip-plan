/**
 * RED 先行 — 错误/降级 UI 测试（AC-11/Case-12/Case-13）
 * 503 可读提示 + fallback 标注
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: true, pollIntervalMs: 2500 }))

describe('503 错误 / fallback 降级渲染（AC-11 / Case-12 / Case-13）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('Case-13: Route 含 fallback=true 的数据时，ItineraryTimeline 应标注「降级估算」', async () => {
    const { default: ItineraryTimeline } = await import('@/components/itinerary/ItineraryTimeline.vue')
    const days = [{
      date: '2026-07-01', index: 1, cityLabel: 'Day1 大理', dailyCost: 1450,
      attractions: [], meals: [],
      routes: [{ fromName: 'A', toName: 'B', distanceKm: 5, durationMinutes: 15, mode: '驾车' as const, fallback: true }],
    }]
    const wrapper = mount(ItineraryTimeline, { props: { days, activeIndex: 1 } })
    // fallback 路线应标注
    expect(wrapper.text()).toMatch(/降级|估算|fallback/i)
  })

  it('Case-12: request.ts 500/503 异常时附带 traceId', async () => {
    vi.resetModules()
    vi.doMock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 2500 }))
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

    const fetchMock = vi.fn().mockResolvedValue({
      ok: false, status: 503,
      json: async () => ({ code: 'SERVICE_UNAVAILABLE', message: '服务暂时不可用', traceId: 'T-abc123' }),
    })
    globalThis.fetch = fetchMock
    setActivePinia(createPinia())

    const { get } = await import('@/utils/request')
    try {
      await get('/trip-plan/P-999')
      expect.fail('should throw')
    } catch (e: unknown) {
      const err = e as { message: string; traceId?: string }
      expect(err.message).toContain('服务暂时不可用')
      expect(err.traceId).toBe('T-abc123')
    }
  })
})
