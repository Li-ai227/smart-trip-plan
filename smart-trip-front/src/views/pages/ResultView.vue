<script setup lang="ts">
/** 页面：⑥ 确认 · 导出 · 反馈 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PhoneFrame from '@/components/PhoneFrame.vue'
import FeedbackExport from '@/components/feedback/FeedbackExport.vue'
import { mockPlanConfirmed } from '@/constants/mock'
import { useMock } from '@/config'
import { useTripPlanStore } from '@/stores/trip-plan'
import { exportPdf as apiExportPdf } from '@/services/trip-plan'
import type { FeedbackRating, TravelStyle } from '@/models/trip-plan'

/** 旅行风格 → 中文标签映射 */
const TRAVEL_STYLE_LABEL: Record<TravelStyle, string> = {
  FAMILY: '亲子',
  COUPLE: '情侣',
  SOLO: '独自',
  TEAM: '团建',
}

const route = useRoute()
const router = useRouter()
const store = useTripPlanStore()
const planId = route.params.planId as string

onMounted(async () => {
  if (!store.detail || store.detail.planId !== planId || !store.detail.plan) {
    await store.fetchOnce(planId)
  }
  if (store.detail?.status === 'review' || store.detail?.status === 'plan_review') {
    router.replace({ name: 'plan', params: { planId } })
  }
})

const plan = computed(() => (useMock ? store.detail?.plan ?? mockPlanConfirmed.plan! : store.detail?.plan))
const travelStyleLabel = computed(() => TRAVEL_STYLE_LABEL[store.request.travelStyle] || store.request.travelStyle)
const title = computed(() => {
  const r = store.request
  return `${r.destination} ${r.days} 日 · ${travelStyleLabel.value}`
})

const onFeedback = (rating: FeedbackRating) => store.feedback(rating)
const exporting = ref(false)
const exportError = ref('')

const onExport = async () => {
  if (exporting.value) return
  exporting.value = true
  exportError.value = ''
  try {
    await apiExportPdf(planId)
  } catch (e: unknown) {
    exportError.value = (e as Error).message || '导出失败，请稍后重试'
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="stage">
    <PhoneFrame>
      <FeedbackExport v-if="plan" :plan="plan" :title="title" :plan-id="planId" :exporting="exporting" :export-error="exportError" @feedback="onFeedback" @export="onExport" />
      <div v-else class="pad muted">正在加载真实确认结果…</div>
    </PhoneFrame>
  </div>
</template>
