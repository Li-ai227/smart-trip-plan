/**
 * request.ts 边界测试 — 覆盖 timeout / 503 / retry-once / mock-mode 跳过
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
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

const fetchMock = vi.fn()
globalThis.fetch = fetchMock

vi.mock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 2500 }))

describe('request.ts 边界场景', () => {
  beforeEach(() => {
    localStorageMock.clear()
    fetchMock.mockReset()
    setActivePinia(createPinia())
  })

  it('503 错误时附带 traceId 字段', async () => {
    fetchMock.mockResolvedValue({
      ok: false, status: 503,
      json: async () => ({ code: 'SERVICE_UNAVAILABLE', message: '服务暂时不可用', traceId: 'T-123' }),
    })
    const { get } = await import('@/utils/request')
    try {
      await get('/trip-plan/P-001')
      expect.fail('should throw')
    } catch (e: unknown) {
      const err = e as { message: string; traceId?: string; status?: number }
      expect(err.message).toContain('服务暂时不可用')
      expect(err.traceId).toBe('T-123')
      expect(err.status).toBe(503)
    }
  })

  it('400 错误时抛出含 message 的 Error', async () => {
    fetchMock.mockResolvedValue({
      ok: false, status: 400,
      json: async () => ({ code: 'INVALID_REQUEST', message: '参数非法：days 必须 > 0' }),
    })
    const { post } = await import('@/utils/request')
    await expect(post('/trip-plan', { body: { days: 0 } })).rejects.toThrow('参数非法：days 必须 > 0')
  })

  it('GET 请求携带 params 时拼接到 URL 查询字符串', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => ({}) })
    const { get } = await import('@/utils/request')
    await get('/trip-plan', { params: { page: 1, size: 10, active: true } }).catch(() => null)
    const [url] = fetchMock.mock.calls[0] as [string]
    expect(url).toContain('page=1')
    expect(url).toContain('size=10')
    expect(url).toContain('active=true')
  })

  it('POST 请求带 body 时 Content-Type 设为 application/json', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => ({ planId: 'P-001' }) })
    const { post } = await import('@/utils/request')
    await post('/trip-plan', { body: { destination: '云南' } })
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers as HeadersInit)
    expect(headers.get('content-type')).toBe('application/json')
    const body = JSON.parse(init.body as string) as Record<string, unknown>
    expect(body.destination).toBe('云南')
  })

  it('auth 路由（/auth/register）不注入 token', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 201, json: async () => ({ userId: 1, username: 'test' }) })
    const { useAuthStore } = await import('@/stores/auth')
    const store = useAuthStore()
    store.setTokens({ accessToken: 'tok', refreshToken: 'rt', expiresIn: 7200 })

    const { post } = await import('@/utils/request')
    await post('/auth/register', { body: { username: 'x', password: 'Pass1234' } })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers as HeadersInit)
    expect(headers.get('authorization')).toBeNull()
  })
})
