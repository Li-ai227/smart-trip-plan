<script setup lang="ts">
/** 模块④ 行程结果页 —— 按天时间轴，渲染 Attraction / Meal / Route，标注 fallback 降级 */
import { computed } from 'vue'
import type { Route, TripDay } from '@/models/trip-plan'

const props = defineProps<{
  days: TripDay[]
  activeIndex: number
  draft?: boolean
  title?: string
}>()
const emit = defineEmits<{ (e: 'select', index: number): void }>()

const ATTRACTION_TIMES = ['09:00', '14:00', '16:00', '17:30']
const MEAL_TIMES: Record<string, string> = { 早: '08:00', 午: '12:00', 晚: '19:00' }

const activeDay = computed(() => props.days.find((d) => d.index === props.activeIndex) ?? props.days[0])

// 时间轴节点：景点（spot）与餐饮（meal）合并后按时间排序
interface Entry {
  time: string
  name: string
  kind: 'spot' | 'meal'
  rating?: number
  durationH?: number
  ticket?: number
  mealMeta?: string
  fallback: boolean
  transit?: Route
}

const entries = computed<Entry[]>(() => {
  const day = activeDay.value
  if (!day) return []
  const routes = day.routes
  const outgoing = (name: string) => routes.find((r) => r.fromName === name)
  const incomingFallback = (name: string) => routes.some((r) => r.toName === name && r.fallback)

  const spots: Entry[] = day.attractions.map((a, i) => ({
    time: ATTRACTION_TIMES[i] ?? '—',
    name: a.name,
    kind: 'spot',
    rating: a.rating,
    durationH: Math.round((a.durationMinutes / 60) * 10) / 10,
    ticket: a.ticket,
    fallback: incomingFallback(a.name),
    transit: outgoing(a.name),
  }))

  const meals: Entry[] = day.meals.map((m) => ({
    time: MEAL_TIMES[m.mealType] ?? '—',
    name: m.name,
    kind: 'meal',
    mealMeta: `🍴 ${m.recommend ?? ''} · ¥${m.perCapita}/人${m.distanceKm ? ` · 距景点 ${m.distanceKm}km` : ''}`,
    fallback: false,
    transit: outgoing(m.name),
  }))

  return [...spots, ...meals].sort((a, b) => a.time.localeCompare(b.time))
})
</script>

<template>
  <div class="scroll">
    <div class="topbar" style="justify-content: space-between">
      <b style="font-size: 16px">{{ title ?? `${activeDay?.cityLabel ?? ''} 行程` }}</b>
      <span v-if="draft" class="pill red" style="margin: 0">草案待确认</span>
      <span v-else class="pill" style="margin: 0; background: #e8faf1; color: #16c784">已确认</span>
    </div>

    <div class="daytab">
      <div
        v-for="d in days"
        :key="d.index"
        class="d"
        :class="{ on: d.index === activeIndex }"
        @click="emit('select', d.index)"
      >
        {{ d.cityLabel }}
      </div>
    </div>

    <div class="pad" style="padding-top: 6px">
      <div class="tl" v-if="entries.length">
        <div class="stop" v-for="(e, i) in entries" :key="i">
          <div class="time">{{ e.time }}</div>
          <div class="spot">
            <div class="nm">{{ e.name }}</div>
            <div class="meta" v-if="e.kind === 'spot'">
              <span class="star">⭐{{ e.rating }}</span> · 游玩 {{ e.durationH }}h · 门票 ¥{{ e.ticket }}
            </div>
            <div class="meta" v-else>{{ e.mealMeta }}</div>
            <span v-if="e.fallback" class="fallback">⚠ 路况离线估算，仅供参考</span>
          </div>
          <div v-if="e.transit" class="transit">
            ↓ 🚗 {{ e.transit.durationMinutes }} min · {{ e.transit.distanceKm }} km
          </div>
        </div>
      </div>
      <div v-else class="muted" style="padding: 20px 4px">该日行程编排中…</div>

      <div class="card" style="margin-top: 6px">
        当日小计
        <b style="float: right; color: var(--red)">¥{{ (activeDay?.dailyCost ?? 0).toLocaleString() }}</b>
      </div>
      <div class="caption">
        按 <code>TripPlan→TripDay[]→Attraction/Meal/Route</code> 渲染：景点卡=评分/门票/时长，箭头=Route
        距离/耗时/方式。<b>fallback:true 的卡片明确标注降级</b>，不假装正常。
      </div>
    </div>
  </div>
</template>
