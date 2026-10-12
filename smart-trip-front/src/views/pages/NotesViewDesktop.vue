<script setup lang="ts">
/**
 * 页面：③ 小红书笔记溯源（桌面端）—— XHSAnalysisAgent 分析过的笔记瀑布流
 */
import NoteWaterfallDesktop from '@/components/notes/NoteWaterfallDesktop.vue'
import { mockNotes } from '@/constants/mock'
</script>

<template>
  <div class="notes-container">
    <div class="page-header">
      <div class="header-left">
        <h1 class="title">📕 精选笔记溯源</h1>
        <p class="subtitle">AI 从海量小红书笔记中智能筛选，为您提炼精华内容</p>
      </div>
      <div class="header-stats">
        <div class="stat-item">
          <span class="stat-value">{{ mockNotes.length }}</span>
          <span class="stat-label">篇精选</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">98%</span>
          <span class="stat-label">相关度</span>
        </div>
      </div>
    </div>

    <NoteWaterfallDesktop :notes="mockNotes" />
  </div>
</template>

<style scoped>
.notes-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 40px 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 40px;
  padding-bottom: 32px;
  border-bottom: 2px solid #f0f0f0;
}

.header-left {
  flex: 1;
}

.title {
  font-size: 32px;
  font-weight: 800;
  color: #2b2b2b;
  margin-bottom: 12px;
}

.subtitle {
  font-size: 16px;
  color: #888;
  line-height: 1.6;
}

.header-stats {
  display: flex;
  gap: 32px;
}

.stat-item {
  text-align: center;
}

.stat-value {
  display: block;
  font-size: 36px;
  font-weight: 800;
  color: #ff2442;
  line-height: 1;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: #888;
}
</style>
