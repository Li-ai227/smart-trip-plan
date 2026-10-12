<script setup lang="ts">
/** 页面：历史方案列表（桌面端） */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
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
  <div class="history-container">
    <div class="page-header">
      <h1 class="page-title">历史方案</h1>
      <p class="page-subtitle">查看和搜索您的旅行规划记录</p>
    </div>

    <div class="search-bar">
      <input
        v-model="searchInput"
        type="text"
        placeholder="搜索目的地..."
        class="search-input"
        @input="onSearch"
      />
    </div>

    <div v-if="store.loading" class="loading">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else-if="store.items.length === 0" class="empty">
      <p>{{ store.keyword ? '没有匹配的方案' : '暂无历史方案' }}</p>
      <button class="new-plan-btn" @click="router.push({ name: 'intent' })">创建新方案</button>
    </div>

    <div v-else class="card-grid">
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
          <div class="info-row">
            <span class="label">日期</span>
            <span>{{ formatDate(item.startDate, item.days) }}</span>
          </div>
          <div class="info-row">
            <span class="label">人数</span>
            <span>{{ item.headcount }} 人<template v-if="styleLabel(item.travelStyle)"> · {{ styleLabel(item.travelStyle) }}</template></span>
          </div>
          <div class="info-row">
            <span class="label">预算</span>
            <span>{{ item.budget }} 元</span>
          </div>
          <div v-if="item.totalCost != null" class="info-row cost-row">
            <span class="label">实际</span>
            <span class="cost-value">{{ item.totalCost }} 元</span>
          </div>
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
</template>

<style scoped>
.history-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 40px 20px;
}

.page-header {
  text-align: center;
  margin-bottom: 32px;
}

.page-title {
  font-size: 32px;
  font-weight: 800;
  color: #2b2b2b;
  margin-bottom: 8px;
}

.page-subtitle {
  font-size: 16px;
  color: #888;
}

.search-bar {
  max-width: 480px;
  margin: 0 auto 32px;
}

.search-input {
  width: 100%;
  padding: 12px 20px;
  border: 1px solid #e0e0e0;
  border-radius: 12px;
  font-size: 15px;
  background: #f9f9f9;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search-input:focus {
  border-color: #ff2442;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(255, 36, 66, 0.08);
}

.loading {
  text-align: center;
  padding: 80px 0;
  color: #888;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 3px solid #f0f0f0;
  border-top-color: #ff2442;
  border-radius: 50%;
  margin: 0 auto 16px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty {
  text-align: center;
  padding: 80px 0;
  color: #999;
  font-size: 15px;
}

.new-plan-btn {
  margin-top: 16px;
  padding: 10px 24px;
  background: #ff2442;
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.new-plan-btn:hover {
  background: #e01e3a;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.plan-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.plan-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.destination {
  font-size: 20px;
  font-weight: 700;
  color: #2b2b2b;
}

.badge {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 10px;
  white-space: nowrap;
  flex-shrink: 0;
}

.badge-success { background: #e8faf1; color: #16c784; }
.badge-fail { background: #ffeaee; color: #ff2442; }
.badge-progress { background: #fff3e0; color: #ff9800; }
.badge-review { background: #e8f0fe; color: #1a73e8; }

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: #555;
}

.label {
  color: #999;
  min-width: 40px;
}

.cost-row .cost-value {
  color: #ff2442;
  font-weight: 700;
}

.expired-hint {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
  font-size: 12px;
  color: #bbb;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-top: 32px;
}

.pagination button {
  padding: 8px 20px;
  border: 1px solid #ddd;
  border-radius: 10px;
  background: #fff;
  color: #333;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.pagination button:hover:not(:disabled) {
  background: #ff2442;
  color: #fff;
  border-color: #ff2442;
}

.pagination button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-num {
  font-size: 14px;
  color: #888;
}
</style>
