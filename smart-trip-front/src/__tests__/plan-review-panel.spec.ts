/**
 * PlanReviewPanel 组件测试 —— 草案确认面板（C-018 T-11）
 *
 * 覆盖：草案展示、CONFIRM/REJECT 按钮交互、loading 态、错误反馈、空草案降级
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

// 在 mount 前 mock store 和 router
const mockIntervene = vi.fn()
const mockRouterPush = vi.fn()
vi.mock('@/stores/trip-plan', () => ({
  useTripPlanStore: () => ({
    intervene: mockIntervene,
    planId: 'P-TEST-001',
  }),
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockRouterPush }),
  useRoute: () => ({ params: { planId: 'P-TEST-001' } }),
}))

// 动态导入组件（组件尚未创建，测试会失败 —— TDD Red 阶段）
const PlanReviewPanel = () => import('@/components/plan/PlanReviewPanel.vue')

describe('PlanReviewPanel.vue', () => {
  beforeEach(() => {
    mockIntervene.mockReset()
  })

  it('渲染草案 Markdown 内容', async () => {
    const comp = (await PlanReviewPanel()).default
    const wrapper = mount(comp, {
      props: {
        planDraft: '# 云南5日游计划\n- 第1天: 昆明',
        loading: false,
        error: '',
      },
    })
    expect(wrapper.text()).toContain('云南5日游计划')
    expect(wrapper.text()).toContain('第1天: 昆明')
  })

  it('loading=true 时显示骨架屏，不显示草案内容', async () => {
    const comp = (await PlanReviewPanel()).default
    const wrapper = mount(comp, {
      props: {
        planDraft: '',
        loading: true,
        error: '',
      },
    })
    // 骨架屏应有多个占位元素
    const skeletons = wrapper.findAll('.skeleton')
    expect(skeletons.length).toBeGreaterThanOrEqual(3)
    expect(wrapper.text()).not.toContain('草案')
  })

  it('error 非空时显示错误信息', async () => {
    const comp = (await PlanReviewPanel()).default
    const wrapper = mount(comp, {
      props: {
        planDraft: '',
        loading: false,
        error: '加载失败，请重试',
      },
    })
    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.find('.error-feedback').exists()).toBe(true)
  })

  it('空草案时显示空态提示', async () => {
    const comp = (await PlanReviewPanel()).default
    const wrapper = mount(comp, {
      props: {
        planDraft: '',
        loading: false,
        error: '',
      },
    })
    expect(wrapper.text()).toContain('暂无草案')
  })

  it('点击 CONFIRM 按钮触发 intervene("CONFIRM")', async () => {
    const comp = (await PlanReviewPanel()).default
    const wrapper = mount(comp, {
      props: {
        planDraft: '# 测试草案',
        loading: false,
        error: '',
      },
    })
    await wrapper.find('.btn-confirm').trigger('click')
    expect(mockIntervene).toHaveBeenCalledWith('CONFIRM')
  })

  it('点击 REJECT 按钮触发 intervene("REJECT")', async () => {
    const comp = (await PlanReviewPanel()).default
    const wrapper = mount(comp, {
      props: {
        planDraft: '# 测试草案',
        loading: false,
        error: '',
      },
    })
    await wrapper.find('.btn-reject').trigger('click')
    expect(mockIntervene).toHaveBeenCalledWith('REJECT')
  })

  it('loading 时显示骨架屏，无按钮', async () => {
    const comp = (await PlanReviewPanel()).default
    const wrapper = mount(comp, {
      props: {
        planDraft: '# 测试草案',
        loading: true,
        error: '',
      },
    })
    // loading 时展示骨架屏，不展示按钮
    expect(wrapper.find('.btn-confirm').exists()).toBe(false)
    expect(wrapper.find('.btn-reject').exists()).toBe(false)
    expect(wrapper.find('.skeleton').exists()).toBe(true)
  })

  it('error 时按钮仍可用（用户可以重试）', async () => {
    const comp = (await PlanReviewPanel()).default
    const wrapper = mount(comp, {
      props: {
        planDraft: '# 测试草案',
        loading: false,
        error: '网络错误',
      },
    })
    expect(wrapper.find('.btn-confirm').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.btn-reject').attributes('disabled')).toBeUndefined()
  })
})