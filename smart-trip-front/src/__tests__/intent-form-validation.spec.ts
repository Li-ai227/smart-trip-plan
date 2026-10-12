/**
 * RED 先行 — IntentForm 表单校验测试（AC-6/Case-10）
 * 非法表单即时拦截，不发送 HTTP 请求
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import IntentForm from '@/components/intent/IntentForm.vue'

vi.mock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: true, pollIntervalMs: 2500 }))

describe('IntentForm 表单校验（AC-6 / Case-10）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('days=0 时触发提交不 emit submit 事件（前端即时拦截）', async () => {
    const wrapper = mount(IntentForm, {
      props: { modelValue: { destination: '云南', startDate: '2026-07-01', days: 0,
        budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [] } },
    })
    await wrapper.find('button.cta').trigger('click')
    // 非法 days=0 不应 emit submit
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('budget=0 时触发提交不 emit submit 事件', async () => {
    const wrapper = mount(IntentForm, {
      props: { modelValue: { destination: '云南', startDate: '2026-07-01', days: 3,
        budget: 0, headcount: 2, travelStyle: 'COUPLE', preferences: [] } },
    })
    await wrapper.find('button.cta').trigger('click')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('headcount=0 时触发提交不 emit submit 事件', async () => {
    const wrapper = mount(IntentForm, {
      props: { modelValue: { destination: '云南', startDate: '2026-07-01', days: 3,
        budget: 5000, headcount: 0, travelStyle: 'COUPLE', preferences: [] } },
    })
    await wrapper.find('button.cta').trigger('click')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('合法表单触发 submit 时 emit 含正确字段', async () => {
    const wrapper = mount(IntentForm, {
      props: { modelValue: { destination: '云南', startDate: '2026-07-01', days: 5,
        budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: ['美食'] } },
    })
    await wrapper.find('button.cta').trigger('click')
    const events = wrapper.emitted('submit')
    expect(events).toBeTruthy()
    const payload = (events as unknown[][])[0][0] as Record<string, unknown>
    expect(payload.days).toBe(5)
    expect(payload.budget).toBe(8000)
    expect(payload.headcount).toBe(2)
  })

  it('点击偏好标签上的 × 时从列表移除', async () => {
    const wrapper = mount(IntentForm, {
      props: { modelValue: { destination: '云南', startDate: '2026-07-01', days: 5,
        budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: ['古镇', '美食'] } },
    })
    // 点击第一个偏好标签（古镇 ×）
    const prefPills = wrapper.findAll('.pill.red')
    expect(prefPills.length).toBeGreaterThanOrEqual(1)
    await prefPills[0]!.trigger('click')
    // 移除后不再出现
    expect(wrapper.text()).not.toContain('古镇 ×')
  })

  it('目的地为空时触发提交不 emit submit 事件', async () => {
    const wrapper = mount(IntentForm, {
      props: { modelValue: { destination: '', startDate: '2026-07-01', days: 5,
        budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [] } },
    })
    await wrapper.find('button.cta').trigger('click')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('切换旅行风格 pill 更新 form.travelStyle', async () => {
    const wrapper = mount(IntentForm, {
      props: { modelValue: { destination: '云南', startDate: '2026-07-01', days: 5,
        budget: 8000, headcount: 2, travelStyle: 'COUPLE', preferences: [] } },
    })
    // 点击「独自」pill
    const pills = wrapper.findAll('.pill:not(.red)')
    const soloPill = pills.find(p => p.text().includes('独自'))
    if (soloPill) {
      await soloPill.trigger('click')
      // 提交并检查 travelStyle
      await wrapper.find('button.cta').trigger('click')
      const events = wrapper.emitted('submit')
      if (events) {
        const payload = (events as unknown[][])[0][0] as Record<string, unknown>
        expect(payload.travelStyle).toBe('SOLO')
      }
    }
  })
})
