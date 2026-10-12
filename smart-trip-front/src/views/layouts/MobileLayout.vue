<script setup lang="ts">
/**
 * 移动端布局
 * @description 手机模拟器模式，保留原有 PhoneFrame 封装
 */
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DeviceSwitcher from '@/components/common/DeviceSwitcher.vue'

const authStore = useAuthStore()
const router = useRouter()

// 设置 body 类名
onMounted(() => {
  document.body.classList.remove('desktop-mode')
  document.body.classList.add('mobile-mode')
})
onUnmounted(() => {
  document.body.classList.remove('mobile-mode')
})

async function onLogout() {
  await authStore.logoutAction()
  window.location.href = '/login'
}
</script>

<template>
  <div class="mobile-shell">
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
          title="历史方案"
          @click="router.push({ name: 'history' })"
        >
          历史
        </button>
        <button
          class="navlink gallery-btn"
          title="查看效果图墙"
          @click="router.push({ name: 'gallery' })"
        >
          🎨 图墙
        </button>
        <button
          v-if="authStore.isAuthenticated"
          class="navlink logout-btn"
          data-testid="logout-btn"
          @click="onLogout"
        >
          退出
        </button>
      </div>
    </nav>

    <!-- 主内容区：手机模拟器 -->
    <main class="main-content">
      <div class="stage">
        <RouterView v-slot="{ Component }">
          <component :is="Component" />
        </RouterView>
      </div>
    </main>
  </div>
</template>

<style scoped>
.mobile-shell {
  min-height: 100vh;
  background: #f4f4f5;
}

.topnav {
  position: sticky;
  top: 0;
  z-index: 100;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
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
  gap: 6px;
}

.brand {
  font-weight: 800;
  font-size: 15px;
  color: #2b2b2b;
}

.brand b {
  color: #ff2442;
}

.navlink {
  font-size: 12px;
  color: #ff2442;
  font-weight: 600;
  text-decoration: none;
  background: #ffe9ed;
  padding: 6px 12px;
  border-radius: 16px;
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
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 16px;
}

.main-content {
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
