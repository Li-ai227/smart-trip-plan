<script setup lang="ts">
/** 页面：③ 小红书笔记溯源 —— XHSAnalysisAgent 分析过的笔记瀑布流 */
import PhoneFrame from '@/components/PhoneFrame.vue'
import NoteWaterfall from '@/components/notes/NoteWaterfall.vue'
import { mockNotes } from '@/constants/mock'
</script>

<template>
  <div class="stage">
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
</template>
