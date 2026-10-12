/**
 * GREEN — LoginView 组件测试（AC-2/Case-3）
 * vi.hoisted 确保 mock 函数在 vi.mock factory 调用时已初始化
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import LoginView from '@/views/pages/LoginView.vue'

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

// vi.hoisted 保证 mock 函数在 vi.mock factory 执行时已初始化（防 TDZ）
const mockLogin = vi.hoisted(() => vi.fn())

vi.mock('@/services/auth', () => ({
  register: vi.fn(),
  login: mockLogin,
  logout: vi.fn(),
  refresh: vi.fn(),
}))

vi.mock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 2500 }))

function makeRouter() {
  return createRouter({
    history: createWebHashHistory(),
    routes: [
      { path: '/', component: { template: '<div/>' } },
      { path: '/intent', name: 'intent', component: { template: '<div/>' } },
      { path: '/login', name: 'login', component: { template: '<div/>' } },
      { path: '/register', name: 'register', component: { template: '<div/>' } },
    ],
  })
}

describe('LoginView（AC-2 / Case-3）', () => {
  beforeEach(() => {
    localStorageMock.clear()
    mockLogin.mockReset()
  })

  afterEach(() => { vi.restoreAllMocks() })

  it('用户名空时前端校验拦截，不调用 login，显示错误信息', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    const wrapper = mount(LoginView, { global: { plugins: [router, pinia] } })

    await wrapper.find('[data-testid="username"]').setValue('')
    await wrapper.find('[data-testid="password"]').setValue('Pass1234')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await flushPromises()

    expect(mockLogin).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="error-msg"]').exists()).toBe(true)
  })

  it('AC-2: 401 时显示「用户名或密码错误」，不跳转', async () => {
    const err = Object.assign(new Error('用户名或密码错误'), { status: 401 })
    mockLogin.mockRejectedValue(err)

    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    await router.push('/login')
    const wrapper = mount(LoginView, { global: { plugins: [router, pinia] } })

    await wrapper.find('[data-testid="username"]').setValue('test')
    await wrapper.find('[data-testid="password"]').setValue('wrongpwd')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await flushPromises()

    const errEl = wrapper.find('[data-testid="error-msg"]')
    expect(errEl.exists()).toBe(true)
    expect(errEl.text()).toContain('用户名或密码错误')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('Case-3: 登录成功 → localStorage 写入 accessToken + refreshToken → 跳转 /intent', async () => {
    mockLogin.mockResolvedValue({ accessToken: 'tok-abc', refreshToken: 'rt-abc', expiresIn: 7200 })

    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    const wrapper = mount(LoginView, { global: { plugins: [router, pinia] } })

    await wrapper.find('[data-testid="username"]').setValue('test')
    await wrapper.find('[data-testid="password"]').setValue('Pass1234')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await flushPromises()

    expect(mockLogin).toHaveBeenCalledWith({ username: 'test', password: 'Pass1234' })
    expect(localStorageMock.getItem('auth_access')).toBe('tok-abc')
    expect(router.currentRoute.value.path).toBe('/intent')
  })
})
