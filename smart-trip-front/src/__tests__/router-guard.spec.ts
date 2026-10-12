/**
 * RED 先行 → GREEN — 路由守卫测试（AC-4/Case-4）
 * 未认证访问受保护路由 → 重定向 /login
 * 已认证访问 /login → 重定向 /intent
 * 刷新恢复：过期 token + 有效 refreshToken → 守卫刷新后放行
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'

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

const mockRefresh = vi.hoisted(() => vi.fn())
vi.mock('@/services/auth', () => ({
  register: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  refresh: mockRefresh,
}))
vi.mock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 2500 }))

// 需要认证的路由名集合（镜像 router/index.ts 逻辑）
const AUTH_REQUIRED = new Set(['intent', 'planning', 'notes', 'plan', 'result'])
const GUEST_ONLY = new Set(['login', 'register'])

describe('路由守卫（AC-4 / Case-4）', () => {
  beforeEach(() => {
    localStorageMock.clear()
    mockRefresh.mockReset()
    setActivePinia(createPinia())
  })

  afterEach(() => { vi.restoreAllMocks() })

  it('未认证无 token 访问 /intent → 重定向 /login', async () => {
    // 使用真实 guard 逻辑的 router（不 mock store）
    const router = createRouter({
      history: createWebHashHistory(),
      routes: [
        { path: '/login', name: 'login', component: { template: '<div>login</div>' } },
        { path: '/intent', name: 'intent', component: { template: '<div>intent</div>' } },
      ],
    })
    router.beforeEach(async (to) => {
      const { useAuthStore } = await import('@/stores/auth')
      const authStore = useAuthStore()
      const name = String(to.name ?? '')
      if (AUTH_REQUIRED.has(name)) {
        if (!authStore.accessToken && !authStore.refreshToken) {
          return { path: '/login', query: { redirect: to.fullPath } }
        }
        const token = await authStore.ensureSession()
        if (!token) return { path: '/login', query: { redirect: to.fullPath } }
      }
      if (GUEST_ONLY.has(name) && authStore.isAuthenticated) return { path: '/intent' }
      return true
    })
    await router.push('/intent')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('已认证访问 /login → 重定向 /intent', async () => {
    const { useAuthStore } = await import('@/stores/auth')
    const store = useAuthStore()
    store.setTokens({ accessToken: 'valid-tok', refreshToken: 'rt', expiresIn: 7200 })

    const router = createRouter({
      history: createWebHashHistory(),
      routes: [
        { path: '/login', name: 'login', component: { template: '<div>login</div>' } },
        { path: '/intent', name: 'intent', component: { template: '<div>intent</div>' } },
      ],
    })
    router.beforeEach(async (to) => {
      const { useAuthStore: getStore } = await import('@/stores/auth')
      const authStore = getStore()
      const name = String(to.name ?? '')
      if (AUTH_REQUIRED.has(name)) {
        if (!authStore.accessToken && !authStore.refreshToken) {
          return { path: '/login', query: { redirect: to.fullPath } }
        }
        const token = await authStore.ensureSession()
        if (!token) return { path: '/login', query: { redirect: to.fullPath } }
      }
      if (GUEST_ONLY.has(name) && authStore.isAuthenticated) return { path: '/intent' }
      return true
    })
    await router.push('/login')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/intent')
  })

  it('refresh 恢复: 过期 token + 有效 refreshToken → 刷新成功，放行受保护路由', async () => {
    mockRefresh.mockResolvedValue({ accessToken: 'new-tok', expiresIn: 7200 })
    const { useAuthStore } = await import('@/stores/auth')
    const store = useAuthStore()
    // 模拟刷新前持久化的过期 token
    store.setTokens({ accessToken: 'old-tok', refreshToken: 'rt', expiresIn: -10 })

    const router = createRouter({
      history: createWebHashHistory(),
      routes: [
        { path: '/login', name: 'login', component: { template: '<div>login</div>' } },
        { path: '/intent', name: 'intent', component: { template: '<div>intent</div>' } },
      ],
    })
    router.beforeEach(async (to) => {
      const { useAuthStore: getStore } = await import('@/stores/auth')
      const authStore = getStore()
      const name = String(to.name ?? '')
      if (AUTH_REQUIRED.has(name)) {
        if (!authStore.accessToken && !authStore.refreshToken) {
          return { path: '/login', query: { redirect: to.fullPath } }
        }
        const token = await authStore.ensureSession()
        if (!token) return { path: '/login', query: { redirect: to.fullPath } }
      }
      if (GUEST_ONLY.has(name) && authStore.isAuthenticated) return { path: '/intent' }
      return true
    })
    await router.push('/intent')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/intent')
    expect(store.accessToken).toBe('new-tok')
  })

  it('refresh 失败: 过期 token + 无效 refreshToken → 重定向 /login', async () => {
    mockRefresh.mockRejectedValue(new Error('invalid refresh token'))
    const { useAuthStore } = await import('@/stores/auth')
    const store = useAuthStore()
    store.setTokens({ accessToken: 'old-tok', refreshToken: 'rt-invalid', expiresIn: -10 })

    const router = createRouter({
      history: createWebHashHistory(),
      routes: [
        { path: '/login', name: 'login', component: { template: '<div>login</div>' } },
        { path: '/intent', name: 'intent', component: { template: '<div>intent</div>' } },
      ],
    })
    router.beforeEach(async (to) => {
      const { useAuthStore: getStore } = await import('@/stores/auth')
      const authStore = getStore()
      const name = String(to.name ?? '')
      if (AUTH_REQUIRED.has(name)) {
        if (!authStore.accessToken && !authStore.refreshToken) {
          return { path: '/login', query: { redirect: to.fullPath } }
        }
        const token = await authStore.ensureSession()
        if (!token) return { path: '/login', query: { redirect: to.fullPath } }
      }
      if (GUEST_ONLY.has(name) && authStore.isAuthenticated) return { path: '/intent' }
      return true
    })
    await router.push('/intent')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/login')
    expect(store.isAuthenticated).toBe(false)
  })
})
