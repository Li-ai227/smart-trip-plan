/**
 * GREEN — RegisterView 组件测试（AC-1/Case-1/Case-2）
 * vi.hoisted 确保 mock 函数在 vi.mock factory 调用时已初始化
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import RegisterView from '@/views/pages/RegisterView.vue'

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

const mockRegister = vi.hoisted(() => vi.fn())
const mockLogin = vi.hoisted(() => vi.fn())

vi.mock('@/services/auth', () => ({
  register: mockRegister,
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

describe('RegisterView（AC-1 / Case-1 / Case-2）', () => {
  beforeEach(() => {
    localStorageMock.clear()
    mockRegister.mockReset()
    mockLogin.mockReset()
  })

  afterEach(() => { vi.restoreAllMocks() })

  it('密码长度 < 8 时前端即时拦截，不调用 register 接口', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    const wrapper = mount(RegisterView, { global: { plugins: [router, pinia] } })

    await wrapper.find('[data-testid="username"]').setValue('testuser')
    await wrapper.find('[data-testid="password"]').setValue('short')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await flushPromises()

    expect(mockRegister).not.toHaveBeenCalled()
    const errMsg = wrapper.find('[data-testid="error-msg"]')
    expect(errMsg.exists()).toBe(true)
    expect(errMsg.text()).toMatch(/密码|8/)
  })

  it('用户名空时前端即时拦截', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    const wrapper = mount(RegisterView, { global: { plugins: [router, pinia] } })

    await wrapper.find('[data-testid="username"]').setValue('')
    await wrapper.find('[data-testid="password"]').setValue('Pass1234!')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await flushPromises()

    expect(mockRegister).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="error-msg"]').exists()).toBe(true)
  })

  it('Case-2: 409 时显示「用户名已存在」，不跳转，不清空表单', async () => {
    const err = Object.assign(new Error('用户名已存在'), { status: 409 })
    mockRegister.mockRejectedValue(err)

    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    await router.push('/register')
    const wrapper = mount(RegisterView, { global: { plugins: [router, pinia] } })

    await wrapper.find('[data-testid="username"]').setValue('existing')
    await wrapper.find('[data-testid="password"]').setValue('Pass1234!')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await flushPromises()

    const errMsg = wrapper.find('[data-testid="error-msg"]')
    expect(errMsg.exists()).toBe(true)
    expect(errMsg.text()).toContain('用户名已存在')
    // 表单未清空
    expect((wrapper.find('[data-testid="username"]').element as HTMLInputElement).value).toBe('existing')
    // 未跳转
    expect(router.currentRoute.value.path).toBe('/register')
  })

  it('Case-1: 注册成功 → 自动登录 → 写入 token → 跳转 /intent', async () => {
    mockRegister.mockResolvedValue({ userId: 1, username: 'newuser' })
    mockLogin.mockResolvedValue({ accessToken: 'tok-new', refreshToken: 'rt-new', expiresIn: 7200 })

    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    const wrapper = mount(RegisterView, { global: { plugins: [router, pinia] } })

    await wrapper.find('[data-testid="username"]').setValue('newuser')
    await wrapper.find('[data-testid="password"]').setValue('Pass1234!')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await flushPromises()

    expect(mockRegister).toHaveBeenCalled()
    expect(mockLogin).toHaveBeenCalled()
    expect(localStorageMock.getItem('auth_access')).toBe('tok-new')
    expect(router.currentRoute.value.path).toBe('/intent')
  })
})
