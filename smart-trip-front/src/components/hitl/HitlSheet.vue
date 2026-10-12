<script setup lang="ts">
/** 模块⑤ 人工介入（HITL）底部弹窗 —— 三动作映射 POST /{planId}/intervene */
import type { InterveneAction } from '@/models/trip-plan'

defineProps<{ reason?: string; advice?: string }>()
const emit = defineEmits<{
  (e: 'action', action: InterveneAction): void
  (e: 'close'): void
}>()
</script>

<template>
  <div class="scrim" @click.self="emit('close')">
    <div class="sheet">
      <button class="sheet-close" @click="emit('close')" title="关闭">✕</button>
      <div class="ic">⚠️</div>
      <h3>需要你确认</h3>
      <div class="sub">{{ reason ?? '方案需要你的确认' }}</div>
      <div class="advise" v-if="advice">💡 建议：{{ advice }}</div>
      <button class="cta" @click="emit('action', 'CONFIRM')">接受建议（CONFIRM）</button>
      <button class="cta ghost" @click="emit('action', 'MODIFY')">我来改（MODIFY）</button>
      <button class="cta gray" @click="emit('action', 'REJECT')">拒绝，重新规划（REJECT）</button>
    </div>
  </div>
</template>

<style scoped>
.sheet {
  position: relative;
}
.sheet-close {
  position: absolute;
  top: 14px;
  right: 14px;
  background: transparent;
  border: none;
  font-size: 16px;
  color: #999;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  line-height: 1;
}
.sheet-close:active {
  background: #f0f0f0;
  color: #333;
}
</style>

