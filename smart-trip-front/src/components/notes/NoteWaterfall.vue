<script setup lang="ts">
/** 模块③ 小红书笔记溯源 —— XHSAnalysisAgent 分析过的笔记瀑布流 */
import NoteCard from '@/components/notes/NoteCard.vue'
import type { XHSNote } from '@/models/trip-plan'

defineProps<{ notes: XHSNote[]; tabs?: string[] }>()
</script>

<template>
  <div class="scroll">
    <div class="topbar"><div class="search">云南 古镇 小众</div></div>
    <div class="tabs">
      <span v-for="(t, i) in tabs ?? ['为你精选', '大理', '丽江', '美食']" :key="t" :class="{ on: i === 0 }">{{
        t
      }}</span>
    </div>
    <div class="falls">
      <NoteCard v-for="n in notes" :key="n.id" :note="n" />
    </div>
    <div class="caption" style="padding: 0 14px 14px">
      瀑布流展示 XHS Agent 分析过的笔记（XHSNote → 候选 Attraction）。<b>红色"提及"标签</b
      >=情感分析正向且提及景点，可点进溯源到具体推荐。
    </div>
  </div>
</template>
