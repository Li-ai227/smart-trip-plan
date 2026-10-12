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

import { calculateDynamicTimeline } from '@/utils/timeline'

const activeDay = computed(() => props.days.find((d) => d.index === props.activeIndex) ?? props.days[0])

const entries = computed(() => calculateDynamicTimeline(activeDay.value))

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
