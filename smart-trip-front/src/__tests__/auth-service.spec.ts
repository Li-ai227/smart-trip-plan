/**
 * RED 先行 — auth service 契约镜像测试（字段对齐 §1.6）
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'

const fetchMock = vi.fn()
globalThis.fetch = fetchMock

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

describe('auth service 契约', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.resetModules()
    localStorageMock.clear()
    vi.doMock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 2500 }))
  })

  afterEach(() => { vi.restoreAllMocks() })

  it('register: 调用 POST /api/v1/auth/register，返回 { userId, username }', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ userId: 1, username: 'test' }),
    })
    const { setActivePinia, createPinia } = await import('pinia')
    setActivePinia(createPinia())
    const { register } = await import('@/services/auth')
    const result = await register({ username: 'test', password: 'Pass1234' })
    expect(result.userId).toBe(1)
    expect(result.username).toBe('test')
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toContain('/api/v1/auth/register')
    expect((init as RequestInit).method).toBe('POST')
  })

  it('login: 调用 POST /api/v1/auth/login，返回 { accessToken, refreshToken, expiresIn }', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ accessToken: 'tok', refreshToken: 'rt', expiresIn: 7200 }),
    })
    const { setActivePinia, createPinia } = await import('pinia')
    setActivePinia(createPinia())
    const { login } = await import('@/services/auth')
    const result = await login({ username: 'test', password: 'Pass1234' })
    expect(result.accessToken).toBeTruthy()
    expect(result.refreshToken).toBeTruthy()
    expect(result.expiresIn).toBeGreaterThan(0)
  })

  it('login: 401 时抛出含 status=401 的 Error（auth 路由不触发 refresh）', async () => {
    // /auth/login 是 AUTH_PATHS，request.ts 不注入 token 也不触发 refresh 重试
    fetchMock.mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({ code: 'UNAUTHORIZED', message: '用户名或密码错误' }),
    })
    const { setActivePinia, createPinia } = await import('pinia')
    setActivePinia(createPinia())
    const { login } = await import('@/services/auth')
    await expect(login({ username: 'x', password: 'wrongpwd' })).rejects.toMatchObject({
      message: '用户名或密码错误',
    })
    // 只调用一次 fetch（不触发 refresh 重试）
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('register: 409 时抛出含 status=409 的 Error', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({ code: 'CONFLICT', message: '用户名已存在' }),
    })
    const { setActivePinia, createPinia } = await import('pinia')
    setActivePinia(createPinia())
    const { register } = await import('@/services/auth')
    await expect(register({ username: 'dup', password: 'Pass1234' })).rejects.toMatchObject({
      message: '用户名已存在',
    })
  })

  it('refresh: 调用 POST /api/v1/auth/refresh，返回 { accessToken, expiresIn }', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ accessToken: 'new-tok', expiresIn: 7200 }),
    })
    const { setActivePinia, createPinia } = await import('pinia')
    setActivePinia(createPinia())
    const { refresh } = await import('@/services/auth')
    const result = await refresh('rt-valid')
    expect(result.accessToken).toBe('new-tok')
    const [url] = fetchMock.mock.calls[0] as [string]
    expect(url).toContain('/api/v1/auth/refresh')
  })
})
