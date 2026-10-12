<script setup lang="ts">
/** 模块② 规划进行中 —— 5 Agent 协作可视化（status 机驱动），轮询 GET /trip-plan/{id} */
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
</script>

<template>
  <div class="scroll">
    <div class="pad">
      <div class="h2">正在规划中… <span class="spin"></span></div>
      <div class="muted">{{ title ?? '' }}</div>

      <div class="card" style="margin-top: 14px">
        <div class="agent" v-for="a in agents" :key="a.key">
          <div class="badge" :class="badgeClass(a.state)">{{ badgeIcon(a) }}</div>
          <div>
            <div class="nm">{{ a.name }}</div>
            <div class="ds">{{ a.desc }}</div>
          </div>
          <div class="st" :class="a.state === 'running' ? 'run' : a.state">{{ stText(a.state) }}</div>
        </div>
      </div>

      <div class="card">
        <div class="muted">预算预估</div>
        <div class="bar"><i :style="{ width: percent + '%' }"></i></div>
        <div class="muted" style="margin-top: 7px">
          ¥{{ (budgetUsed ?? 0).toLocaleString() }} / ¥{{ (budgetTotal ?? 0).toLocaleString() }} ·
          已耗时 {{ elapsed ?? '00:41' }}
        </div>
      </div>

      <button class="cta gray" @click="emit('cancel')">取消规划</button>
      <div class="caption">
        前端每 2~3s 轮询（或 SSE 订阅），读 <b>status</b> 字段：<code
          >analyzing→planning→assembling→budgeting</code
        >。进度节点 = 5 Agent 状态机的可视化。
      </div>
    </div>
  </div>
</template>
