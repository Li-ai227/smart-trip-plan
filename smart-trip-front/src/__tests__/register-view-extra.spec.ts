/**
 * 补强 RegisterView 覆盖率：覆盖 useMock=true / 400 分支 / 密码强度逻辑 / 交互事件函数
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
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

function makeRouter() {
  return createRouter({
    history: createWebHashHistory(),
    routes: [
      { path: '/intent', name: 'intent', component: { template: '<div>intent</div>' } },
      { path: '/login', name: 'login', component: { template: '<div>login</div>' } },
      { path: '/register', name: 'register', component: { template: '<div>register</div>' } },
    ],
  })
}

describe('RegisterView extra coverage', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.resetModules()
  })

  it('useMock=true 时直接写入 mock token 并跳转 /intent', async () => {
    vi.doMock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: true, pollIntervalMs: 2500 }))
    vi.doMock('@/services/auth', () => ({
      register: vi.fn(),
      login: vi.fn(),
      logout: vi.fn(),
      refresh: vi.fn(),
    }))

    const { default: RegisterView } = await import('@/views/pages/RegisterView.vue')
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    await router.push('/register')
    await router.isReady()

    const wrapper = mount(RegisterView, { global: { plugins: [router, pinia] } })
    await wrapper.find('[data-testid="username"]').setValue('mock-user')
    await wrapper.find('[data-testid="password"]').setValue('Pass1234!')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await flushPromises()

    expect(localStorageMock.getItem('auth_access')).toBe('mock-token')
    expect(router.currentRoute.value.path).toBe('/intent')
  })

  it('register 返回 400 时显示密码格式错误，并覆盖 focus/blur/eye/strength 交互', async () => {
    const mockRegister = vi.fn().mockRejectedValue(Object.assign(new Error('bad request'), { status: 400 }))
    const mockLogin = vi.fn()
    vi.doMock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: false, pollIntervalMs: 2500 }))
    vi.doMock('@/services/auth', () => ({
      register: mockRegister,
      login: mockLogin,
      logout: vi.fn(),
      refresh: vi.fn(),
    }))

    const { default: RegisterView } = await import('@/views/pages/RegisterView.vue')
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    await router.push('/register')
    await router.isReady()

    const wrapper = mount(RegisterView, { global: { plugins: [router, pinia] } })

    const username = wrapper.find('[data-testid="username"]')
    const nickname = wrapper.find('[data-testid="nickname"]')
    const password = wrapper.find('[data-testid="password"]')

    await username.trigger('focus')
    await username.setValue('user-400')
    await username.trigger('blur')

    await nickname.trigger('focus')
    await nickname.setValue('阿花')
    await nickname.trigger('blur')

    await password.trigger('focus')
    await wrapper.find('.eye-btn').trigger('click')
    expect((password.element as HTMLInputElement).type).toBe('text')
    await password.setValue('Pass1234!')
    await password.trigger('blur')

    expect(wrapper.text()).toContain('很强')

    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await flushPromises()

    expect(mockRegister).toHaveBeenCalledTimes(1)
    expect(mockLogin).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="error-msg"]').text()).toContain('密码格式错误')
    expect(router.currentRoute.value.path).toBe('/register')
  })
})
