<script setup lang="ts">
/** 页面：历史方案列表（移动端） */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PhoneFrame from '@/components/PhoneFrame.vue'
import { useHistoryStore } from '@/stores/history'
import type { PlanSummary, TravelStyle } from '@/models/trip-plan'

const TRAVEL_STYLE_LABEL: Record<TravelStyle, string> = {
  FAMILY: '亲子',
  COUPLE: '情侣',
  SOLO: '独自',
  TEAM: '团建',
}

const STATUS_LABEL: Record<string, { text: string; cls: string }> = {
  confirmed: { text: '已完成', cls: 'badge-success' },
  failed: { text: '已失败', cls: 'badge-fail' },
  analyzing: { text: '分析中', cls: 'badge-progress' },
  planning: { text: '规划中', cls: 'badge-progress' },
  assembling: { text: '编排中', cls: 'badge-progress' },
  budgeting: { text: '核算中', cls: 'badge-progress' },
  review: { text: '待确认', cls: 'badge-review' },
}

const router = useRouter()
const store = useHistoryStore()
const searchInput = ref('')
let debounceTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => store.fetchHistory())

function onSearch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    store.fetchHistory(1, searchInput.value)
  }, 300)
}

function onCardClick(item: PlanSummary) {
  if (item.status === 'review' || item.status === 'plan_review') {
    router.push({ name: 'plan', params: { planId: item.planId } })
  } else if (['analyzing', 'planning', 'assembling', 'budgeting', 'processing'].includes(item.status)) {
    router.push({ name: 'planning', params: { planId: item.planId } })
  } else {
    router.push({ name: 'result', params: { planId: item.planId } })
  }
}

function formatDate(dateStr: string, days: number) {
  return `${dateStr} 起 · ${days} 天`
}

function styleLabel(style: TravelStyle | null) {
  return style ? TRAVEL_STYLE_LABEL[style] || style : ''
}

function statusInfo(status: string) {
  return STATUS_LABEL[status] || { text: status, cls: 'badge-progress' }
}

function isExpired(createdAt: number) {
  return Date.now() - createdAt > 24 * 60 * 60 * 1000
}

const totalPages = computed(() => Math.ceil(store.total / store.pageSize))

function goPage(p: number) {
  if (p >= 1 && p <= totalPages.value) {
    store.fetchHistory(p, searchInput.value)
  }
}
</script>

<template>
  <div class="stage">
    <PhoneFrame>
      <div class="history-page">
        <h2 class="page-title">历史方案</h2>

        <div class="search-bar">
          <input
            v-model="searchInput"
            type="text"
            placeholder="搜索目的地..."
            class="search-input"
            @input="onSearch"
          />
        </div>

        <div v-if="store.loading" class="loading">加载中...</div>

        <div v-else-if="store.items.length === 0" class="empty">
          <p>{{ store.keyword ? '没有匹配的方案' : '暂无历史方案' }}</p>
        </div>

        <div v-else class="card-list">
          <div
            v-for="item in store.items"
            :key="item.planId"
            class="plan-card"
            @click="onCardClick(item)"
          >
            <div class="card-header">
              <span class="destination">{{ item.destination }}</span>
              <span :class="['badge', statusInfo(item.status).cls]">
                {{ statusInfo(item.status).text }}
              </span>
            </div>
            <div class="card-body">
              <p class="date-info">{{ formatDate(item.startDate, item.days) }}</p>
              <p class="meta">
                <span v-if="styleLabel(item.travelStyle)">{{ styleLabel(item.travelStyle) }}</span>
                <span>{{ item.headcount }} 人</span>
                <span>预算 {{ item.budget }} 元</span>
              </p>
              <p v-if="item.totalCost != null" class="cost">
                实际花费 <b>{{ item.totalCost }}</b> 元
              </p>
            </div>
            <div v-if="isExpired(item.createdAt)" class="expired-hint">详情可能已过期</div>
          </div>
        </div>

        <div v-if="totalPages > 1" class="pagination">
          <button :disabled="store.page <= 1" @click="goPage(store.page - 1)">上一页</button>
          <span class="page-num">{{ store.page }} / {{ totalPages }}</span>
          <button :disabled="store.page >= totalPages" @click="goPage(store.page + 1)">下一页</button>
        </div>
      </div>
    </PhoneFrame>
  </div>
</template>

<style scoped>
.history-page {
  padding: 20px 16px;
  min-height: 100%;
}

.page-title {
  font-size: 20px;
  font-weight: 800;
  color: #2b2b2b;
  margin-bottom: 16px;
}

.search-bar {
  margin-bottom: 16px;
}

.search-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  font-size: 14px;
  background: #f9f9f9;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s;
}

.search-input:focus {
  border-color: #ff2442;
  background: #fff;
}

.loading,
.empty {
  text-align: center;
  padding: 40px 0;
  color: #999;
  font-size: 14px;
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.plan-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.plan-card:active {
  transform: scale(0.98);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.destination {
  font-size: 16px;
  font-weight: 700;
  color: #2b2b2b;
}

.badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 10px;
  white-space: nowrap;
  flex-shrink: 0;
}

.badge-success {
  background: #e8faf1;
  color: #16c784;
}

.badge-fail {
  background: #ffeaee;
  color: #ff2442;
}

.badge-progress {
  background: #fff3e0;
  color: #ff9800;
}

.badge-review {
  background: #e8f0fe;
  color: #1a73e8;
}

.card-body {
  font-size: 13px;
  color: #666;
  line-height: 1.6;
}

.date-info {
  margin-bottom: 2px;
}

.meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.cost {
  margin-top: 4px;
  color: #333;
}

.cost b {
  color: #ff2442;
  font-weight: 700;
}

.expired-hint {
  margin-top: 6px;
  font-size: 11px;
  color: #bbb;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
  padding-bottom: 20px;
}

.pagination button {
  padding: 6px 14px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background: #fff;
  color: #333;
  font-size: 13px;
  cursor: pointer;
}

.pagination button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-num {
  font-size: 13px;
  color: #888;
}
</style>
