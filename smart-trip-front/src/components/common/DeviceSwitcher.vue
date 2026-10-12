<script setup lang="ts">
/**
 * 设备切换控制器
 * @description 允许用户在桌面端/移动端布局间切换
 */
import { computed } from 'vue'
import { useDeviceStore } from '@/stores/device'
import type { DeviceMode } from '@/stores/device'
import { useRouter } from 'vue-router'

const deviceStore = useDeviceStore()
const router = useRouter()

type ModeOption = {
  mode: DeviceMode
  label: string
  icon: string
  description: string
}

const modes: ModeOption[] = [
  {
    mode: 'desktop',
    label: '桌面端',
    icon: '🖥️',
    description: '宽屏优化布局，适合电脑浏览',
  },
  {
    mode: 'mobile',
    label: '手机端',
    icon: '📱',
    description: '手机模拟器，预览移动端效果',
  },
]

const currentMode = computed(() => {
  return modes.find((m) => m.mode === deviceStore.mode)!
})

async function switchMode(newMode: DeviceMode) {
  if (deviceStore.mode === newMode) {
    return
  }
  deviceStore.setMode(newMode)
  await router.push('/intent')
}
</script>

<template>
  <div class="device-switcher">
    <div class="switcher-label">设备模式</div>
    <div class="mode-options">
      <button
        v-for="item in modes"
        :key="item.mode"
        class="mode-btn"
        :class="{ on: deviceStore.mode === item.mode }"
        @click="switchMode(item.mode)"
        :title="item.description"
      >
        <span class="icon">{{ item.icon }}</span>
        <span class="label">{{ item.label }}</span>
      </button>
    </div>
    <div class="current-mode" v-if="deviceStore.isDesktop">
      <span class="hint">当前：{{ currentMode.label }}</span>
    </div>
  </div>
</template>

<style scoped>
.device-switcher {
  display: flex;
  align-items: center;
  gap: 12px;
}

.switcher-label {
  font-size: 13px;
  color: #666;
  font-weight: 600;
}

.mode-options {
  display: flex;
  gap: 8px;
  background: #f5f5f5;
  padding: 4px;
  border-radius: 12px;
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s ease;
  font-weight: 500;
}

.mode-btn:hover {
  background: rgba(255, 36, 66, 0.1);
  color: #ff2442;
}

.mode-btn.on {
  background: #ff2442;
  color: #fff;
  box-shadow: 0 2px 8px rgba(255, 36, 66, 0.3);
}

.mode-btn .icon {
  font-size: 14px;
}

.current-mode {
  font-size: 12px;
  color: #999;
}

.hint {
  color: #ff2442;
  font-weight: 600;
}
</style>
