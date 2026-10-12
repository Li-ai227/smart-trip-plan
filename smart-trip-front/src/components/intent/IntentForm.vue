<script setup lang="ts">
/** 模块① 意图输入页 —— 字段 1:1 映射 POST /api/v1/trip-plan request body */
import { reactive, ref } from 'vue'
import type { TravelStyle, TripPlanRequest } from '@/models/trip-plan'
import TagSelectionSheet from '@/components/tag/TagSelectionSheet.vue'
import { fetchTags } from '@/services/tag'

const props = defineProps<{ modelValue?: Partial<TripPlanRequest> }>()
const emit = defineEmits<{ (e: 'submit', payload: TripPlanRequest): void }>()

const styles: { key: TravelStyle; label: string }[] = [
  { key: 'FAMILY', label: '亲子' },
  { key: 'COUPLE', label: '情侣' },
  { key: 'SOLO', label: '独自' },
  { key: 'TEAM', label: '团建' },
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
const showTagSheet = ref(false)
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

// 打开标签选择抽屉
const openTagSheet = () => {
  selectedTags.value = [...form.preferences]
  showTagSheet.value = true
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
  <div class="scroll">
    <div class="topbar"><div class="search">想去哪儿？云南 / 厦门 / 成都…</div></div>
    <div class="pad">
      <div class="h2">帮你规划一场<br />小红书同款旅行 ✈️</div>
      <div class="muted">填一次意图，5 个 AI 管家帮你搞定</div>
      <div class="card" style="margin-top: 14px">
        <div class="field">
          <label>目的地</label>
          <input class="input" v-model="form.destination" placeholder="如：云南" />
        </div>
        <div class="field row2">
          <div>
            <label>出发日期</label>
            <input class="input" v-model="form.startDate" placeholder="2026-07-01" />
          </div>
          <div>
            <label>天数</label>
            <input class="input" type="number" v-model.number="form.days" />
          </div>
        </div>
        <div class="field row2">
          <div>
            <label>人数</label>
            <input class="input" type="number" v-model.number="form.headcount" />
          </div>
          <div>
            <label>预算（元）</label>
            <input class="input" type="number" v-model.number="form.budget" />
          </div>
        </div>
        <div class="field">
          <label>旅行风格</label>
          <span
            v-for="s in styles"
            :key="s.key"
            class="pill"
            :class="{ on: form.travelStyle === s.key }"
            @click="form.travelStyle = s.key"
            >{{ s.label }}</span
          >
        </div>
        <div class="field">
          <label>偏好标签</label>
          <span v-for="p in form.preferences" :key="p" class="pill red" @click="removePref(p)"
            >{{ p }} ×</span
          >
          <span class="pill" @click="openTagSheet">+ 添加</span>
        </div>
        <div class="field">
          <label>📋 计划模式</label>
          <label class="switch-horiz">
            <input type="checkbox" v-model="form.planMode" />
            <span class="slider-text" :class="{ on: form.planMode }">{{
              form.planMode ? "先起草计划再执行" : "直接执行"
            }}</span>
          </label>
        </div>
      </div>
      <div v-if="formErrors.days" class="field-error" data-testid="days-error">{{ formErrors.days }}</div>
      <div v-if="formErrors.budget" class="field-error" data-testid="budget-error">{{ formErrors.budget }}</div>
      <div v-if="formErrors.headcount" class="field-error" data-testid="headcount-error">{{ formErrors.headcount }}</div>
      <button class="cta" @click="onSubmit">开始规划 →</button>
      <div class="caption">
        字段 <b>1:1 映射</b> request body：destination / startDate / days / headcount / budget /
        travelStyle / preferences。点击即触发主流程。
      </div>
    </div>

    <!-- 移动端标签选择抽屉 -->
    <TagSelectionSheet
      v-model="showTagSheet"
      :tags="availableTags"
      :selected-tags="selectedTags"
      @select="handleTagSelect"
      @cancel="handleTagCancel"
    />
  </div>
</template>
