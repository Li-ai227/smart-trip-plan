<script setup lang="ts">
/** 手机外壳：状态栏 + 屏幕区（默认插槽）+ 可选底部 Tab 栏（#tabbar 插槽） */
withDefaults(defineProps<{ time?: string }>(), { time: '9:41' })
</script>

<template>
  <div class="phone">
    <div class="statusbar">
      <span>{{ time }}</span>
      <span class="dots">●●● 📶 🔋</span>
    </div>
    <div class="screen">
      <slot />
    </div>
    <slot name="tabbar" />
  </div>
</template>
