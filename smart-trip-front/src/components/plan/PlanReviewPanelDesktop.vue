<script setup lang="ts">
/**
 * PlanReviewPanelDesktop —— 桌面端计划草案确认面板（C-018 T-11c）
 *
 * PC 端宽屏布局：左侧草案正文（Markdown）+ 右侧固定操作栏。
 * 支持骨架屏、错误横幅、空草案降级。
 */
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useTripPlanStore } from '@/stores/trip-plan'
import type { InterveneAction } from '@/models/trip-plan'

const props = withDefaults(defineProps<{
  /** 草案 Markdown 内容 */
  planDraft?: string
  /** 是否加载中 */
  loading?: boolean
  /** 错误信息 */
  error?: string
}>(), {
  planDraft: '',
  loading: false,
  error: '',
})

const emit = defineEmits<{
  confirm: []
  reject: []
}>()

const store = useTripPlanStore()
const router = useRouter()
const route = useRoute()
const planId = route.params.planId as string

const hasDraft = computed(() => props.planDraft && props.planDraft.length > 0)
const isDisabled = computed(() => props.loading)

const handleAction = async (action: InterveneAction) => {
  if (isDisabled.value) return
  await store.intervene(action)
  if (action === 'CONFIRM') {
    emit('confirm')
    // 直接导航到规划进度页，不依赖父组件 emit 链（C-018 关键约束）
    router.push({ name: 'planning', params: { planId }, query: { t: Date.now() } })
  } else if (action === 'REJECT') {
    emit('reject')
    // 拒绝后返回规划页重新提交（C-018 用户期望）
    router.push({ name: 'intent' })
  }
}
</script>

<template>
  <div class="plan-review-panel-desktop">
    <!-- Loading 骨架屏 -->
    <div v-if="loading" class="loading-state">
      <div class="skeleton-row">
        <div class="skeleton skeleton-title"></div>
        <div class="skeleton skeleton-line"></div>
        <div class="skeleton skeleton-line"></div>
        <div class="skeleton skeleton-line short"></div>
        <div class="skeleton skeleton-line"></div>
        <div class="skeleton skeleton-line"></div>
        <div class="skeleton skeleton-line short"></div>
      </div>
      <div class="skeleton-sidebar">
        <div class="skeleton skeleton-sidebar-btn"></div>
        <div class="skeleton skeleton-sidebar-btn"></div>
      </div>
    </div>

    <!-- 空草案降级 -->
    <div v-else-if="!hasDraft && !error" class="empty-state">
      <div class="empty-icon">📋</div>
      <div class="empty-text">暂无草案</div>
      <div class="empty-hint">AI 正在生成计划草案，请稍候…</div>
    </div>

    <!-- 草案展示（含错误横幅） -->
    <div v-else class="draft-layout">
      <!-- 左侧：草案正文 -->
      <div class="draft-main">
        <div v-if="error" class="error-banner">
          <span class="error-banner-icon">⚠️</span>
          <span class="error-banner-text">{{ error }}</span>
        </div>
        <div class="draft-content">
          <pre class="draft-markdown">{{ planDraft }}</pre>
        </div>
      </div>

      <!-- 右侧：操作栏 -->
      <div class="draft-sidebar">
        <div class="sidebar-card">
          <div class="sidebar-title">审阅计划草案</div>
          <div class="sidebar-desc">AI 已起草了执行计划，请审阅后决定是否放行执行</div>
          <div class="sidebar-divider"></div>
          <button
            class="btn btn-confirm"
            :disabled="isDisabled"
            @click="handleAction('CONFIRM')"
          >
            <span class="btn-icon">✅</span>
            <span class="btn-label">确认，开始执行</span>
          </button>
          <button
            class="btn btn-reject"
            :disabled="isDisabled"
            @click="handleAction('REJECT')"
          >
            <span class="btn-icon">✕</span>
            <span class="btn-label">拒绝，重新提交</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.plan-review-panel-desktop {
  width: 100%;
  min-height: 500px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  border: 1px solid #e8e8e8;
  overflow: hidden;
}

/* Loading 骨架屏 */
.loading-state {
  display: flex;
  gap: 0;
  min-height: 500px;
}

.skeleton-row {
  flex: 1;
  padding: 40px;
  border-right: 1px solid #eee;
}

.skeleton-sidebar {
  width: 320px;
  padding: 40px 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.skeleton {
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 8px;
  margin-bottom: 16px;
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

.skeleton-title {
  height: 32px;
  width: 50%;
  margin-bottom: 32px;
}

.skeleton-line {
  height: 18px;
  width: 100%;
}

.skeleton-line.short {
  width: 65%;
}

.skeleton-sidebar-btn {
  height: 52px;
  width: 100%;
  border-radius: 12px;
}

/* 空态 */
.empty-state {
  text-align: center;
  padding: 100px 20px;
}

.empty-icon {
  font-size: 72px;
  margin-bottom: 24px;
}

.empty-text {
  font-size: 22px;
  font-weight: 600;
  color: #666;
  margin-bottom: 12px;
}

.empty-hint {
  font-size: 16px;
  color: #999;
}

/* 双栏布局 */
.draft-layout {
  display: flex;
  gap: 0;
  min-height: 500px;
}

.draft-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #eee;
  min-width: 0;
}

.draft-sidebar {
  width: 320px;
  flex-shrink: 0;
  padding: 32px;
  background: #fafafa;
}

/* 错误横幅 */
.error-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 24px;
  background: #fff2f0;
  border-bottom: 1px solid #ffccc7;
  color: #e61e3a;
  font-size: 14px;
}

.error-banner-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.error-banner-text {
  flex: 1;
}

/* 草案内容 */
.draft-content {
  padding: 32px 40px;
  flex: 1;
  overflow-y: auto;
  max-height: 65vh;
}

.draft-markdown {
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.9;
  color: #333;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f8f8f8;
  padding: 28px 32px;
  border-radius: 12px;
  margin: 0;
  border: 1px solid #eee;
}

/* 侧边栏 */
.sidebar-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 32px;
}

.sidebar-title {
  font-size: 18px;
  font-weight: 700;
  color: #2b2b2b;
}

.sidebar-desc {
  font-size: 14px;
  color: #888;
  line-height: 1.6;
}

.sidebar-divider {
  height: 1px;
  background: #eee;
  margin: 4px 0;
}

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px 24px;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  width: 100%;
  letter-spacing: 0.3px;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}

.btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.btn:active:not(:disabled) {
  transform: translateY(0);
}

.btn-confirm {
  background: linear-gradient(135deg, #ff2442, #e61e3a);
  color: #fff;
}

.btn-reject {
  background: #fff;
  color: #666;
  border: 2px solid #ddd;
}

.btn-reject:hover:not(:disabled) {
  background: #fafafa;
  color: #333;
  border-color: #bbb;
}

.btn-icon {
  font-size: 20px;
}

.btn-label {
  letter-spacing: 0.5px;
}
</style>