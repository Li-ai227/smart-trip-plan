/**
 * 设备模式 Store - 管理桌面端/移动端切换
 * @description 支持用户在不同设备布局间切换，状态持久化到 localStorage
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type DeviceMode = 'desktop' | 'mobile'

interface DeviceModeState {
  mode: DeviceMode
}

export const useDeviceStore = defineStore('device', () => {
  // 从 localStorage 读取初始值，默认桌面端
  const storedMode = localStorage.getItem('device-mode') as DeviceMode | null
  const mode = ref<DeviceMode>(storedMode ?? 'desktop')

  /**
   * 切换设备模式
   */
  function toggle() {
    mode.value = mode.value === 'desktop' ? 'mobile' : 'desktop'
    localStorage.setItem('device-mode', mode.value)
  }

  /**
   * 设置为指定模式
   */
  function setMode(newMode: DeviceMode) {
    mode.value = newMode
    localStorage.setItem('device-mode', newMode)
  }

  /**
   * 判断是否为移动端模式
   */
  const isMobile = computed(() => mode.value === 'mobile')

  /**
   * 判断是否为桌面端模式
   */
  const isDesktop = computed(() => mode.value === 'desktop')

  return {
    mode,
    isMobile,
    isDesktop,
    toggle,
    setMode,
  }
})
