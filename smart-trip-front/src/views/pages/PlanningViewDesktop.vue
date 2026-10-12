<script setup lang="ts">
/**
 * 页面：② 规划进行中（桌面端）—— 轮询 GET /trip-plan/{id}，status 驱动跳转
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AgentProgressDesktop from '@/components/planning/AgentProgressDesktop.vue'
import { useTripPlanStore } from '@/stores/trip-plan'
import type { AgentProgressItem, PlanStatus, TravelStyle } from '@/models/trip-plan'

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

// status → 5 Agent 进度状态
const STEP: Record<string, number> = { processing: 0, analyzing: 0, planning: 1, assembling: 2, budgeting: 3 }
const base: Omit<AgentProgressItem, 'state'>[] = [
  { key: 'xhs', name: '笔记分析', emoji: '📕', desc: 'XHS 检索并分析小红书笔记' },
  { key: 'route', name: '路线规划', emoji: '🗺️', desc: 'Route 规划景点间交通' },
  { key: 'itinerary', name: '行程编排', emoji: '📅', desc: 'Itinerary 编排每日行程' },
  { key: 'budget', name: '费用统筹', emoji: '💰', desc: 'Budget 估算与预算管控' },
]

const status = computed<PlanStatus>(() => store.detail?.status ?? 'analyzing')
const step = computed(() => STEP[status.value] ?? 4)
const agents = computed<AgentProgressItem[]>(() =>
  base.map((a, i) => ({
    ...a,
    state: i < step.value ? 'done' : i === step.value ? 'running' : 'waiting',
  })),
)

// 使用 store.request 中的实际预算，进度模拟按 step 比例分配
const requestBudget = computed(() => store.request.budget || 8000)
const budgetUsed = computed(() => Math.round(requestBudget.value * (step.value / (base.length + 1))))
const travelStyleLabel = computed(() => TRAVEL_STYLE_LABEL[store.request.travelStyle] || store.request.travelStyle)
const title = computed(() => {
  const r = store.request
  return `${r.destination} · ${r.days} 日 · ${travelStyleLabel.value} · ¥${r.budget.toLocaleString()}`
})

// 前端计时器：记录进入规划页面的时间，每秒刷新 elapsed 显示
const startTime = Date.now()
const elapsed = ref('00:00')
let elapsedTimer: ReturnType<typeof setInterval> | null = null

function tickElapsed() {
  const diffSec = Math.floor((Date.now() - startTime) / 1000)
  const mm = String(Math.floor(diffSec / 60)).padStart(2, '0')
  const ss = String(diffSec % 60).padStart(2, '0')
  elapsed.value = `${mm}:${ss}`
}

onMounted(() => {
  tickElapsed()
  elapsedTimer = setInterval(tickElapsed, 1000)
})

watch(status, (s, old) => {
  if (s === 'review' || s === 'plan_review') {
    if (old !== 'processing') {
      router.replace({ name: 'plan', params: { planId } })
    }
  } else if (s === 'confirmed') {
    if (old === 'review' || old === 'plan_review' || old === 'processing') {
      router.replace({ name: 'result', params: { planId } })
    } else {
      router.replace({ name: 'plan', params: { planId } })
    }
  }
}, { immediate: true })

onMounted(() => store.startPolling(planId))
onUnmounted(() => {
  store.stopPolling()
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
})

const cancel = () => {
  store.stopPolling()
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
  router.push({ name: 'intent' })
}
</script>

<template>
  <div class="planning-container">
    <div class="page-header">
      <div class="progress-indicator">
        <span class="step-number">{{ step + 1 }}/{{ base.length + 1 }}</span>
        <span class="step-text">正在规划中...</span>
      </div>
      <h1 class="title">{{ title }}</h1>
      <p class="subtitle">AI 智能体团队协作中，请稍候</p>
    </div>

    <div class="progress-card card">
      <AgentProgressDesktop
        :status="status"
        :agents="agents"
        :budget-used="budgetUsed"
        :budget-total="requestBudget"
        :elapsed="elapsed"
        :title="title"
        @cancel="cancel"
      />
    </div>
  </div>
</template>

<style scoped>
.planning-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
}

.page-header {
  text-align: center;
  margin-bottom: 48px;
}

.progress-indicator {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  background: #ffe9ed;
  color: #ff2442;
  padding: 8px 16px;
  border-radius: 24px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 16px;
}

.step-number {
  font-size: 16px;
  font-weight: 700;
}

.step-text {
  font-size: 13px;
}

.title {
  font-size: 32px;
  font-weight: 800;
  color: #2b2b2b;
  margin-bottom: 12px;
  line-height: 1.3;
}

.subtitle {
  font-size: 16px;
  color: #888;
}

.progress-card {
  background: #fff;
  border-radius: 20px;
  padding: 48px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  border: 1px solid #e8e8e8;
}
</style>
