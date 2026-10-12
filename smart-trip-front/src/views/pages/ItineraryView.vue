<script setup lang="ts">
/** 页面：④⑤ 行程结果 + 人工介入（HITL） */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PhoneFrame from '@/components/PhoneFrame.vue'
import ItineraryTimeline from '@/components/itinerary/ItineraryTimeline.vue'
import HitlSheet from '@/components/hitl/HitlSheet.vue'
import PlanReviewPanel from '@/components/plan/PlanReviewPanel.vue'
import { mockPlanReview } from '@/constants/mock'
import { useMock } from '@/config'
import { useTripPlanStore } from '@/stores/trip-plan'
import type { InterveneAction } from '@/models/trip-plan'

const route = useRoute()
const router = useRouter()
const store = useTripPlanStore()
const planId = route.params.planId as string

onMounted(async () => {
  if (!store.detail || store.detail.planId !== planId || !store.detail.plan) {
    await store.fetchOnce(planId)
  }
})

const detail = computed(() => (useMock ? store.detail ?? mockPlanReview : store.detail))
const plan = computed(() => detail.value?.plan)
const draft = computed(() => detail.value?.status !== 'confirmed')
const isPlanReview = computed(() => store.detail?.status === 'plan_review')
const planDraft = computed(() => store.detail?.planDraft ?? (useMock ? mockPlanReview.planDraft : undefined))
const planReviewLoading = computed(() => store.loading)
const planReviewError = computed(() => store.error ?? '')
const isHitlModalOpen = ref(false)

// 初始若后端确需 HITL 介入，自动开启
watch(() => detail.value?.requiresHumanIntervention, (val) => {
  if (val) isHitlModalOpen.value = true
}, { immediate: true })

const activeIndex = ref(1)

const title = computed(() => {
  const r = store.request
  return `${r.destination} ${r.days} 日行程`
})

const onAction = async (action: InterveneAction) => {
  isHitlModalOpen.value = false
  if (action === 'MODIFY') {
    // MODIFY：关闭弹窗，留在当前页进入草案微调编辑模式
    if (store.detail) {
      store.detail.status = 'review'
      store.detail.requiresHumanIntervention = false
    }
    return
  }

  try {
    await store.intervene(action)
    if (action === 'CONFIRM') {
      if (store.detail) {
        store.detail.status = 'confirmed'
        store.detail.requiresHumanIntervention = false
      }
      router.push({ name: 'result', params: { planId } })
    } else if (action === 'REJECT') {
      router.push({ name: 'planning', params: { planId } })
    }
  } catch (err: unknown) {
    const msg = (err as Error)?.message || '操作失败，请重试'
    alert(msg)
  }
}

const onEditPlan = () => {
  // 切换回 review（草案待确认）编辑模式，允许用户直接在时间轴增删微调，不弹出确认框
  if (store.detail) {
    store.detail.status = 'review'
    store.detail.requiresHumanIntervention = false
  }
  isHitlModalOpen.value = false
}

const onRemoveAttraction = (payload: { dayIndex: number; attractionId: string }) => {
  const currentPlan = store.detail?.plan
  const currentDay = currentPlan?.days.find((d) => d.index === payload.dayIndex)
  const spot = currentDay?.attractions.find((a) => a.id === payload.attractionId)
  const spotName = spot?.name || '选中的景点'

  if (!confirm(`确定要从第 ${payload.dayIndex} 天行程中移除「${spotName}」吗？`)) {
    return
  }

  if (currentDay && spot) {
    currentDay.attractions = currentDay.attractions.filter((a) => a.id !== payload.attractionId)
    if (currentDay.dailyCost && spot.ticket) {
      currentDay.dailyCost = Math.max(0, currentDay.dailyCost - spot.ticket)
    }
    if (currentPlan.totalCost && spot.ticket) {
      currentPlan.totalCost = Math.max(0, currentPlan.totalCost - spot.ticket)
    }
  }
}

const onRemoveMeal = (payload: { dayIndex: number; mealType: string }) => {
  const currentPlan = store.detail?.plan
  const currentDay = currentPlan?.days.find((d) => d.index === payload.dayIndex)
  const meal = currentDay?.meals?.find((m) => m.mealType === payload.mealType)
  const mealName = meal?.restaurantName ? `${meal.mealType === 'lunch' ? '午餐' : '晚餐'}（${meal.restaurantName}）` : (payload.mealType === 'lunch' ? '午餐' : '晚餐')

  if (!confirm(`确定要从第 ${payload.dayIndex} 天行程中移除「${mealName}」安排吗？`)) {
    return
  }

  if (currentDay && meal) {
    currentDay.meals = currentDay.meals.filter((m) => m.mealType !== payload.mealType)
    const headcount = store.request?.headcount || 1
    const mealTotal = (meal.perCapita || 0) * headcount
    if (currentDay.dailyCost && mealTotal) {
      currentDay.dailyCost = Math.max(0, currentDay.dailyCost - mealTotal)
    }
    if (currentPlan.totalCost && mealTotal) {
      currentPlan.totalCost = Math.max(0, currentPlan.totalCost - mealTotal)
    }
  }
}

const onPlanConfirmed = () => {
  // CONFIRM 后跳转到规划进度页，前端通过轮询获取进度
  router.push({ name: 'planning', params: { planId }, query: { t: Date.now() } })
}

const onPlanRejected = () => {
  router.push({ name: 'planning', params: { planId } })
}

// 监听后端异步执行完成状态（C-018：CONFIRM 后轮询，状态变化时自动导航）
watch(() => store.detail?.status, (newStatus) => {
  if (newStatus === 'confirmed') {
    router.push({ name: 'result', params: { planId } })
  } else if (newStatus === 'failed') {
    store.error = store.detail?.failureReason ?? '规划执行失败'
  }
})
</script>

<style scoped>
.draft-markdown {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  color: #333;
  background: #fafafa;
  padding: 16px;
  border-radius: 12px;
  max-height: 60vh;
  overflow-y: auto;
  font-family: inherit;
  margin: 0;
}
</style>

<template>
  <div class="stage">
    <PhoneFrame>
      <template v-if="isPlanReview">
        <PlanReviewPanel
          :plan-draft="planDraft"
          :loading="planReviewLoading"
          :error="planReviewError"
          @confirm="onPlanConfirmed"
          @reject="onPlanRejected"
        />
      </template>
      <template v-else>
      <template v-if="plan">
        <ItineraryTimeline
          :days="plan.days"
          :active-index="activeIndex"
          :draft="draft"
          :title="title"
          @select="activeIndex = $event"
          @open-confirm="onAction('CONFIRM')"
          @edit-plan="onEditPlan"
          @remove-attraction="onRemoveAttraction"
          @remove-meal="onRemoveMeal"
        />
        <HitlSheet
          v-if="isHitlModalOpen"
          :reason="detail?.interventionReason"
          :advice="detail?.interventionAdvice"
          @action="onAction"
          @close="isHitlModalOpen = false"
        />
      </template>
      <div v-else class="pad muted">正在加载真实行程结果…</div>
      </template>
    </PhoneFrame>
  </div>
</template>
