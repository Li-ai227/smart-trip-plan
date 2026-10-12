<script setup lang="ts">
/** 模块⑥ 确认 · 导出 · 反馈 —— PDF 导出 + 👍👎（POST /{planId}/feedback） */
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
  <div class="scroll">
    <div class="pad center">
      <div class="done-ic">✓</div>
      <div class="h2">行程已确认！</div>
      <div class="muted">{{ title ?? '' }} · 共 ¥{{ plan.totalCost.toLocaleString() }}（预算内）</div>

      <div class="card" style="text-align: left; margin-top: 16px" :style="planId ? 'cursor:pointer' : ''" @click="goToPlan">
        <div style="font-size: 13px; line-height: 2">
          📅 {{ plan.days.length }} 天 {{ spotCount }} 个景点 · {{ mealCount }} 顿特色餐<br />
          💰 总费用 ¥{{ plan.totalCost.toLocaleString() }}
          <span style="color: var(--ok)" v-if="saved > 0">省 ¥{{ saved.toLocaleString() }}</span><br />
          ⭐ 平均景点评分 {{ avgRating }} / 5.0
        </div>
      </div>

      <button class="cta" :disabled="exporting" @click="emit('export')">
        <span v-if="exporting" class="spinner-small"></span>
        <span v-else>📄</span>
        {{ exporting ? '导出中…' : '导出 PDF 行程单' }}
      </button>
      <div v-if="exportError" class="export-error">{{ exportError }}</div>
      <div class="caption" style="text-align: left">PDF = PdfGenerationSkill 产物。</div>

      <div class="card" style="margin-top: 8px">
        <div class="muted" style="text-align: left">这份行程满意吗？</div>
        <div class="fbrow">
          <div class="fbbtn" :class="{ up: picked === 'UP' }" @click="choose('UP')">
            👍
            <div style="font-size: 11px; margin-top: 4px" :style="{ color: picked === 'UP' ? 'var(--red)' : '#999' }">
              满意
            </div>
          </div>
          <div class="fbbtn" :class="{ up: picked === 'DOWN' }" @click="choose('DOWN')">
            👎
            <div style="font-size: 11px; color: #999; margin-top: 4px">一般</div>
          </div>
        </div>
        <div class="caption" style="text-align: left">
          👍/👎 + 手动修改项 → 回流 <b>ReMe 长期记忆</b>，影响下次规划的个性化。
        </div>
      </div>
    </div>
  </div>
</template>
