<script setup lang="ts">
/**
 * 页面：④⑤ 行程结果 + 人工介入（HITL）—— 桌面端版本
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ItineraryTimelineDesktop from '@/components/itinerary/ItineraryTimelineDesktop.vue'
import HitlSheetDesktop from '@/components/hitl/HitlSheetDesktop.vue'
import PlanReviewPanelDesktop from '@/components/plan/PlanReviewPanelDesktop.vue'
import { mockPlanDraft, mockPlanReview } from '@/constants/mock'
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

const detail = computed(() => {
  if (useMock) {
    if (store.detail) return store.detail
    // 按路由参数选择合适的 mock 回退
    if (route.name === 'plan' && route.query.mock === 'plan_review') return mockPlanDraft
    return mockPlanReview
  }
  return store.detail
})
const plan = computed(() => detail.value?.plan)
const draft = computed(() => detail.value?.status !== 'confirmed')
const isPlanReview = computed(() => store.detail?.status === 'plan_review')
const planDraft = computed(() => store.detail?.planDraft)
const planReviewLoading = computed(() => store.loading)
const planReviewError = computed(() => store.error ?? '')
const isHitlModalOpen = ref(false)

const activeIndex = ref(1)

const title = computed(() => {
  const r = store.request
  return `${r.destination} ${r.days} 日行程`
})

// 获取当前选中的天
const activeDay = computed(() => {
  if (!plan.value) return undefined
  return plan.value.days.find((d) => d.index === activeIndex.value) ?? plan.value.days[0]
})

const onAction = async (action: InterveneAction) => {
  isHitlModalOpen.value = false
  try {
    await store.intervene(action)
    if (action === 'CONFIRM') {
      if (store.detail) {
        store.detail.status = 'confirmed'
        store.detail.requiresHumanIntervention = false
      }
    } else if (action === 'REJECT') {
      router.push({ name: 'planning', params: { planId } })
    }
  } catch (err: unknown) {
    const msg = (err as Error)?.message || '操作失败，请重试'
    alert(msg)
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

<template>
  <div class="itinerary-container">
    <div class="page-header" v-if="plan">
      <h1 class="title">📅 您的专属行程已生成</h1>
      <p class="subtitle">{{ activeDay?.cityLabel ?? store.request.destination }} · {{ plan.days.length }}天精彩之旅</p>
    </div>

    <div v-if="isPlanReview" class="plan-review-section">
        <div class="review-header">
          <h1 class="review-title">📋 审阅计划草案</h1>
          <p class="review-subtitle">AI 已起草了执行计划，请审阅后决定是否放行执行</p>
        </div>
        <PlanReviewPanelDesktop
          :plan-draft="planDraft"
          :loading="planReviewLoading"
          :error="planReviewError"
          @confirm="onPlanConfirmed"
          @reject="onPlanRejected"
        />
    </div>

    <div v-else-if="plan" class="timeline-section">
      <ItineraryTimelineDesktop
        :days="plan.days"
        :active-index="activeIndex"
        :draft="draft"
        :title="title"
        @select="activeIndex = $event"
        @open-confirm="isHitlModalOpen = true"
      />
    </div>

    <HitlSheetDesktop
      v-if="isHitlModalOpen"
      :reason="detail?.interventionReason"
      :advice="detail?.interventionAdvice"
      @action="onAction"
      @close="isHitlModalOpen = false"
    />

    <div v-else-if="!detail" class="loading-placeholder">
      <div class="loading-spinner"></div>
      <p>正在加载真实行程结果…</p>
    </div>
  </div>
</template>

<style scoped>
.itinerary-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 40px 20px;
}

.page-header {
  text-align: center;
  margin-bottom: 48px;
}

.title {
  font-size: 36px;
  font-weight: 800;
  color: #2b2b2b;
  margin-bottom: 12px;
}

.subtitle {
  font-size: 18px;
  color: #888;
}

.timeline-section {
  background: #fff;
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  border: 1px solid #e8e8e8;
}

.loading-placeholder {
  text-align: center;
  padding: 120px 20px;
}

.loading-spinner {
  width: 60px;
  height: 60px;
  border: 4px solid #f0f0f0;
  border-top-color: #ff2442;
  border-radius: 50%;
  margin: 0 auto 24px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-placeholder p {
  font-size: 16px;
  color: #888;
}

/* Plan Review 草案确认 */
.plan-review-section {
  max-width: 1100px;
  margin: 0 auto;
}

.review-header {
  text-align: center;
  margin-bottom: 40px;
}

.review-title {
  font-size: 32px;
  font-weight: 800;
  color: #2b2b2b;
  margin-bottom: 12px;
}

.review-subtitle {
  font-size: 16px;
  color: #888;
}
</style>
