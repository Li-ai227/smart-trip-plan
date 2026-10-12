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
const emit = defineEmits<{
  (e: 'select', index: number): void
  (e: 'open-confirm'): void
  (e: 'edit-plan'): void
  (e: 'remove-attraction', payload: { dayIndex: number; attractionId: string }): void
  (e: 'remove-meal', payload: { dayIndex: number; mealType: string }): void
}>()

import { calculateDynamicTimeline } from '@/utils/timeline'

const activeDay = computed(() => props.days.find((d) => d.index === props.activeIndex) ?? props.days[0])

const entries = computed(() => calculateDynamicTimeline(activeDay.value))

</script>

<template>
  <div class="scroll">
    <div class="topbar" style="justify-content: space-between">
      <b style="font-size: 16px">{{ title ?? `${activeDay?.cityLabel ?? ''} 行程` }}</b>
      <div style="display: flex; align-items: center; gap: 8px">
        <!-- 草案态：显示确认方案 -->
        <button
          v-if="draft"
          class="pill red"
          style="margin: 0; cursor: pointer; border: none; font-weight: 700; background: #ff2442; color: #fff"
          @click="emit('open-confirm')"
        >
          📋 确认方案
        </button>
        <!-- 已完成态：显示修改行程 -->
        <button
          v-else
          class="pill"
          style="margin: 0; cursor: pointer; border: 1px solid #d9d9d9; background: #fff; color: #333; font-weight: 600"
          @click="emit('edit-plan')"
        >
          ✏️ 修改行程
        </button>
        <span v-if="draft" class="pill red" style="margin: 0">草案待确认</span>
        <span v-else class="pill" style="margin: 0; background: #e8faf1; color: #16c784">已完成</span>
      </div>
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
            <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 6px">
              <div class="nm">{{ e.name }}</div>
              <!-- 仅在草案/编辑模式下展示移除按钮 -->
              <button
                v-if="draft && e.kind === 'spot' && e.id"
                class="mobile-btn-remove spot"
                title="移除景点"
                @click.stop="emit('remove-attraction', { dayIndex: activeIndex, attractionId: e.id })"
              >
                ✕ 移除
              </button>
              <button
                v-else-if="draft && e.kind === 'meal' && e.id"
                class="mobile-btn-remove meal"
                title="移除此餐"
                @click.stop="emit('remove-meal', { dayIndex: activeIndex, mealType: e.id.replace('meal-', '') })"
              >
                ✕ 移除此餐
              </button>
            </div>
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

<style scoped>
.mobile-btn-remove {
  border: 1px solid #ffccd2;
  background: #fff5f6;
  color: #ff2442;
  font-size: 11px;
  font-weight: 600;
  border-radius: 12px;
  padding: 2px 8px;
  cursor: pointer;
  white-space: nowrap;
  line-height: 1.4;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.mobile-btn-remove.meal {
  border-color: #ffe0b2;
  background: #fff8f0;
  color: #ff8a00;
}

.mobile-btn-remove:active {
  transform: scale(0.95);
  opacity: 0.8;
}
</style>
