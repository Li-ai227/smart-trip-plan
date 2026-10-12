<script setup lang="ts">
/**
 * 行程时间轴（桌面端）—— 横向布局，信息密度更高
 */
import { computed } from 'vue'
import type { Route, TripDay } from '@/models/trip-plan'
import { useTripPlanStore } from '@/stores/trip-plan'

const props = defineProps<{
  days: TripDay[]
  activeIndex: number
  draft?: boolean
  title?: string
}>()
const emit = defineEmits<{
  (e: 'select', index: number): void
  (e: 'open-confirm'): void
  (e: 'remove-attraction', payload: { dayIndex: number; attractionId: string }): void
}>()

const store = useTripPlanStore()

import { calculateDynamicTimeline } from '@/utils/timeline'

const activeDay = computed(() => props.days.find((d) => d.index === props.activeIndex) ?? props.days[0])

const entries = computed(() => calculateDynamicTimeline(activeDay.value))

</script>

<template>
  <div class="desktop-timeline-container">
    <!-- 顶部状态栏 -->
    <div class="timeline-header">
      <h2 class="timeline-title">{{ title ?? `${store.request.destination} ${store.request.days} 日行程` }}</h2>
      <div class="header-right">
        <button
          v-if="draft"
          class="btn-open-confirm"
          @click="emit('open-confirm')"
        >
          <span class="btn-icon">📋</span>
          <span>确认方案</span>
        </button>
        <div class="status-badge" :class="{ draft: draft }">
          {{ draft ? '草案待确认' : '已完成' }}
        </div>
      </div>
    </div>

    <!-- 日期选择器 -->
    <div class="day-selector">
      <button
        v-for="d in days"
        :key="d.index"
        class="day-btn"
        :class="{ on: d.index === activeIndex }"
        @click="emit('select', d.index)"
      >
        <span class="day-icon">📅</span>
        <span class="day-label">{{ d.cityLabel }}</span>
        <span class="day-cost">¥{{ (d.dailyCost ?? 0).toLocaleString() }}</span>
      </button>
    </div>

    <!-- 时间轴内容 -->
    <div class="timeline-content">
      <div v-if="entries.length" class="timeline-grid">
        <div v-for="(e, i) in entries" :key="i" class="timeline-item">
          <div class="item-time">{{ e.time }}</div>
          <div class="item-content">
            <div class="item-top-row">
              <div class="item-type" :class="e.kind">{{ e.kind === 'spot' ? '🏛️ 景点' : '🍽️ 美食' }}</div>
              <button
                v-if="e.kind === 'spot' && e.id"
                class="btn-remove-spot"
                title="从今日行程中移除此景点"
                @click.stop="emit('remove-attraction', { dayIndex: activeIndex, attractionId: e.id! })"
              >
                ✕ 移除
              </button>
            </div>
            <h3 class="item-name">{{ e.name }}</h3>

            <!-- 景点详情 -->
            <div v-if="e.kind === 'spot'" class="item-details">
              <span class="detail-tag">⭐ {{ e.rating }}分</span>
              <span class="detail-tag">⏱️ {{ e.durationH }}小时</span>
              <span class="detail-tag">🎫 ¥{{ e.ticket }}门票</span>
            </div>

            <!-- 餐饮详情 -->
            <div v-else class="item-details">
              <span class="detail-text">{{ e.mealMeta }}</span>
            </div>

            <!-- Fallback 提示 -->
            <span v-if="e.fallback" class="fallback-badge">⚠ 路况离线估算，仅供参考</span>

            <!-- 交通信息：前往下一站 -->
            <div v-if="e.transit" class="transit-info">
              <span class="transit-icon">🚗 乘车前往下站：</span>
              <span class="transit-highlight">{{ e.transit.durationMinutes }} 分钟</span>
              <span class="transit-dist">（{{ e.transit.distanceKm }} 公里）</span>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-state">
        <div class="empty-icon">📋</div>
        <p>该日行程编排中…</p>
      </div>
    </div>

    <!-- 底部说明 -->
    <div class="caption">
      按 <code>TripPlan→TripDay[]→Attraction/Meal/Route</code> 渲染：景点卡=评分/门票/时长，箭头=Route
      距离/耗时/方式。<b>fallback:true 的卡片明确标注降级</b>，不假装正常。
    </div>
  </div>
</template>

<style scoped>
.desktop-timeline-container {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* 标题区域 */
.timeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.timeline-title {
  font-size: 24px;
  font-weight: 700;
  color: #2b2b2b;
}

.status-badge {
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  background: #e8faf1;
  color: #16c784;
}

.status-badge.draft {
  background: #ffe9ed;
  color: #ff2442;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-open-confirm {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(135deg, #ff2442 0%, #ff5268 100%);
  color: #fff;
  border: none;
  border-radius: 20px;
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(255, 36, 66, 0.25);
  transition: all 0.2s ease;
}

.btn-open-confirm:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(255, 36, 66, 0.35);
  background: linear-gradient(135deg, #e01e3a 0%, #ff3b53 100%);
}

.btn-open-confirm:active {
  transform: translateY(0);
}

.btn-icon {
  font-size: 15px;
}

/* 日期选择器 */
.day-selector {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 8px;
}

.day-btn {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 24px;
  background: #f5f5f5;
  border: 2px solid transparent;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.day-btn:hover {
  background: #ffe9ed;
}

.day-btn.on {
  background: #ff2442;
  color: #fff;
  box-shadow: 0 4px 16px rgba(255, 36, 66, 0.3);
}

.day-icon {
  font-size: 24px;
}

.day-label {
  font-size: 15px;
  font-weight: 600;
}

.day-cost {
  font-size: 13px;
  opacity: 0.8;
}

/* 时间轴内容 */
.timeline-content {
  min-height: 400px;
}

.timeline-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.timeline-item {
  background: #fff;
  border: 2px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  position: relative;
  transition: all 0.3s ease;
}

.timeline-item:hover {
  border-color: #ff2442;
  box-shadow: 0 8px 24px rgba(255, 36, 66, 0.1);
}

.item-time {
  font-size: 28px;
  font-weight: 800;
  color: #ff2442;
  margin-bottom: 16px;
}

.item-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.item-type {
  display: inline-block;
  padding: 6px 12px;
  background: #f5f5f5;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #666;
}

.btn-remove-spot {
  background: #fff0f2;
  color: #ff2442;
  border: 1px solid #ffd1d8;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-remove-spot:hover {
  background: #ff2442;
  color: #fff;
  border-color: #ff2442;
  transform: scale(1.03);
}

.item-type.spot {
  background: #e8f5e9;
  color: #16c784;
}

.item-type.meal {
  background: #fff3e0;
  color: #ff8a00;
}

.item-name {
  font-size: 18px;
  font-weight: 700;
  color: #2b2b2b;
  margin-bottom: 12px;
}

.item-details {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.detail-tag {
  font-size: 12px;
  color: #888;
  background: #f9f9f9;
  padding: 4px 10px;
  border-radius: 6px;
}

.detail-text {
  font-size: 13px;
  color: #888;
}

.fallback-badge {
  display: inline-block;
  padding: 6px 12px;
  background: #fff6e9;
  color: #ff8a00;
  border-radius: 8px;
  font-size: 12px;
  margin-top: 8px;
}

.transit-info {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e0e0e0;
  font-size: 13px;
  color: #888;
  display: flex;
  align-items: center;
  gap: 6px;
}

.transit-icon {
  font-size: 14px;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
}

.empty-icon {
  font-size: 64px;
  opacity: 0.3;
  margin-bottom: 16px;
}

.empty-state p {
  font-size: 16px;
  color: #aaa;
}

.caption {
  font-size: 13px;
  color: #999;
  line-height: 1.6;
  text-align: center;
}

caption b {
  color: #ff2442;
}

code {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: ui-monospace, Menlo, monospace;
  font-size: 12px;
  color: #ff2442;
}
</style>
