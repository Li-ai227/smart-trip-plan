<script setup lang="ts">
/**
 * 反馈·导出（桌面端）—— 信息面板 + 操作区
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FeedbackRating, TripPlan } from '@/models/trip-plan'

const props = defineProps<{
  plan: TripPlan
  title?: string
  planId?: string
  /** 导出进行中，按钮显示加载态（T-6 AC-7）。 */
  exporting?: boolean
  /** 导出失败的错误消息，非空时显示提示条（T-6 AC-7）。 */
  exportError?: string
}>()

const router = useRouter()
const goToPlan = () => {
  if (props.planId) router.push({ name: 'plan', params: { planId: props.planId } })
}
const emit = defineEmits<{
  (e: 'feedback', rating: FeedbackRating): void
  (e: 'export'): void
}>()

const picked = ref<FeedbackRating | ''>('')

const spotCount = computed(() => props.plan.days.reduce((s, d) => s + d.attractions.length, 0) || 12)
const mealCount = computed(() => props.plan.days.reduce((s, d) => s + d.meals.length, 0) || 8)
const avgRating = computed(() => {
  const all = props.plan.days.flatMap((d) => d.attractions.map((a) => a.rating))
  if (!all.length) return 4.6
  return Math.round((all.reduce((a, b) => a + b, 0) / all.length) * 10) / 10
})
const saved = computed(() => props.plan.budget.total - props.plan.budget.used)

const choose = (r: FeedbackRating) => {
  picked.value = r
  emit('feedback', r)
}
</script>

<template>
  <div class="desktop-feedback-container">
    <!-- 行程概览卡片 -->
    <div class="summary-card card">
      <div class="summary-header">
        <h2 class="summary-title">{{ title ?? '' }}</h2>
        <span class="budget-badge">总预算 ¥{{ plan.totalCost.toLocaleString() }}</span>
      </div>

      <div class="summary-stats">
        <div class="stat-item" :class="{ clickable: !!planId }" @click="goToPlan">
          <span class="stat-icon">📅</span>
          <span class="stat-value">{{ plan.days.length }}</span>
          <span class="stat-label">天行程</span>
        </div>
        <div class="stat-item" :class="{ clickable: !!planId }" @click="goToPlan">
          <span class="stat-icon">🏛️</span>
          <span class="stat-value">{{ spotCount }}</span>
          <span class="stat-label">个景点</span>
        </div>
        <div class="stat-item" :class="{ clickable: !!planId }" @click="goToPlan">
          <span class="stat-icon">🍽️</span>
          <span class="stat-value">{{ mealCount }}</span>
          <span class="stat-label">顿美食</span>
        </div>
        <div class="stat-item" :class="{ clickable: !!planId }" @click="goToPlan">
          <span class="stat-icon">⭐</span>
          <span class="stat-value">{{ avgRating }}</span>
          <span class="stat-label">平均评分</span>
        </div>
        <div v-if="saved > 0" class="stat-item success" :class="{ clickable: !!planId }" @click="goToPlan">
          <span class="stat-icon">💰</span>
          <span class="stat-value">¥{{ saved.toLocaleString() }}</span>
          <span class="stat-label">节省</span>
        </div>
      </div>
    </div>

    <!-- 导出区域 -->
    <div class="export-section">
      <h3 class="section-title">📄 导出行程单</h3>
      <p class="section-desc">生成 PDF 格式的完整行程，包含景点详情、交通路线、餐厅推荐</p>
      <button class="export-btn" :disabled="exporting" @click="emit('export')">
        <span v-if="exporting" class="spinner-small"></span>
        <span v-else class="btn-icon">⬇️</span>
        <span class="btn-text">{{ exporting ? '导出中…' : '下载 PDF 行程单' }}</span>
      </button>
      <div v-if="exportError" class="export-caption export-error">{{ exportError }}</div>
      <div v-else class="export-caption">PDF 格式完整行程，支持打印和分享</div>
    </div>

    <!-- 反馈区域 -->
    <div class="feedback-section">
      <h3 class="section-title">💬 反馈评价</h3>
      <p class="section-desc">您的反馈将帮助 AI 更好地理解您的偏好</p>

      <div class="rating-buttons">
        <button class="rating-btn" :class="{ selected: picked === 'UP' }" @click="choose('UP')">
          <span class="rating-icon">👍</span>
          <span class="rating-label">满意</span>
        </button>
        <button class="rating-btn" :class="{ selected: picked === 'DOWN' }" @click="choose('DOWN')">
          <span class="rating-icon">👎</span>
          <span class="rating-label">一般</span>
        </button>
      </div>

      <div class="feedback-note">
        👍/👎 + 手动修改项 → 回流 <b>ReMe 长期记忆</b>，影响下次规划的个性化。
      </div>
    </div>
  </div>
</template>

<style scoped>
.desktop-feedback-container {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* 概览卡片 */
.summary-card {
  background: #fff;
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  border: 1px solid #e8e8e8;
}

.summary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 2px solid #f0f0f0;
}

.summary-title {
  font-size: 24px;
  font-weight: 700;
  color: #2b2b2b;
  margin: 0;
}

.budget-badge {
  font-size: 18px;
  font-weight: 700;
  color: #ff2442;
}

.summary-stats {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 100px;
  padding: 16px;
  background: #f9f9f9;
  border-radius: 12px;
  transition: all 0.2s ease;
}

.stat-item.clickable {
  cursor: pointer;
}

.stat-item:hover {
  background: #ffe9ed;
  transform: translateY(-2px);
}

.stat-item.success {
  background: #e8faf1;
}

.stat-icon {
  font-size: 24px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  color: #2b2b2b;
  line-height: 1;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: #888;
}

/* 导出区域 */
.export-section {
  background: #fff;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border: 1px solid #e8e8e8;
}

.section-title {
  font-size: 20px;
  font-weight: 700;
  color: #2b2b2b;
  margin-bottom: 8px;
}

.section-desc {
  font-size: 14px;
  color: #888;
  margin-bottom: 20px;
}

.export-btn {
  width: 100%;
  height: 56px;
  background: linear-gradient(135deg, #ff2442 0%, #ff4d6d 100%);
  border: none;
  border-radius: 16px;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.3);
  transition: all 0.3s ease;
}

.export-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(255, 36, 66, 0.4);
}

.btn-icon {
  font-size: 20px;
}

.btn-text {
  font-size: 16px;
}

.export-caption {
  font-size: 12px;
  color: #aaa;
  text-align: center;
  margin-top: 12px;
}

/* 反馈区域 */
.feedback-section {
  background: #fff;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border: 1px solid #e8e8e8;
}

.rating-buttons {
  display: flex;
  gap: 24px;
  margin: 24px 0;
}

.rating-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px;
  background: #f5f5f5;
  border: 2px solid transparent;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.rating-btn:hover {
  background: #ffe9ed;
  border-color: #ff2442;
}

.rating-btn.selected {
  background: #ffe9ed;
  border-color: #ff2442;
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.2);
}

.rating-icon {
  font-size: 48px;
}

.rating-label {
  font-size: 15px;
  font-weight: 600;
  color: #666;
}

.rating-btn.selected .rating-label {
  color: #ff2442;
}

.feedback-note {
  font-size: 13px;
  color: #999;
  line-height: 1.6;
  text-align: center;
}

.feedback-note b {
  color: #ff2442;
}
</style>
