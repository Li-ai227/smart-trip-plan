<script setup lang="ts">
/**
 * 规划进度（桌面端）—— 5 Agent 协作可视化，宽屏布局
 */
import { computed } from 'vue'
import type { AgentProgressItem, PlanStatus } from '@/models/trip-plan'

const props = defineProps<{
  status: PlanStatus
  agents: AgentProgressItem[]
  budgetUsed?: number
  budgetTotal?: number
  elapsed?: string
  title?: string
}>()
const emit = defineEmits<{ (e: 'cancel'): void }>()

const percent = computed(() => {
  if (!props.budgetTotal) return 0
  return Math.min(100, Math.round(((props.budgetUsed ?? 0) / props.budgetTotal) * 100))
})

const badgeClass = (s: AgentProgressItem['state']) =>
  s === 'done' ? 'b-done' : s === 'running' ? 'b-run' : 'b-wait'
const badgeIcon = (a: AgentProgressItem) => (a.state === 'done' ? '✓' : a.emoji)
const stText = (s: AgentProgressItem['state']) =>
  s === 'done' ? '✓ 完成' : s === 'running' ? '进行中' : '等待'

const totalBudget = props.budgetTotal ?? 8000
</script>

<template>
  <div class="desktop-progress-container">
    <!-- 顶部状态栏 -->
    <div class="status-bar">
      <div class="status-left">
        <span class="status-icon">⏳</span>
        <span class="status-text">AI 智能体团队协作中</span>
      </div>
      <div class="status-right">
        <span class="elapsed-time">{{ elapsed ?? '00:41' }}</span>
        <button class="cancel-btn" @click="emit('cancel')">取消规划</button>
      </div>
    </div>

    <!-- 标题区域 -->
    <div class="header-section">
      <h2 class="plan-title">{{ title ?? '' }}</h2>
      <p class="plan-subtitle">正在调用 5 个专业 AI 管家协同工作</p>
    </div>

    <!-- Agent 进度列表 -->
    <div class="agents-grid">
      <div v-for="agent in agents" :key="agent.key" class="agent-card">
        <div class="agent-header">
          <div class="agent-badge" :class="badgeClass(agent.state)">
            {{ badgeIcon(agent) }}
          </div>
          <div class="agent-info">
            <h3 class="agent-name">{{ agent.name }}</h3>
            <p class="agent-desc">{{ agent.desc }}</p>
          </div>
          <div class="agent-status" :class="agent.state">
            {{ stText(agent.state) }}
          </div>
        </div>
      </div>
    </div>

    <!-- 预算进度条 -->
    <div class="budget-section card">
      <div class="budget-header">
        <span class="budget-label">预算预估</span>
        <span class="budget-percent">{{ percent }}%</span>
      </div>
      <div class="budget-bar">
        <div class="budget-fill" :style="{ width: percent + '%' }"></div>
      </div>
      <div class="budget-details">
        <span class="budget-used">¥{{ (budgetUsed ?? 0).toLocaleString() }}</span>
        <span class="budget-separator">/</span>
        <span class="budget-total">¥{{ totalBudget.toLocaleString() }}</span>
      </div>
    </div>

    <!-- 说明文字 -->
    <div class="caption">
      前端每 2~3s 轮询（或 SSE 订阅），读 <b>status</b> 字段：
      <code>analyzing→planning→assembling→budgeting</code>。进度节点 = 5 Agent 状态机的可视化。
    </div>
  </div>
</template>

<style scoped>
.desktop-progress-container {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* 状态栏 */
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #f9f9f9;
  border-radius: 16px;
  border: 1px solid #e8e8e8;
}

.status-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-icon {
  font-size: 20px;
}

.status-text {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.status-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.elapsed-time {
  font-size: 14px;
  color: #888;
  font-family: ui-monospace, Menlo, monospace;
}

.cancel-btn {
  padding: 8px 16px;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 12px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s ease;
}

.cancel-btn:hover {
  background: #f5f5f5;
  border-color: #ff2442;
  color: #ff2442;
}

/* 标题区域 */
.header-section {
  text-align: center;
  padding: 24px 0;
}

.plan-title {
  font-size: 28px;
  font-weight: 800;
  color: #2b2b2b;
  margin-bottom: 8px;
}

.plan-subtitle {
  font-size: 15px;
  color: #888;
}

/* Agent 卡片网格 */
.agents-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.agent-card {
  background: #fff;
  border: 2px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px;
  transition: all 0.3s ease;
}

.agent-card:hover {
  border-color: #ff2442;
  box-shadow: 0 8px 24px rgba(255, 36, 66, 0.1);
  transform: translateY(-2px);
}

.agent-header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.agent-badge {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
  background: #f1f1f1;
}

.agent-badge.b-done {
  background: #e8faf1;
  color: #16c784;
}

.agent-badge.b-run {
  background: #ffe9ed;
  color: #ff2442;
}

.agent-badge.b-wait {
  background: #f1f1f1;
  color: #bbb;
}

.agent-info {
  flex: 1;
}

.agent-name {
  font-size: 18px;
  font-weight: 700;
  color: #333;
  margin-bottom: 6px;
}

.agent-desc {
  font-size: 13px;
  color: #888;
  line-height: 1.5;
}

.agent-status {
  font-size: 14px;
  font-weight: 700;
  padding: 6px 12px;
  border-radius: 8px;
  background: #f5f5f5;
  color: #bbb;
  white-space: nowrap;
}

.agent-status.run {
  background: #ffe9ed;
  color: #ff2442;
}

.agent-status.done {
  background: #e8faf1;
  color: #16c784;
}

/* 预算区域 */
.budget-section {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #e8e8e8;
}

.budget-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.budget-label {
  font-size: 14px;
  font-weight: 600;
  color: #666;
}

.budget-percent {
  font-size: 20px;
  font-weight: 800;
  color: #ff2442;
}

.budget-bar {
  height: 12px;
  background: #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 12px;
}

.budget-fill {
  height: 100%;
  background: linear-gradient(90deg, #ff6a80, #ff2442);
  border-radius: 6px;
  transition: width 0.4s ease;
}

.budget-details {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  color: #888;
}

.budget-used,
.budget-total {
  font-weight: 700;
  color: #333;
}

.budget-separator {
  color: #ccc;
}

.caption {
  font-size: 13px;
  color: #aaa;
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
