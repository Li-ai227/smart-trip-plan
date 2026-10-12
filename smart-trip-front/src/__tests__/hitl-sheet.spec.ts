/**
 * RED 先行 — HitlSheet 组件测试（AC-9/Case-11）
 * HITL 提示展示 + CONFIRM/MODIFY/REJECT 操作
 */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import HitlSheet from '@/components/hitl/HitlSheet.vue'

vi.mock('@/config', () => ({ apiPrefix: 'http://test-api', useMock: true, pollIntervalMs: 2500 }))

describe('HitlSheet（AC-9 / Case-11）', () => {
  it('展示 reason 和 advice 文本', () => {
    const wrapper = mount(HitlSheet, {
      props: { reason: '预算超支 16%', advice: '改为精品民宿可节省 ¥1500' },
    })
    expect(wrapper.text()).toContain('预算超支 16%')
    expect(wrapper.text()).toContain('改为精品民宿')
  })

  it('点击 CONFIRM 按钮 → emit action=CONFIRM', async () => {
    const wrapper = mount(HitlSheet, { props: { reason: '测试', advice: '建议' } })
    const confirmBtn = wrapper.findAll('button').find(b => b.text().includes('CONFIRM'))
    expect(confirmBtn).toBeTruthy()
    await confirmBtn!.trigger('click')
    expect(wrapper.emitted('action')?.[0]).toEqual(['CONFIRM'])
  })

  it('点击 MODIFY 按钮 → emit action=MODIFY', async () => {
    const wrapper = mount(HitlSheet, { props: { reason: '测试', advice: '建议' } })
    const modifyBtn = wrapper.findAll('button').find(b => b.text().includes('MODIFY'))
    expect(modifyBtn).toBeTruthy()
    await modifyBtn!.trigger('click')
    expect(wrapper.emitted('action')?.[0]).toEqual(['MODIFY'])
  })

  it('点击 REJECT 按钮 → emit action=REJECT', async () => {
    const wrapper = mount(HitlSheet, { props: { reason: '测试', advice: '建议' } })
    const rejectBtn = wrapper.findAll('button').find(b => b.text().includes('REJECT'))
    expect(rejectBtn).toBeTruthy()
    await rejectBtn!.trigger('click')
    expect(wrapper.emitted('action')?.[0]).toEqual(['REJECT'])
  })

  it('无 advice 时不渲染建议区域', () => {
    const wrapper = mount(HitlSheet, { props: { reason: '测试' } })
    expect(wrapper.find('.advise').exists()).toBe(false)
  })

  it('点击关闭按钮或背景遮罩 → emit close', async () => {
    const wrapper = mount(HitlSheet, { props: { reason: '测试' } })
    const closeBtn = wrapper.find('.sheet-close')
    expect(closeBtn.exists()).toBe(true)
    await closeBtn.trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()

    const scrim = wrapper.find('.scrim')
    await scrim.trigger('click')
    expect(wrapper.emitted('close')?.length).toBe(2)
  })
})
