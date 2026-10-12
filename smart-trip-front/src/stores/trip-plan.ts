import { defineStore } from 'pinia'
import { ref } from 'vue'
import { pollIntervalMs, useMock } from '@/config'
import * as api from '@/services/trip-plan'
import type {
  FeedbackRating,
  InterveneAction,
  PlanStatus,
  TripPlanDetail,
  TripPlanRequest,
} from '@/models/trip-plan'
import { MOCK_PLAN_ID, mockPlanConfirmed, mockPlanDraft, mockPlanReview, mockRequest } from '@/constants/mock'

// Mock 模式下规划状态机的推进顺序
const MOCK_FLOW: PlanStatus[] = ['analyzing', 'planning', 'plan_review', 'assembling', 'budgeting', 'review']

export const useTripPlanStore = defineStore('trip-plan', () => {
  const request = ref<TripPlanRequest>({ ...mockRequest })
  const planId = ref<string>('')
  const detail = ref<TripPlanDetail | null>(null)
  const loading = ref(false)
  const error = ref<string>('')

  let timer: ReturnType<typeof setTimeout> | null = null
  let mockTick = 0

  const stopPolling = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  // —— 提交规划请求（① 主触发）——
  const submit = async (payload: TripPlanRequest): Promise<string> => {
    request.value = { ...payload }
    error.value = ''
    if (useMock) {
      planId.value = MOCK_PLAN_ID
      mockTick = 0
      detail.value = { planId: MOCK_PLAN_ID, status: 'analyzing', requiresHumanIntervention: false }
      return planId.value
    }
    loading.value = true
    try {
      const res = await api.submitTripPlan(payload)
      planId.value = res.planId
      detail.value = { planId: res.planId, status: res.status, requiresHumanIntervention: false }
      return res.planId
    } catch (e) {
      error.value = (e as Error).message
      throw e
    } finally {
      loading.value = false
    }
  }

  // —— 拉取一次进度/结果（② 轮询）——
  const fetchOnce = async (id: string) => {
    planId.value = id
    if (useMock) {
      mockTick += 1
      const idx = Math.min(mockTick, MOCK_FLOW.length - 1)
      const status = MOCK_FLOW[idx] as PlanStatus
      detail.value =
        status === 'plan_review' ? { ...mockPlanDraft } :
        status === 'review' ? { ...mockPlanReview } : { planId: id, status, requiresHumanIntervention: false }
      return
    }
    try {
      detail.value = await api.getTripPlan(id)
      if (detail.value?.request) {
        request.value = { ...request.value, ...detail.value.request }
      } else if (detail.value?.plan) {
        const days = detail.value.plan.days
        if (days?.length) {
          const label = days[0].cityLabel ?? ''
          const city = label.replace(/^Day\d+\s*/, '')
          if (city) {
            request.value = { ...request.value, destination: city, days: days.length }
          }
        }
      }
    } catch (e) {
      error.value = (e as Error).message
    }
  }

  // —— 启动轮询，直到 review / confirmed / failed ——
  const startPolling = (id: string) => {
    planId.value = id
    stopPolling()
    const loop = async () => {
      await fetchOnce(id)
      const s = detail.value?.status
      if (s && ['plan_review', 'review', 'confirmed', 'failed'].includes(s)) {
        stopPolling()
        return
      }
      timer = setTimeout(loop, pollIntervalMs)
    }
    loop()
  }

  // —— 人工介入（③ HITL）——
  const intervene = async (action: InterveneAction, changes?: Record<string, unknown>) => {
    const targetPlanId = planId.value || detail.value?.planId || ''
    if (useMock) {
      if (action === 'CONFIRM') detail.value = { ...mockPlanConfirmed }
      else if (action === 'REJECT') {
        mockTick = 0
        detail.value = { planId: targetPlanId, status: 'planning', requiresHumanIntervention: false }
        startPolling(targetPlanId)
      }
      return
    }
    detail.value = await api.intervenePlan(targetPlanId, action, changes)
    // CONFIRM 后不在此启动轮询，由 PlanningView/PlanningViewDesktop 的 onMounted 负责
    // 避免 fetchOnce 读取到后端旧状态覆盖 local processing 状态
  }

  // —— 用户反馈（④）——
  const feedback = async (rating: FeedbackRating, modifiedItems: string[] = [], comment = '') => {
    if (useMock) return
    await api.submitFeedback(planId.value, rating, modifiedItems, comment)
  }

  return {
    request,
    planId,
    detail,
    loading,
    error,
    submit,
    fetchOnce,
    startPolling,
    stopPolling,
    intervene,
    feedback,
  }
})
