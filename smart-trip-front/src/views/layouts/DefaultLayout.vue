<script setup lang="ts">
import { computed, watch, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useDeviceStore } from '@/stores/device'
import { useAuthStore } from '@/stores/auth'
import { useRoute, useRouter } from 'vue-router'
import DeviceSwitcher from '@/components/common/DeviceSwitcher.vue'

import IntentViewDesktop from '@/views/pages/IntentViewDesktop.vue'
import PlanningViewDesktop from '@/views/pages/PlanningViewDesktop.vue'
import NotesViewDesktop from '@/views/pages/NotesViewDesktop.vue'
import ItineraryViewDesktop from '@/views/pages/ItineraryViewDesktop.vue'
import ResultViewDesktop from '@/views/pages/ResultViewDesktop.vue'
import HistoryViewDesktop from '@/views/pages/HistoryViewDesktop.vue'

const deviceStore = useDeviceStore()
const { isDesktop, mode } = storeToRefs(deviceStore)
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const isGallery = computed(() => route.name === 'gallery')

const DESKTOP_MAP: Record<string, any> = {
  intent: IntentViewDesktop,
  planning: PlanningViewDesktop,
  notes: NotesViewDesktop,
  plan: ItineraryViewDesktop,
  result: ResultViewDesktop,
  history: HistoryViewDesktop,
}

const desktopOverride = computed(() => {
  if (!isDesktop.value) return null
  return DESKTOP_MAP[String(route.name || '')] || null
})

watch(
  mode,
  (m) => {
    document.body.classList.toggle('desktop-mode', m === 'desktop')
    document.body.classList.toggle('mobile-mode', m === 'mobile')
  },
  { immediate: true },
)

onUnmounted(() => {
  document.body.classList.remove('desktop-mode', 'mobile-mode')
})

async function onLogout() {
  await authStore.logoutAction()
  window.location.href = '/login'
}
</script>

<template>
  <div :class="isDesktop ? 'desktop-shell' : 'mobile-shell'">
    <nav :class="['topnav', isDesktop ? 'topnav-desktop' : 'topnav-mobile']">
      <div class="nav-left">
        <span class="brand">
          智能旅游规划
        </span>
      </div>
      <div class="nav-center">
        <DeviceSwitcher />
      </div>
      <div class="nav-right">
        <button
          class="navlink history-btn"
          title="查看历史方案"
          @click="router.push({ name: 'history' })"
        >
          {{ isDesktop ? '历史方案' : '历史' }}
        </button>
        <button
          class="navlink gallery-btn"
          title="切换效果图墙 / 交互流程"
          @click="router.push(isGallery ? '/intent' : '/gallery')"
        >
          {{ isGallery ? '← 交互流程' : '🎨 图墙' }}
        </button>
        <button
          v-if="authStore.isAuthenticated"
          class="navlink logout-btn"
          data-testid="logout-btn"
          @click="onLogout"
        >
          {{ isDesktop ? '退出登录' : '退出' }}
        </button>
      </div>
    </nav>

    <main :class="isDesktop ? 'main-desktop' : 'main-mobile'">
      <RouterView v-slot="{ Component }">
        <component v-if="desktopOverride" :is="desktopOverride" />
        <div v-else-if="!isDesktop" class="stage">
          <component :is="Component" />
        </div>
        <component v-else :is="Component" />
      </RouterView>
    </main>
  </div>
</template>

<style scoped>
/* ---- Shell ---- */
.desktop-shell,
.mobile-shell {
  min-height: 100vh;
  background: #f4f4f5;
}

/* ---- Top Nav (shared) ---- */
.topnav {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid #e8e8e8;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.topnav-desktop {
  height: 64px;
  padding: 0 40px;
}

.topnav-mobile {
  height: 56px;
  padding: 0 16px;
}

.nav-left {
  flex: 1;
}

.nav-center {
  flex: 0 0 auto;
}

.nav-right {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

.topnav-mobile .nav-right {
  gap: 6px;
}

.brand {
  font-weight: 800;
  color: #2b2b2b;
}

.topnav-desktop .brand {
  font-size: 18px;
}

.topnav-mobile .brand {
  font-size: 15px;
}

.brand b {
  color: #ff2442;
}

/* ---- Nav Links ---- */
.navlink {
  color: #ff2442;
  font-weight: 600;
  text-decoration: none;
  border-radius: 20px;
  transition: all 0.2s ease;
}

.topnav-desktop .navlink {
  font-size: 14px;
  padding: 8px 16px;
}

.topnav-mobile .navlink {
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 16px;
}

.navlink:hover {
  background: #ff2442;
  color: #fff;
}

.history-btn,
.gallery-btn {
  border: none;
  cursor: pointer;
  background: #fff0f3;
  color: #ff2442;
}

.logout-btn {
  border: none;
  cursor: pointer;
  color: #888;
  background: #f5f5f5;
  transition: all 0.2s ease;
}

.topnav-desktop .logout-btn {
  font-size: 14px;
  padding: 8px 16px;
  border-radius: 20px;
}

.topnav-mobile .logout-btn {
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 16px;
}

.logout-btn:hover {
  background: #e8e8e8;
  color: #333;
}

/* ---- Main Content ---- */
.main-desktop {
  max-width: 1400px;
  margin: 0 auto;
  padding: 40px 20px;
}

.main-mobile {
  padding: 20px 0;
}

.stage {
  min-height: calc(100vh - 56px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 16px;
}
</style>
