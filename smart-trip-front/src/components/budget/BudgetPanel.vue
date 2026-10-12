<script setup lang="ts">
/** 模块⑤ 预算面板 —— 渲染 Budget.breakdown 四类拆分（交通/住宿/餐饮/门票） */
import { computed } from 'vue'
import type { Budget } from '@/models/trip-plan'

const props = defineProps<{ budget: Budget }>()

const overrun = computed(() => props.budget.overrunRate)
const overText = computed(() => {
  const r = overrun.value
  if (r > 0) return `超 ${(r * 100).toFixed(1)}%`
  return `省 ¥${(props.budget.total - props.budget.used).toLocaleString()}`
})
const usedColor = computed(() => (overrun.value > 0 ? 'var(--warn)' : 'var(--ink)'))

const segs = computed(() => {
  const b = props.budget.breakdown
  const sum = b.transport + b.hotel + b.food + b.ticket || 1
  return [
    { label: '交通', value: b.transport, color: '#5b8def' },
    { label: '住宿', value: b.hotel, color: '#ff8a00' },
    { label: '餐饮', value: b.food, color: '#ff2442' },
    { label: '门票', value: b.ticket, color: '#16c784' },
  ].map((s) => ({ ...s, pct: (s.value / sum) * 100 }))
})
</script>

<template>
  <div class="card bud">
    <div class="muted">预算总览</div>
    <div class="tot">
      <span class="big" :style="{ color: usedColor }">¥{{ budget.used.toLocaleString() }}</span>
      <span class="of">/ ¥{{ budget.total.toLocaleString() }} · {{ overText }}</span>
    </div>
    <div class="bseg">
      <i v-for="s in segs" :key="s.label" :style="{ width: s.pct + '%', background: s.color }"></i>
    </div>
    <div class="blegend">
      <template v-for="(s, i) in segs" :key="s.label">
        <span class="k" :style="{ background: s.color }"></span>{{ s.label }} ¥{{
          s.value.toLocaleString()
        }}<template v-if="i % 2 === 0">&nbsp;&nbsp;</template><br v-if="i % 2 === 1" />
      </template>
    </div>
  </div>
</template>
