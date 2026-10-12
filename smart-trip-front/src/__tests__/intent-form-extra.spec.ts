/**
 * 补强 IntentForm 覆盖率：覆盖更多分支与事件函数
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import IntentForm from '@/components/intent/IntentForm.vue'

describe('IntentForm extra coverage', () => {
  it('days>30 时显示字段错误', async () => {
    const wrapper = mount(IntentForm, {
      props: {
        modelValue: {
          destination: '云南',
          startDate: '2026-07-01',
          days: 31,
          budget: 8000,
          headcount: 2,
          travelStyle: 'COUPLE',
          preferences: [],
        },
      },
    })

    await wrapper.find('button.cta').trigger('click')
    expect(wrapper.find('[data-testid="days-error"]').text()).toContain('1~30')
  })

  it('点击独自风格后提交，travelStyle 更新为 SOLO', async () => {
    const wrapper = mount(IntentForm, {
      props: {
        modelValue: {
          destination: '云南',
          startDate: '2026-07-01',
          days: 5,
          budget: 8000,
          headcount: 2,
          travelStyle: 'COUPLE',
          preferences: ['古镇'],
        },
      },
    })

    const soloPill = wrapper.findAll('.pill').find((p) => p.text().includes('独自'))
    expect(soloPill).toBeTruthy()
    await soloPill!.trigger('click')
    await wrapper.find('button.cta').trigger('click')

    const events = wrapper.emitted('submit')
    expect(events).toBeTruthy()
    const payload = events![0]![0] as { travelStyle: string }
    expect(payload.travelStyle).toBe('SOLO')
  })

  it('移除最后一个偏好后仍可合法提交空 preferences', async () => {
    const wrapper = mount(IntentForm, {
      props: {
        modelValue: {
          destination: '云南',
          startDate: '2026-07-01',
          days: 5,
          budget: 8000,
          headcount: 2,
          travelStyle: 'COUPLE',
          preferences: ['古镇'],
        },
      },
    })

    await wrapper.find('.pill.red').trigger('click')
    await wrapper.find('button.cta').trigger('click')

    const events = wrapper.emitted('submit')
    expect(events).toBeTruthy()
    const payload = events![0]![0] as { preferences: string[] }
    expect(payload.preferences).toEqual([])
  })
})
