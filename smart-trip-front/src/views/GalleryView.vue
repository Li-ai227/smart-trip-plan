<script setup lang="ts">
/**
 * 效果图墙 —— 手机端 / 桌面端双模展示。
 * 手机端：6 个模块放入 PhoneFrame；桌面端：7 个模块直接渲染 Desktop 组件。
 * 底部提供切换按钮。
 */
import { computed } from 'vue'
import { useDeviceStore } from '@/stores/device'
import PhoneFrame from '@/components/PhoneFrame.vue'

// 手机端组件
import IntentForm from '@/components/intent/IntentForm.vue'
import AgentProgress from '@/components/planning/AgentProgress.vue'
import NoteWaterfall from '@/components/notes/NoteWaterfall.vue'
import ItineraryTimeline from '@/components/itinerary/ItineraryTimeline.vue'
import BudgetPanel from '@/components/budget/BudgetPanel.vue'
import HitlSheet from '@/components/hitl/HitlSheet.vue'
import FeedbackExport from '@/components/feedback/FeedbackExport.vue'

// 桌面端组件
import IntentFormDesktop from '@/components/intent/IntentFormDesktop.vue'
import AgentProgressDesktop from '@/components/planning/AgentProgressDesktop.vue'
import NoteWaterfallDesktop from '@/components/notes/NoteWaterfallDesktop.vue'
import ItineraryTimelineDesktop from '@/components/itinerary/ItineraryTimelineDesktop.vue'
import HitlSheetDesktop from '@/components/hitl/HitlSheetDesktop.vue'
import FeedbackExportDesktop from '@/components/feedback/FeedbackExportDesktop.vue'

import {
  mockAgentProgress,
  mockHistoryItems,
  mockNotes,
  mockPlanConfirmed,
  mockPlanReview,
  mockRequest,
} from '@/constants/mock'

const noop = () => {}
const reviewPlan = mockPlanReview.plan!
const confirmedPlan = mockPlanConfirmed.plan!

const deviceStore = useDeviceStore()
const mode = computed(() => deviceStore.mode)
</script>

<template>
  <div class="gallery-page">
    <div class="page-head">
      <h1>智能旅游规划 <span class="dot">·</span> 前端效果图（小红书风格）</h1>
      <p>
        小红书旅游规划多智能体系统 · 前端按模块拆分原型 ｜ 设计语言：品牌红 #FF2442 · 白底圆角卡片 ·
        瀑布流 · 标签胶囊<br />
        所有字段与状态对齐 <code>.harness/wiki/接口协议.md</code> 与 <code>业务模型.md</code>。
      </p>
      <div class="legend">
        <span>主触发 <b>POST /api/v1/trip-plan</b></span>
        <span>轮询 <b>GET /trip-plan/{planId}</b></span>
        <span>人工介入 <b>POST /{planId}/intervene</b></span>
        <span>反馈 <b>POST /{planId}/feedback</b></span>
        <span>历史 <b>GET /trip-plan/history</b></span>
      </div>
    </div>

    <!-- ========== 手机端效果图墙 ========== -->
    <div v-if="mode === 'mobile'" class="wall">
      <!-- ① 意图输入 -->
      <div class="module">
        <div class="mhead">
          <div class="num">1</div>
          <div class="t">意图输入页</div>
          <div class="api">POST /trip-plan</div>
        </div>
        <PhoneFrame>
          <IntentForm :model-value="mockRequest" @submit="noop" />
        </PhoneFrame>
      </div>

      <!-- ② 规划进行中 -->
      <div class="module">
        <div class="mhead">
          <div class="num">2</div>
          <div class="t">规划进行中</div>
          <div class="api">GET /trip-plan/{id}</div>
        </div>
        <PhoneFrame>
          <AgentProgress
            status="assembling"
            :agents="mockAgentProgress"
            :budget-used="4200"
            :budget-total="8000"
            @cancel="noop"
          />
        </PhoneFrame>
      </div>

      <!-- ③ 小红书笔记溯源 -->
      <div class="module">
        <div class="mhead">
          <div class="num">3</div>
          <div class="t">小红书笔记溯源</div>
          <div class="api">XHSAnalysisAgent</div>
        </div>
        <PhoneFrame>
          <NoteWaterfall :notes="mockNotes" />
          <template #tabbar>
            <div class="tabbar">
              <div class="it on"><span class="ic">🏠</span>首页</div>
              <div class="it"><span class="ic">🧭</span>发现</div>
              <div class="it"><span class="plus">+</span></div>
              <div class="it"><span class="ic">💬</span>消息</div>
              <div class="it"><span class="ic">👤</span>我的</div>
            </div>
          </template>
        </PhoneFrame>
      </div>

      <!-- ④ 行程结果页 -->
      <div class="module">
        <div class="mhead">
          <div class="num">4</div>
          <div class="t">行程结果页</div>
          <div class="api">status=review</div>
        </div>
        <PhoneFrame>
          <ItineraryTimeline :days="reviewPlan.days" :active-index="1" :draft="true" @select="noop" />
        </PhoneFrame>
      </div>

      <!-- ⑤ 预算面板 + HITL -->
      <div class="module">
        <div class="mhead">
          <div class="num">5</div>
          <div class="t">预算面板 + 人工介入</div>
          <div class="api">POST /{id}/intervene</div>
        </div>
        <PhoneFrame>
          <div class="scroll">
            <div class="pad">
              <BudgetPanel :budget="reviewPlan.budget" />
              <div class="caption">
                右侧面板=<code>Budget.breakdown</code> 四类拆分。当
                <b>requiresHumanIntervention=true</b> 时，压出底部确认弹窗 →
              </div>
            </div>
          </div>
          <HitlSheet
            :reason="mockPlanReview.interventionReason"
            :advice="mockPlanReview.interventionAdvice"
            @action="noop"
          />
        </PhoneFrame>
      </div>

      <!-- ⑥ 确认 · 导出 · 反馈 -->
      <div class="module">
        <div class="mhead">
          <div class="num">6</div>
          <div class="t">确认 · 导出 · 反馈</div>
          <div class="api">POST /{id}/feedback</div>
        </div>
        <PhoneFrame>
          <FeedbackExport :plan="confirmedPlan" @feedback="noop" @export="noop" />
        </PhoneFrame>
      </div>
    </div>

    <!-- ========== 桌面端效果图墙 ========== -->
    <div v-else class="desktop-wall">
      <!-- ① 意图输入 -->
      <div class="desktop-module">
        <div class="mhead">
          <div class="num">1</div>
          <div class="t">意图输入页</div>
          <div class="api">POST /trip-plan</div>
        </div>
        <div class="desktop-frame">
          <IntentFormDesktop :model-value="mockRequest" @submit="noop" />
        </div>
      </div>

      <!-- ② 规划进行中 -->
      <div class="desktop-module">
        <div class="mhead">
          <div class="num">2</div>
          <div class="t">规划进行中</div>
          <div class="api">GET /trip-plan/{id}</div>
        </div>
        <div class="desktop-frame">
          <AgentProgressDesktop
            status="assembling"
            :agents="mockAgentProgress"
            :budget-used="4200"
            :budget-total="8000"
            elapsed="00:41"
            @cancel="noop"
          />
        </div>
      </div>

      <!-- ③ 小红书笔记溯源 -->
      <div class="desktop-module">
        <div class="mhead">
          <div class="num">3</div>
          <div class="t">小红书笔记溯源</div>
          <div class="api">XHSAnalysisAgent</div>
        </div>
        <div class="desktop-frame">
          <NoteWaterfallDesktop :notes="mockNotes" />
        </div>
      </div>

      <!-- ④ 行程结果页 -->
      <div class="desktop-module">
        <div class="mhead">
          <div class="num">4</div>
          <div class="t">行程结果页</div>
          <div class="api">status=review</div>
        </div>
        <div class="desktop-frame">
          <ItineraryTimelineDesktop :days="reviewPlan.days" :active-index="1" :draft="true" @select="noop" />
        </div>
      </div>

      <!-- ⑤ 人工介入弹窗 -->
      <div class="desktop-module">
        <div class="mhead">
          <div class="num">5</div>
          <div class="t">人工介入弹窗</div>
          <div class="api">POST /{id}/intervene</div>
        </div>
        <div class="desktop-frame hitl-preview">
          <HitlSheetDesktop
            :reason="mockPlanReview.interventionReason"
            :advice="mockPlanReview.interventionAdvice"
            @action="noop"
          />
        </div>
      </div>

      <!-- ⑥ 确认 · 导出 · 反馈 -->
      <div class="desktop-module">
        <div class="mhead">
          <div class="num">6</div>
          <div class="t">确认 · 导出 · 反馈</div>
          <div class="api">POST /{id}/feedback</div>
        </div>
        <div class="desktop-frame">
          <FeedbackExportDesktop :plan="confirmedPlan" @feedback="noop" @export="noop" />
        </div>
      </div>

      <!-- ⑦ 历史方案列表 -->
      <div class="desktop-module">
        <div class="mhead">
          <div class="num">7</div>
          <div class="t">历史方案列表</div>
          <div class="api">GET /trip-plan/history</div>
        </div>
        <div class="desktop-frame">
          <div class="history-preview">
            <div class="history-preview-header">
              <h2>历史方案</h2>
              <p>查看和搜索您的旅行规划记录</p>
            </div>
            <div class="history-preview-search">
              <input type="text" placeholder="搜索目的地..." disabled />
            </div>
            <div class="history-preview-grid">
              <div v-for="item in mockHistoryItems" :key="item.planId" class="history-preview-card">
                <div class="hpc-header">
                  <span class="hpc-dest">{{ item.destination }}</span>
                  <span :class="['hpc-badge', item.status === 'confirmed' ? 'hpc-ok' : 'hpc-fail']">
                    {{ item.status === 'confirmed' ? '已完成' : '已失败' }}
                  </span>
                </div>
                <div class="hpc-body">
                  <span>{{ item.startDate }} 起 · {{ item.days }} 天</span>
                  <span>{{ item.headcount }} 人 · 预算 {{ item.budget }} 元</span>
                  <span v-if="item.totalCost != null" class="hpc-cost">实际 {{ item.totalCost }} 元</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 模式切换 ========== -->
    <div class="mode-switch">
      <button :class="['mode-btn', { active: mode === 'mobile' }]" @click="deviceStore.setMode('mobile')">
        手机端效果图
      </button>
      <button :class="['mode-btn', { active: mode === 'desktop' }]" @click="deviceStore.setMode('desktop')">
        桌面端效果图
      </button>
    </div>

    <div class="foot">
      智能旅游规划 smart-trip-front · 前端原型（建议变更 C-NNN-web-frontend）<br />
      消费 smart-trip-server REST API，不直接调用 Agent（符合工程结构 R3 分层）· 详见《重构大纲》第 25 章
    </div>
  </div>
</template>

<style scoped>
/* ---- 桌面端效果图墙 ---- */
.desktop-wall {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 40px;
}

.desktop-module {
  width: 100%;
}

.desktop-frame {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  border: 1px solid #ececec;
  padding: 24px;
}

/* HitlSheetDesktop 是全屏 overlay，在预览中需要限制在容器内 */
.hitl-preview {
  position: relative;
  min-height: 480px;
  padding: 0;
}

.hitl-preview :deep(.desktop-hitl-overlay) {
  position: absolute;
  border-radius: 16px;
}

/* ---- 历史方案预览（静态 mock，不依赖 store） ---- */
.history-preview {
  max-width: 1000px;
  margin: 0 auto;
  padding: 16px 0;
}

.history-preview-header {
  text-align: center;
  margin-bottom: 24px;
}

.history-preview-header h2 {
  font-size: 28px;
  font-weight: 800;
  color: #2b2b2b;
  margin-bottom: 6px;
}

.history-preview-header p {
  font-size: 15px;
  color: #888;
}

.history-preview-search {
  max-width: 400px;
  margin: 0 auto 24px;
}

.history-preview-search input {
  width: 100%;
  padding: 10px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  font-size: 14px;
  background: #f9f9f9;
  color: #aaa;
  box-sizing: border-box;
}

.history-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.history-preview-card {
  background: #f9f9fb;
  border-radius: 12px;
  padding: 16px 20px;
  border: 1px solid #f0f0f0;
}

.hpc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.hpc-dest {
  font-size: 18px;
  font-weight: 700;
  color: #2b2b2b;
}

.hpc-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 8px;
  white-space: nowrap;
  flex-shrink: 0;
}

.hpc-ok {
  background: #e8faf1;
  color: #16c784;
}

.hpc-fail {
  background: #ffeaee;
  color: #ff2442;
}

.hpc-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 13px;
  color: #666;
}

.hpc-cost {
  color: #ff2442;
  font-weight: 600;
}

/* ---- 模式切换按钮 ---- */
.mode-switch {
  max-width: 1280px;
  margin: 40px auto 0;
  display: flex;
  justify-content: center;
  gap: 12px;
}

.mode-btn {
  padding: 10px 28px;
  border: 2px solid #e0e0e0;
  border-radius: 24px;
  background: #fff;
  color: #666;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mode-btn:hover {
  border-color: #ff2442;
  color: #ff2442;
}

.mode-btn.active {
  background: #ff2442;
  border-color: #ff2442;
  color: #fff;
}
</style>
