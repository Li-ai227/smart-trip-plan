/**
 * 补强 LoginView 覆盖率：覆盖 useMock=true 分支 + 交互事件函数
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

describe('LoginView mock 模式与交互覆盖', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.resetModules()
  })

  it('useMock=true 时直接写入 mock token 并跳转 /intent，同时覆盖 focus/blur/eye 交互', async () => {
    vi.doMock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: true, pollIntervalMs: 2500 }))
    vi.doMock('@/services/auth', () => ({
      register: vi.fn(),
      login: vi.fn(),
      logout: vi.fn(),
      refresh: vi.fn(),
    }))

    const { default: LoginView } = await import('@/views/pages/LoginView.vue')
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    await router.push('/login')
    await router.isReady()

    const wrapper = mount(LoginView, { global: { plugins: [router, pinia] } })

    const username = wrapper.find('[data-testid="username"]')
    const password = wrapper.find('[data-testid="password"]')

    await username.trigger('focus')
    await username.setValue('mock-user')
    await username.trigger('blur')

    await password.trigger('focus')
    await wrapper.find('.eye-btn').trigger('click')
    expect((password.element as HTMLInputElement).type).toBe('text')
    await wrapper.find('.eye-btn').trigger('click')
    await password.setValue('Pass1234!')
    await password.trigger('blur')

    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await flushPromises()

    expect(localStorageMock.getItem('auth_access')).toBe('mock-token')
    expect(localStorageMock.getItem('auth_refresh')).toBe('mock-refresh')
    expect(router.currentRoute.value.path).toBe('/intent')
  })
})
