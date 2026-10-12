<script setup lang="ts">
/**
 * PlanReviewPanel —— 计划草案确认面板（C-018 T-11）
 *
 * 展示 AI 生成的计划草案，提供 CONFIRM/REJECT 按钮。
 * 支持 loading 骨架屏、错误反馈、空草案降级。
 */
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useTripPlanStore } from '@/stores/trip-plan'
import type { InterveneAction } from '@/models/trip-plan'

const props = withDefaults(defineProps<{
  /** 草案 Markdown 内容 */
  planDraft?: string
  /** 是否加载中 （CONFIRM/REJECT 请求中） */
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
  <div class="plan-review-panel">
    <!-- Loading 骨架屏 -->
    <div v-if="loading" class="loading-state">
      <div class="skeleton skeleton-title"></div>
      <div class="skeleton skeleton-line"></div>
      <div class="skeleton skeleton-line"></div>
      <div class="skeleton skeleton-line short"></div>
      <div class="skeleton skeleton-line"></div>
      <div class="skeleton skeleton-line"></div>
      <div class="skeleton skeleton-line short"></div>
      <div class="skeleton skeleton-actions">
        <div class="skeleton skeleton-btn"></div>
        <div class="skeleton skeleton-btn"></div>
      </div>
    </div>

    <!-- 错误反馈（无草案时的纯错误页） -->
    <div v-else-if="error && !hasDraft" class="error-feedback">
      <div class="error-icon">⚠️</div>
      <div class="error-text">{{ error }}</div>
      <div class="error-hint">请检查网络连接后重试，或联系客服</div>
    </div>

    <!-- 空草案降级 -->
    <div v-else-if="!hasDraft" class="empty-state">
      <div class="empty-icon">📋</div>
      <div class="empty-text">暂无草案</div>
      <div class="empty-hint">AI 正在生成计划草案，请稍候…</div>
    </div>

    <!-- 草案展示（含可能出现的错误横幅） -->
    <div v-else class="draft-section">
      <div v-if="error" class="error-banner">
        <span class="error-banner-icon">⚠️</span>
        <span class="error-banner-text">{{ error }}</span>
      </div>
      <div class="draft-header">
        <div class="draft-title">📋 执行计划草案</div>
        <div class="draft-subtitle">AI 已起草了执行计划，请审阅后决定是否放行执行</div>
      </div>
      <div class="draft-content">
        <pre class="draft-markdown">{{ planDraft }}</pre>
      </div>
      <div class="draft-actions">
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
</template>

<style scoped>
.plan-review-panel {
  width: 100%;
}

/* Loading 骨架屏 */
.loading-state {
  padding: 20px;
}

.skeleton {
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 8px;
  margin-bottom: 12px;
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

.skeleton-title {
  height: 24px;
  width: 60%;
  margin-bottom: 20px;
}

.skeleton-line {
  height: 14px;
  width: 100%;
}

.skeleton-line.short {
  width: 70%;
}

.skeleton-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  background: none;
  animation: none;
}

.skeleton-btn {
  height: 44px;
  flex: 1;
  border-radius: 12px;
}

/* 错误反馈 */
.error-feedback {
  text-align: center;
  padding: 40px 20px;
}

.error-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.error-text {
  font-size: 16px;
  font-weight: 600;
  color: #e61e3a;
  margin-bottom: 8px;
}

.error-hint {
  font-size: 14px;
  color: #999;
}

/* 空态 */
.empty-state {
  text-align: center;
  padding: 40px 20px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
  font-weight: 600;
  color: #666;
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 14px;
  color: #999;
}

/* 错误横幅（草案上方） */
.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: #fff2f0;
  border-bottom: 1px solid #ffccc7;
  color: #e61e3a;
  font-size: 14px;
}

.error-banner-icon {
  font-size: 16px;
}

.error-banner-text {
  flex: 1;
}

/* 草案展示 */
.draft-section {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.draft-header {
  padding: 20px 20px 12px;
}

.draft-title {
  font-size: 17px;
  font-weight: 700;
  color: #2b2b2b;
  margin-bottom: 6px;
}

.draft-subtitle {
  font-size: 13px;
  color: #888;
  line-height: 1.5;
}

.draft-content {
  padding: 0 12px;
  max-height: 50vh;
  overflow-y: auto;
}

.draft-markdown {
  font-family: 'PingFang SC', 'Microsoft YaHei', monospace;
  font-size: 13px;
  line-height: 1.7;
  color: #333;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fafafa;
  padding: 16px;
  border-radius: 12px;
  margin: 0;
}

.draft-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 20px 20px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 24px;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  width: 100%;
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
  background: #f5f5f5;
  color: #666;
  border: 1px solid #ddd;
}

.btn-reject:hover:not(:disabled) {
  background: #eee;
  color: #333;
}

.btn-icon {
  font-size: 18px;
}

.btn-label {
  letter-spacing: 0.5px;
}
</style>
