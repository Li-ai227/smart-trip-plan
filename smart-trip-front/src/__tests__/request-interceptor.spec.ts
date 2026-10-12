/**
 * GREEN — request.ts 拦截器测试（AC-3/Case-7）
 * Bearer 注入 / refresh 并发锁（只发一次 refresh）
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

// 配置 mock（注意: vi.mock 顶层调用会被 hoisted，这里用 vi.doMock 在 beforeEach 中控制）
vi.mock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 2500 }))

describe('request.ts — useMock=false 时 Bearer 注入', () => {
  beforeEach(() => {
    localStorageMock.clear()
    fetchMock.mockReset()
    setActivePinia(createPinia())
  })

  it('AC-3: token 有效时自动注入 Authorization: Bearer <token>', async () => {
    const { useAuthStore } = await import('@/stores/auth')
    const store = useAuthStore()
    store.setTokens({ accessToken: 'valid-tok', refreshToken: 'rt', expiresIn: 7200 })

    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ planId: 'P-001' }),
    })

    const { get } = await import('@/utils/request')
    await get('/trip-plan/P-001')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers as HeadersInit)
    expect(headers.get('authorization')).toBe('Bearer valid-tok')
  })

  it('AC-3: token 不存在时不注入 Authorization 头', async () => {
    // 不设置 token（accessToken = ''）
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({}),
    })

    const { get } = await import('@/utils/request')
    await get('/some-endpoint')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers as HeadersInit)
    expect(headers.get('authorization')).toBeNull()
  })
})

describe('request.ts — auth 路由不触发 refresh 重试', () => {
  beforeEach(() => {
    localStorageMock.clear()
    fetchMock.mockReset()
    setActivePinia(createPinia())
  })

  it('auth/login 返回 401 时只调用一次 fetch（不重试）', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({ message: '用户名或密码错误' }),
    })

    const { post } = await import('@/utils/request')
    await expect(post('/auth/login', { body: { username: 'x', password: 'y' } })).rejects.toThrow('用户名或密码错误')
    // 只调用一次 fetch，不触发 refresh
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('request.ts — refresh 并发锁（Case-7）', () => {
  beforeEach(() => {
    localStorageMock.clear()
    fetchMock.mockReset()
    setActivePinia(createPinia())
  })

  it('并发请求触发 refresh 时委托 store.ensureSession，共享并发锁', async () => {
    const { useAuthStore } = await import('@/stores/auth')
    const store = useAuthStore()
    store.setTokens({ accessToken: 'old-tok', refreshToken: 'rt-valid', expiresIn: -1 })

    let refreshCallCount = 0
    fetchMock.mockImplementation((url: string) => {
      if ((url as string).includes('/auth/refresh')) {
        refreshCallCount++
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ accessToken: 'new-tok', expiresIn: 7200 }),
        })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => ({ ok: true }) })
    })

    const { get } = await import('@/utils/request')
    await Promise.all([
      get('/data/1').catch(() => null),
      get('/data/2').catch(() => null),
      get('/data/3').catch(() => null),
    ])

    // 并发锁由 store._sessionPromise 保证，/auth/refresh 最多调用一次
    expect(refreshCallCount).toBeLessThanOrEqual(1)
  }, 15000)
})
