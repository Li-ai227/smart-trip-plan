<script setup lang="ts">
/**
 * 笔记瀑布流（桌面端）—— 多列网格布局，卡片更大更清晰
 */
import NoteCardDesktop from '@/components/notes/NoteCardDesktop.vue'
import type { XHSNote } from '@/models/trip-plan'

const props = defineProps<{
  notes: XHSNote[]
  tabs?: string[]
}>()

const defaultTabs = ['为你精选', '大理', '丽江', '美食', '攻略', '避坑']
</script>

<template>
  <div class="desktop-waterfall-container">
    <!-- 顶部搜索和标签 -->
    <div class="waterfall-header">
      <div class="search-bar">
        <span class="search-icon">🔍</span>
        <input type="text" class="search-input" placeholder="搜索云南、古镇、小众景点…" />
      </div>
      <div class="tabs-container">
        <button
          v-for="(t, i) in tabs ?? defaultTabs"
          :key="t"
          class="tab-btn"
          :class="{ on: i === 0 }"
        >
          {{ t }}
        </button>
      </div>
    </div>

    <!-- 瀑布流网格 -->
    <div class="waterfall-grid">
      <NoteCardDesktop v-for="n in notes" :key="n.id" :note="n" />
    </div>

    <!-- 说明文字 -->
    <div class="waterfall-caption">
      瀑布流展示 XHS Agent 分析过的笔记（XHSNote → 候选 Attraction）。
      <b>红色"提及"标签</b> = 情感分析正向且提及景点，可点进溯源到具体推荐。
    </div>
  </div>
</template>

<style scoped>
.desktop-waterfall-container {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* 头部区域 */
.waterfall-header {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f5f5f5;
  padding: 14px 18px;
  border-radius: 12px;
  margin-bottom: 20px;
}

.search-icon {
  font-size: 18px;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 15px;
  color: #333;
  outline: none;
}

.search-input::placeholder {
  color: #aaa;
}

.tabs-container {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.tab-btn {
  padding: 10px 20px;
  background: #f9f9f9;
  border: 2px solid transparent;
  border-radius: 24px;
  font-size: 14px;
  font-weight: 600;
  color: #666;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-btn:hover {
  background: #ffe9ed;
  color: #ff2442;
}

.tab-btn.on {
  background: #ff2442;
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 36, 66, 0.3);
}

/* 瀑布流网格 */
.waterfall-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

/* 说明文字 */
.waterfall-caption {
  font-size: 13px;
  color: #999;
  line-height: 1.6;
  text-align: center;
}

.waterfall-caption b {
  color: #ff2442;
}
</style>
