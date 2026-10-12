/**
 * RED 先行 — useAuthStore 状态机测试（AC-2/AC-3/AC-5/Case-6/Case-8）
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

const mockLogout = vi.hoisted(() => vi.fn())
const mockRefresh = vi.hoisted(() => vi.fn())
vi.mock('@/services/auth', () => ({
  register: vi.fn(),
  login: vi.fn(),
  logout: mockLogout,
  refresh: mockRefresh,
}))

vi.mock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 2500 }))

// localStorage mock
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

describe('useAuthStore', () => {
  beforeEach(() => {
    localStorageMock.clear()
    mockLogout.mockReset()
    mockRefresh.mockReset()
    setActivePinia(createPinia())
  })

  it('初始状态: isAuthenticated 为 false（无 token）', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)
  })

  it('setTokens 后 isAuthenticated 为 true，localStorage 已写入', () => {
    const store = useAuthStore()
    store.setTokens({ accessToken: 'tok-abc', refreshToken: 'rt-xyz', expiresIn: 7200 })
    expect(store.isAuthenticated).toBe(true)
    expect(store.accessToken).toBe('tok-abc')
    expect(store.refreshToken).toBe('rt-xyz')
    expect(localStorageMock.getItem('auth_access')).toBe('tok-abc')
    expect(localStorageMock.getItem('auth_refresh')).toBe('rt-xyz')
  })

  it('clearTokens 后 isAuthenticated 为 false，localStorage 已清除', () => {
    const store = useAuthStore()
    store.setTokens({ accessToken: 'tok-abc', refreshToken: 'rt-xyz', expiresIn: 7200 })
    store.clearTokens()
    expect(store.isAuthenticated).toBe(false)
    expect(store.accessToken).toBe('')
    expect(localStorageMock.getItem('auth_access')).toBeNull()
  })

  it('expiresAt 已过期时 isAuthenticated 为 false（Case-6）', () => {
    const store = useAuthStore()
    // 模拟已过期 token：expiresAt = 1（过去时间）
    store.setTokens({ accessToken: 'old-tok', refreshToken: 'rt', expiresIn: -10 })
    expect(store.isAuthenticated).toBe(false)
  })

  it('setTokens 仅传 accessToken + expiresIn（refresh 响应）不覆盖 refreshToken', () => {
    const store = useAuthStore()
    store.setTokens({ accessToken: 'tok', refreshToken: 'rt-original', expiresIn: 7200 })
    store.setTokens({ accessToken: 'tok-new', expiresIn: 7200 })
    expect(store.refreshToken).toBe('rt-original')
    expect(store.accessToken).toBe('tok-new')
  })

  it('Case-8: logoutAction 调用 logout 服务（服务端吊销），并清除所有本地 token', async () => {
    mockLogout.mockResolvedValue({ message: 'ok' })
    const store = useAuthStore()
    store.setTokens({ accessToken: 'tok', refreshToken: 'rt', expiresIn: 7200 })
    await store.logoutAction()
    expect(mockLogout).toHaveBeenCalledTimes(1)
    expect(store.isAuthenticated).toBe(false)
    expect(localStorageMock.getItem('auth_access')).toBeNull()
  })

  it('logoutAction 在 logout 服务失败时仍清除本地 token', async () => {
    mockLogout.mockRejectedValue(new Error('network error'))
    const store = useAuthStore()
    store.setTokens({ accessToken: 'tok', refreshToken: 'rt', expiresIn: 7200 })
    await store.logoutAction()
    // 服务失败不影响本地清除
    expect(store.isAuthenticated).toBe(false)
    expect(localStorageMock.getItem('auth_access')).toBeNull()
  })

  it('页面刷新时从 localStorage 恢复 token（expiresAt 未来时间）', () => {
    // 预置 localStorage（模拟刷新前写入）
    const future = Date.now() + 3600 * 1000
    localStorageMock.setItem('auth_access', 'persisted-tok')
    localStorageMock.setItem('auth_refresh', 'persisted-rt')
    localStorageMock.setItem('auth_expires_at', String(future))
    setActivePinia(createPinia())
    const store = useAuthStore()
    expect(store.accessToken).toBe('persisted-tok')
    expect(store.isAuthenticated).toBe(true)
  })

  it('ensureSession: token 有效时直接返回，不发起 refresh', async () => {
    const store = useAuthStore()
    store.setTokens({ accessToken: 'valid-tok', refreshToken: 'rt', expiresIn: 7200 })
    const token = await store.ensureSession()
    expect(token).toBe('valid-tok')
    expect(mockRefresh).not.toHaveBeenCalled()
  })

  it('ensureSession: expiresAt===0 时信任 token，不触发 refresh（与 isAuthenticated 一致）', async () => {
    // 用 Date.now mock 让 setTokens({ expiresIn: 0 }) 写入 expiresAt = 0
    // 避免 setActivePinia(createPinia()) 干扰后续测试的 mock 计数
    const store = useAuthStore()
    const nowSpy = vi.spyOn(Date, 'now').mockReturnValue(0)
    store.setTokens({ accessToken: 'tok-no-expiry', refreshToken: 'rt', expiresIn: 0 })
    nowSpy.mockRestore()
    // expiresAt = 0 + 0 * 1000 = 0（与 readLS() 无 auth_expires_at 时等效）
    expect(store.expiresAt).toBe(0)
    const token = await store.ensureSession()
    expect(token).toBe('tok-no-expiry')
    expect(mockRefresh).not.toHaveBeenCalled()
  })

  it('ensureSession: refresh 失败但 token 尚未过期时降级返回当前 token（防误踢）', async () => {
    mockRefresh.mockRejectedValue(new Error('network error'))
    const store = useAuthStore()
    // expiresIn=60 → expiresAt = now+60s，在 REFRESH_THRESHOLD_MS(5min=300s) 阈值内，触发 refresh
    store.setTokens({ accessToken: 'almost-expired-tok', refreshToken: 'rt', expiresIn: 60 })
    const token = await store.ensureSession()
    // refresh 失败但 token 尚有 1 分钟有效期，应降级使用而非踢出
    expect(token).toBe('almost-expired-tok')
    expect(store.isAuthenticated).toBe(true)
  })

  it('ensureSession: token 过期但有 refreshToken → 成功刷新并返回新 token', async () => {
    mockRefresh.mockResolvedValue({ accessToken: 'new-tok', expiresIn: 7200 })
    const store = useAuthStore()
    store.setTokens({ accessToken: 'old-tok', refreshToken: 'rt', expiresIn: -10 })
    const token = await store.ensureSession()
    expect(token).toBe('new-tok')
    expect(store.accessToken).toBe('new-tok')
    expect(store.isAuthenticated).toBe(true)
    expect(mockRefresh).toHaveBeenCalledTimes(1)
  })

  it('ensureSession: refresh 失败 → 清除 token，返回 null', async () => {
    mockRefresh.mockRejectedValue(new Error('network error'))
    const store = useAuthStore()
    store.setTokens({ accessToken: 'old-tok', refreshToken: 'rt', expiresIn: -10 })
    const token = await store.ensureSession()
    expect(token).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(store.accessToken).toBe('')
  })

  it('ensureSession: 无 refreshToken 时直接返回 null', async () => {
    const store = useAuthStore()
    store.setTokens({ accessToken: 'old-tok', expiresIn: -10 })
    const token = await store.ensureSession()
    expect(token).toBeNull()
  })
})
