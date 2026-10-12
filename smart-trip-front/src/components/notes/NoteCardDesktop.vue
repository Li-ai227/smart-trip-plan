<script setup lang="ts">
/**
 * 笔记卡片（桌面端）—— 大图展示，信息更丰富
 */
import type { XHSNote } from '@/models/trip-plan'

const props = defineProps<{ note: XHSNote }>()

const likeText = (n: number) => (n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n))
</script>

<template>
  <div class="desktop-note-card">
    <div class="cover" :class="note.coverClass">
      <div class="emoji">{{ note.emoji }}</div>
      <div class="cover-overlay">
        <span class="mention-tag" v-if="note.mention">🏷️ {{ note.mention }}</span>
      </div>
    </div>
    <div class="content">
      <h3 class="title">{{ note.title }}</h3>
      <div class="meta">
        <div class="author">
          <div class="avatar"></div>
          <span class="name">{{ note.author }}</span>
        </div>
        <div class="likes">
          <span class="heart">❤</span>
          <span class="count">{{ likeText(note.likes) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.desktop-note-card {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
  cursor: pointer;
}

.desktop-note-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12);
}

.cover {
  height: 0;
  position: relative;
  overflow: hidden;
}

.cover.c1 {
  padding-top: 133%;
  background: linear-gradient(135deg, #ffc1cc, #ff8fa3);
}

.cover.c2 {
  padding-top: 150%;
  background: linear-gradient(135deg, #c8e7ff, #a0c4ff);
}

.cover.c3 {
  padding-top: 120%;
  background: linear-gradient(135deg, #d6f5d6, #9be59b);
}

.cover.c4 {
  padding-top: 140%;
  background: linear-gradient(135deg, #ffe0b3, #ffb877);
}

.cover .emoji {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 64px;
}

.cover-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.4), transparent);
}

.mention-tag {
  display: inline-block;
  background: rgba(255, 255, 255, 0.95);
  color: #ff2442;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 700;
  backdrop-filter: blur(8px);
}

.content {
  padding: 20px;
}

.title {
  font-size: 16px;
  font-weight: 700;
  color: #2b2b2b;
  line-height: 1.5;
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.author {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #ddd;
}

.name {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

.likes {
  display: flex;
  align-items: center;
  gap: 4px;
}

.heart {
  font-size: 14px;
}

.count {
  font-size: 13px;
  color: #999;
  font-weight: 600;
}
</style>
