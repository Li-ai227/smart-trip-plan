<script setup lang="ts">
/**
 * 意图表单 - 桌面端版本
 * @description 宽屏布局优化，卡片式设计，字段自适应宽度
 */
import { reactive, ref } from 'vue'
import type { TravelStyle, TripPlanRequest } from '@/models/trip-plan'
import TagSelectionModal from '@/components/tag/TagSelectionModal.vue'
import { fetchTags } from '@/services/tag'

const props = defineProps<{ modelValue?: Partial<TripPlanRequest> }>()
const emit = defineEmits<{ (e: 'submit', payload: TripPlanRequest): void }>()

const styles: { key: TravelStyle; label: string }[] = [
  { key: 'FAMILY', label: '👨‍👩‍👧‍👦 亲子游' },
  { key: 'COUPLE', label: '💕 情侣浪漫' },
  { key: 'SOLO', label: '🚶 独自旅行' },
  { key: 'TEAM', label: '👥 团队建设' },
]

const form = reactive<TripPlanRequest>({
  destination: props.modelValue?.destination ?? '云南',
  startDate: props.modelValue?.startDate ?? '2026-07-01',
  days: props.modelValue?.days ?? 5,
  budget: props.modelValue?.budget ?? 8000,
  headcount: props.modelValue?.headcount ?? 2,
  travelStyle: props.modelValue?.travelStyle ?? 'COUPLE',
  preferences: props.modelValue?.preferences ?? ['古镇', '美食', '小众'],
})

const formErrors = ref<Record<string, string>>({})

// 标签相关状态
const availableTags = ref<string[]>([])
const showTagModal = ref(false)
const selectedTags = ref<string[]>([])

// 加载标签列表
async function loadTags() {
  try {
    availableTags.value = await fetchTags()
  } catch (error) {
    console.error('Failed to load tags:', error)
  }
}

// 初始化时加载标签
loadTags()

// 打开标签选择弹窗
const openTagModal = () => {
  selectedTags.value = [...form.preferences]
  showTagModal.value = true
}

// 确认标签选择
const handleTagSelect = (tags: string[]) => {
  form.preferences = tags
}

// 取消标签选择
const handleTagCancel = () => {
  // 不执行任何操作
}

const validate = (): boolean => {
  const errs: Record<string, string> = {}
  if (!form.destination.trim()) errs.destination = '目的地不能为空'
  if (form.days <= 0 || form.days > 30) errs.days = '天数必须在 1~30 之间'
  if (form.budget <= 0) errs.budget = '预算必须大于 0'
  if (form.headcount < 1) errs.headcount = '人数至少 1 人'
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

const onSubmit = () => {
  if (!validate()) return
  emit('submit', { ...form })
}

const removePref = (p: string) => {
  form.preferences = form.preferences.filter((x) => x !== p)
}
</script>

<template>
  <div class="desktop-form-container">
    <div class="page-header">
      <h1 class="title">帮你规划一场小红书同款旅行 ✈️</h1>
      <p class="subtitle">填一次意图，5 个 AI 管家帮你搞定</p>
    </div>

    <div class="form-card card">
      <div class="form-grid">
        <!-- 目的地 -->
        <div class="field-group full-width">
          <label class="field-label">
            <span class="icon">📍</span> 目的地
          </label>
          <input
            class="input-field"
            v-model="form.destination"
            placeholder="如：云南、厦门、成都…"
            autocomplete="off"
          />
          <div v-if="formErrors.destination" class="error-text">{{ formErrors.destination }}</div>
        </div>

        <!-- 出发日期 & 天数 -->
        <div class="field-group">
          <label class="field-label">
            <span class="icon">📅</span> 出发日期
          </label>
          <input
            class="input-field"
            v-model="form.startDate"
            type="date"
            autocomplete="off"
          />
        </div>

        <div class="field-group">
          <label class="field-label">
            <span class="icon">🗓️</span> 旅行天数
          </label>
          <input
            class="input-field"
            v-model.number="form.days"
            type="number"
            min="1"
            max="30"
            placeholder="1-30 天"
          />
          <div v-if="formErrors.days" class="error-text">{{ formErrors.days }}</div>
        </div>

        <!-- 人数 & 预算 -->
        <div class="field-group">
          <label class="field-label">
            <span class="icon">👥</span> 出行人数
          </label>
          <input
            class="input-field"
            v-model.number="form.headcount"
            type="number"
            min="1"
            placeholder="至少 1 人"
          />
          <div v-if="formErrors.headcount" class="error-text">{{ formErrors.headcount }}</div>
        </div>

        <div class="field-group">
          <label class="field-label">
            <span class="icon">💰</span> 总预算（元）
          </label>
          <input
            class="input-field"
            v-model.number="form.budget"
            type="number"
            min="1"
            placeholder="例如：8000"
          />
          <div v-if="formErrors.budget" class="error-text">{{ formErrors.budget }}</div>
        </div>

        <!-- 旅行风格 -->
        <div class="field-group full-width">
          <label class="field-label">
            <span class="icon">🎭</span> 旅行风格
          </label>
          <div class="style-options">
            <button
              v-for="s in styles"
              :key="s.key"
              class="style-btn"
              :class="{ on: form.travelStyle === s.key }"
              @click="form.travelStyle = s.key"
            >
              {{ s.label }}
            </button>
          </div>
        </div>

        <!-- 偏好标签 -->
        <div class="field-group full-width">
          <label class="field-label">
            <span class="icon">🏷️</span> 偏好标签
          </label>
          <div class="tags-container">
            <span
              v-for="p in form.preferences"
              :key="p"
              class="tag-item"
              @click="removePref(p)"
            >
              {{ p }} ×
            </span>
            <button class="tag-add" @click="openTagModal">+ 添加偏好</button>
          </div>
          <div class="tag-hint">推荐：古镇、美食、小众、摄影、美食探店、海滩度假</div>
        </div>

        <!-- 计划模式 -->
        <div class="field-group full-width">
          <label class="field-label">
            <span class="icon">📋</span> 计划模式
          </label>
          <label class="plan-mode-toggle">
            <input type="checkbox" v-model="form.planMode" />
            <span class="toggle-text" :class="{ on: form.planMode }">{{
              form.planMode ? '先起草计划再执行' : '直接执行'
            }}</span>
          </label>
        </div>
      </div>

      <!-- 提交按钮 -->
      <button class="submit-btn" @click="onSubmit">
        <span class="btn-text">开始规划 →</span>
        <span class="btn-icon">✈️</span>
      </button>

      <!-- 说明文字 -->
      <div class="form-caption">
        字段 <b>1:1 映射</b> API request body：destination / startDate / days / headcount / budget /
        travelStyle / preferences。点击即触发主流程。
      </div>
    </div>

    <!-- 桌面端标签选择弹窗 -->
    <TagSelectionModal
      v-model="showTagModal"
      :tags="availableTags"
      :selected-tags="selectedTags"
      @select="handleTagSelect"
      @cancel="handleTagCancel"
    />
  </div>
</template>

<style scoped>
.desktop-form-container {
  max-width: 1200px;
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
  line-height: 1.4;
  margin-bottom: 12px;
}

.subtitle {
  font-size: 16px;
  color: #888;
  line-height: 1.6;
}

.form-card {
  background: #fff;
  border-radius: 20px;
  padding: 48px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  border: 1px solid #e8e8e8;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 32px;
  margin-bottom: 40px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-group.full-width {
  grid-column: span 2;
}

.field-label {
  font-size: 14px;
  font-weight: 600;
  color: #555;
  display: flex;
  align-items: center;
  gap: 6px;
}

.field-label .icon {
  font-size: 16px;
}

.input-field {
  background: #f9f9f9;
  border: 2px solid transparent;
  border-radius: 12px;
  height: 48px;
  padding: 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: #333;
  transition: all 0.2s ease;
}

.input-field:focus {
  outline: none;
  border-color: #ff2442;
  background: #fff;
  box-shadow: 0 0 0 4px rgba(255, 36, 66, 0.1);
}

.input-field::placeholder {
  color: #ccc;
  font-weight: 400;
}

/* 旅行风格选项 */
.style-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.style-btn {
  padding: 10px 20px;
  border: 2px solid #e8e8e8;
  background: #fff;
  border-radius: 24px;
  font-size: 14px;
  font-weight: 600;
  color: #666;
  cursor: pointer;
  transition: all 0.2s ease;
}

.style-btn:hover {
  border-color: #ff2442;
  color: #ff2442;
}

.style-btn.on {
  background: #ff2442;
  color: #fff;
  border-color: #ff2442;
  box-shadow: 0 4px 12px rgba(255, 36, 66, 0.3);
}

/* 偏好标签 */
.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  min-height: 48px;
}

.tag-item {
  padding: 8px 14px;
  background: #ffe9ed;
  color: #ff2442;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tag-item:hover {
  background: #ff2442;
  color: #fff;
}

.tag-add {
  padding: 8px 14px;
  background: #f5f5f5;
  color: #999;
  border: 2px dashed #ddd;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tag-add:hover {
  border-color: #ff2442;
  color: #ff2442;
  background: #ffe9ed;
}

.tag-hint {
  font-size: 12px;
  color: #aaa;
  margin-top: 6px;
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  height: 56px;
  background: linear-gradient(135deg, #ff2442 0%, #ff4d6d 100%);
  border: none;
  border-radius: 28px;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  box-shadow: 0 8px 24px rgba(255, 36, 66, 0.35);
  transition: all 0.3s ease;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(255, 36, 66, 0.45);
}

.submit-btn:active {
  transform: translateY(0);
}

.btn-text {
  font-size: 16px;
}

.btn-icon {
  font-size: 20px;
}

.form-caption {
  margin-top: 24px;
  font-size: 13px;
  color: #999;
  line-height: 1.6;
  text-align: center;
}

.form-caption b {
  color: #ff2442;
  font-weight: 700;
}

.error-text {
  color: #ff2442;
  font-size: 13px;
  margin-top: 4px;
}

/* 计划模式开关 */
.plan-mode-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.toggle-text {
  padding: 8px 14px;
  background: #f5f5f5;
  color: #999;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.toggle-text.on {
  background: #ffe9ed;
  color: #ff2442;
}
</style>
