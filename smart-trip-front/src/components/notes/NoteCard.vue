<script setup lang="ts">
/** 小红书笔记卡（瀑布流单元） */
import type { XHSNote } from '@/models/trip-plan'

defineProps<{ note: XHSNote }>()

const likeText = (n: number) => (n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n))
</script>

<template>
  <div class="note">
    <div class="cover" :class="note.coverClass"><div class="emo">{{ note.emoji }}</div></div>
    <div class="tx">
      <div class="ti">{{ note.title }}</div>
      <span v-if="note.mention" class="tag-mention">{{ note.mention }}</span>
      <div class="ft">
        <span class="av"></span>{{ note.author }}<span class="lk">❤ {{ likeText(note.likes) }}</span>
      </div>
    </div>
  </div>
</template>
