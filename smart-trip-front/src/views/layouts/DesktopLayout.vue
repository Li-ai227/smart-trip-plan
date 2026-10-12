<script setup lang="ts">
/**
 * 桌面端布局
 * @description 宽屏优化布局，适合电脑浏览器使用
 */
import { computed, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import DeviceSwitcher from '@/components/common/DeviceSwitcher.vue'
import { useRoute, useRouter } from 'vue-router'

// 静态导入所有 desktop 组件
import IntentViewDesktop from '@/views/pages/IntentViewDesktop.vue'
import PlanningViewDesktop from '@/views/pages/PlanningViewDesktop.vue'
import NotesViewDesktop from '@/views/pages/NotesViewDesktop.vue'
import ItineraryViewDesktop from '@/views/pages/ItineraryViewDesktop.vue'
import ResultViewDesktop from '@/views/pages/ResultViewDesktop.vue'
import HistoryViewDesktop from '@/views/pages/HistoryViewDesktop.vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

/** 根据路径解析对应的 desktop 组件 */
function resolveDesktopComponent(path: string, routeName: string): any {
  if (path === '/intent' || routeName === 'intent') return IntentViewDesktop
  if (path.startsWith('/planning') || routeName === 'planning') return PlanningViewDesktop
  if (path.startsWith('/notes') || routeName === 'notes') return NotesViewDesktop
  if (path.startsWith('/plan') || routeName === 'plan') return ItineraryViewDesktop
  if (path.startsWith('/result') || routeName === 'result') return ResultViewDesktop
  if (path === '/history' || routeName === 'history') return HistoryViewDesktop
  if (path.startsWith('/gallery') || routeName === 'gallery') return null
  return null
}

// 当前 desktop 组件：computed 自动跟随 route 变化，组件销毁时自动清理。
// 避免 onMounted 前短暂 fallback 到 RouterView 导致 IntentView/IntentForm 双重挂载。
const CurrentDesktopComponent = computed(() =>
  resolveDesktopComponent(route.path, String(route.name || '')),
)

// 设置 body 类名
onMounted(() => {
  document.body.classList.remove('mobile-mode')
  document.body.classList.add('desktop-mode')
})
onUnmounted(() => {
  document.body.classList.remove('desktop-mode')
})

async function onLogout() {
  await authStore.logoutAction()
  window.location.href = '/login'
}
</script>

<template>
  <div class="desktop-shell">
    <!-- 顶部导航栏 -->
    <nav class="topnav">
      <div class="nav-left">
        <span class="brand">智能旅游规划</span>
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
          历史方案
        </button>
        <button
          class="navlink gallery-btn"
          title="查看效果图墙"
          @click="router.push({ name: 'gallery' })"
        >
          🎨 效果图墙
        </button>
        <button
          v-if="authStore.isAuthenticated"
          class="navlink logout-btn"
          data-testid="logout-btn"
          @click="onLogout"
        >
          退出登录
        </button>
      </div>
    </nav>

    <!-- 主内容区：优先使用 desktop 组件 -->
    <main class="main-content">
      <component :is="CurrentDesktopComponent" v-if="CurrentDesktopComponent" />
      <RouterView v-else />
    </main>
  </div>
</template>

<style scoped>
.desktop-shell {
  min-height: 100vh;
  background: #f4f4f5;
}

.topnav {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 40px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid #e8e8e8;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
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

.brand {
  font-weight: 800;
  font-size: 18px;
  color: #2b2b2b;
}

.brand b {
  color: #ff2442;
}

.navlink {
  font-size: 14px;
  color: #ff2442;
  font-weight: 600;
  text-decoration: none;
  background: #ffe9ed;
  padding: 8px 16px;
  border-radius: 20px;
  transition: all 0.2s ease;
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
  font-size: 14px;
  padding: 8px 16px;
  border-radius: 20px;
  transition: all 0.2s ease;
}

.logout-btn:hover {
  background: #e8e8e8;
  color: #333;
}

.main-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 40px 20px;
}
</style>
