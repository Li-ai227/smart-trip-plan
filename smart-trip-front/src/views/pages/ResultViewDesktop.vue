<script setup lang="ts">
/**
 * 页面：⑥ 确认·导出·反馈（桌面端）
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FeedbackExportDesktop from '@/components/feedback/FeedbackExportDesktop.vue'
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
  <div class="result-container" v-if="plan">
    <div class="success-header">
      <div class="success-icon">✓</div>
      <h1 class="success-title">行程规划完成！</h1>
      <p class="success-subtitle">{{ title }}</p>
    </div>

    <FeedbackExportDesktop :plan="plan" :title="title" :plan-id="planId" :exporting="exporting" :export-error="exportError" @feedback="onFeedback" @export="onExport" />
  </div>

  <div v-else class="loading-placeholder">
    <div class="loading-spinner"></div>
    <p>正在加载真实确认结果…</p>
  </div>
</template>

<style scoped>
.result-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 40px 20px;
}

.success-header {
  text-align: center;
  margin-bottom: 48px;
}

.success-icon {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #e8faf1 0%, #d4f4e2 100%);
  color: #16c784;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  font-weight: 800;
  margin: 0 auto 24px;
  box-shadow: 0 8px 24px rgba(22, 199, 132, 0.2);
}

.success-title {
  font-size: 36px;
  font-weight: 800;
  color: #2b2b2b;
  margin-bottom: 12px;
}

.success-subtitle {
  font-size: 18px;
  color: #888;
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
</style>
