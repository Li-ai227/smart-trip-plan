import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import DeviceSwitcher from '@/components/common/DeviceSwitcher.vue'
import { useDeviceStore } from '@/stores/device'

describe('DeviceSwitcher', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('switches device mode and redirects to intent from gallery', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/gallery', name: 'gallery', component: defineComponent({ template: '<div />' }) },
        { path: '/intent', name: 'intent', component: defineComponent({ template: '<div />' }) },
      ],
    })

    await router.push({ name: 'gallery' })
    await router.isReady()

    const pushSpy = vi.spyOn(router, 'push')

    const wrapper = mount(DeviceSwitcher, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    const deviceStore = useDeviceStore()
    deviceStore.setMode('desktop')

    const buttons = wrapper.findAll('button')
    await buttons[1].trigger('click')
    await flushPromises()

    expect(deviceStore.mode).toBe('mobile')
    expect(pushSpy).toHaveBeenCalledWith('/intent')
    expect(router.currentRoute.value.path).toBe('/intent')
  })

  it('does not switch when clicking the already-active mode', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/intent', name: 'intent', component: defineComponent({ template: '<div />' }) },
      ],
    })

    await router.push({ name: 'intent' })
    await router.isReady()

    const wrapper = mount(DeviceSwitcher, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    const deviceStore = useDeviceStore()
    const initialMode = deviceStore.mode

    const buttons = wrapper.findAll('button')
    const activeButton = buttons.find((button) => button.classes('on'))!
    await activeButton.trigger('click')

    expect(deviceStore.mode).toBe(initialMode)
    expect(router.currentRoute.value.path).toBe('/intent')
  })

  it('redirects to intent when switching device mode from intent', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/intent', name: 'intent', component: defineComponent({ template: '<div />' }) },
        { path: '/gallery', name: 'gallery', component: defineComponent({ template: '<div />' }) },
      ],
    })

    await router.push({ name: 'intent' })
    await router.isReady()

    const wrapper = mount(DeviceSwitcher, {
      global: {
        plugins: [createPinia(), router],
      },
    })

    const deviceStore = useDeviceStore()
    deviceStore.setMode('mobile')

    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click')
    await flushPromises()

    expect(deviceStore.mode).toBe('desktop')
    expect(router.currentRoute.value.path).toBe('/intent')
  })
})
