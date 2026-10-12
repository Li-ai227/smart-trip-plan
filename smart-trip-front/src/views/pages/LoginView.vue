<script setup lang="ts">
/** 页面：登录 —— 调用 POST /api/v1/auth/login，写 token 并跳转 /intent（AC-2） */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { login } from '@/services/auth'
import { useMock } from '@/config'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ username: '', password: '' })
const error = ref('')
const loading = ref(false)
const showPwd = ref(false)

const onSubmit = async () => {
  error.value = ''
  if (!form.username) { error.value = '请输入用户名'; return }
  if (!form.password) { error.value = '请输入密码'; return }

  if (useMock) {
    authStore.setTokens({ accessToken: 'mock-token', refreshToken: 'mock-refresh', expiresIn: 7200 })
    router.push('/intent')
    return
  }

  loading.value = true
  try {
    const tokens = await login({ username: form.username, password: form.password })
    authStore.setTokens(tokens)
    router.push('/intent')
  } catch (e: unknown) {
    const status = (e as { status?: number }).status
    error.value = status === 401 ? '用户名或密码错误' : ((e as Error).message || '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 左侧品牌面板 -->
    <div class="brand-panel">
      <div class="brand-inner">
        <div class="brand-logo">
          <span class="logo-icon">✈️</span>
          <span class="logo-text">智能旅游规划</span>
        </div>
        <h2 class="brand-slogan">让 AI 为你<br/>规划完美旅程</h2>
        <p class="brand-desc">小红书风格 · 智能行程规划 · 一键出发</p>

        <div class="brand-cards">
          <div class="bcard">
            <span class="bcard-icon">🗺️</span>
            <div>
              <div class="bcard-title">智能规划</div>
              <div class="bcard-sub">AI 全程陪伴，行程自动生成</div>
            </div>
          </div>
          <div class="bcard">
            <span class="bcard-icon">💰</span>
            <div>
              <div class="bcard-title">费用拆分</div>
              <div class="bcard-sub">透明预算，精准到每一天</div>
            </div>
          </div>
          <div class="bcard">
            <span class="bcard-icon">✅</span>
            <div>
              <div class="bcard-title">HITL 确认</div>
              <div class="bcard-sub">人工介入，方案由你做主</div>
            </div>
          </div>
        </div>

        <div class="brand-dots">
          <span class="dot active"></span>
          <span class="dot"></span>
          <span class="dot"></span>
        </div>
      </div>
    </div>

    <!-- 右侧表单面板 -->
    <div class="form-panel">
      <div class="form-inner">
        <!-- 移动端 Logo -->
        <div class="mobile-logo">
          <span class="logo-icon">✈️</span>
          <span class="logo-text">智能旅游规划</span>
        </div>

        <div class="form-header">
          <h1 class="form-title">欢迎回来</h1>
          <p class="form-sub">登录以继续你的旅行规划</p>
        </div>

        <form @submit.prevent="onSubmit" class="auth-form" novalidate>
          <!-- 用户名 -->
          <div class="field-wrap" :class="{ 'has-val': form.username }">
            <span class="field-icon">👤</span>
            <div class="field-body">
              <label class="float-label">用户名</label>
              <input
                v-model="form.username"
                class="line-input"
                autocomplete="username"
                data-testid="username"
                @focus="($event.target as HTMLElement).parentElement!.parentElement!.classList.add('focused')"
                @blur="($event.target as HTMLElement).parentElement!.parentElement!.classList.remove('focused')"
              />
            </div>
          </div>

          <!-- 密码 -->
          <div class="field-wrap" :class="{ 'has-val': form.password }">
            <span class="field-icon">🔒</span>
            <div class="field-body">
              <label class="float-label">密码</label>
              <div class="pwd-row">
                <input
                  v-model="form.password"
                  :type="showPwd ? 'text' : 'password'"
                  class="line-input"
                  autocomplete="current-password"
                  data-testid="password"
                  @focus="($event.target as HTMLElement).parentElement!.parentElement!.parentElement!.classList.add('focused')"
                  @blur="($event.target as HTMLElement).parentElement!.parentElement!.parentElement!.classList.remove('focused')"
                />
                <button type="button" class="eye-btn" @click="showPwd = !showPwd" tabindex="-1">
                  {{ showPwd ? '🙈' : '👁️' }}
                </button>
              </div>
            </div>
          </div>

          <!-- 错误提示 -->
          <div v-if="error" class="error-bar" role="alert" data-testid="error-msg">
            <span class="error-icon">⚠️</span>{{ error }}
          </div>

          <!-- 提交 -->
          <button
            type="submit"
            class="submit-btn"
            :class="{ loading }"
            :disabled="loading"
            data-testid="submit-btn"
            @click.prevent="onSubmit"
          >
            <span v-if="!loading">登 录</span>
            <span v-else class="loading-dots">登录中<i></i><i></i><i></i></span>
          </button>
        </form>

        <!-- 分割线 -->
        <div class="divider"><span>还没有账号？</span></div>

        <!-- 注册跳转 -->
        <router-link to="/register" class="switch-btn">立即注册，开始规划旅行</router-link>

        <!-- 协议 -->
        <p class="agreement">
          登录即代表同意
          <a href="#" @click.prevent>《用户协议》</a>和
          <a href="#" @click.prevent>《隐私政策》</a>
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 整体布局 ===== */
.login-page {
  min-height: 100vh;
  display: flex;
  background: #fff;
}

/* ===== 左侧品牌面板 ===== */
.brand-panel {
  display: none;
  width: 46%;
  background: linear-gradient(145deg, #ff2442 0%, #ff6b6b 50%, #ff9472 100%);
  position: relative;
  overflow: hidden;
}
.brand-panel::before {
  content: '';
  position: absolute;
  top: -80px;
  right: -80px;
  width: 320px;
  height: 320px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 50%;
}
.brand-panel::after {
  content: '';
  position: absolute;
  bottom: -100px;
  left: -60px;
  width: 280px;
  height: 280px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 50%;
}
@media (min-width: 768px) {
  .brand-panel { display: flex; align-items: center; justify-content: center; }
}
.brand-inner {
  padding: 48px 44px;
  position: relative;
  z-index: 1;
}
.brand-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 40px;
}
.logo-icon {
  font-size: 28px;
  filter: drop-shadow(0 2px 8px rgba(0,0,0,0.15));
}
.logo-text {
  font-size: 22px;
  font-weight: 900;
  color: #fff;
  letter-spacing: 1px;
}
.brand-slogan {
  font-size: 34px;
  font-weight: 900;
  color: #fff;
  line-height: 1.35;
  margin-bottom: 12px;
}
.brand-desc {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.75);
  margin-bottom: 40px;
  letter-spacing: 0.5px;
}
.brand-cards {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 36px;
}
.bcard {
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 14px;
  padding: 14px 16px;
  backdrop-filter: blur(6px);
}
.bcard-icon {
  font-size: 22px;
  flex-shrink: 0;
}
.bcard-title {
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 2px;
}
.bcard-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}
.brand-dots {
  display: flex;
  gap: 6px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.4);
}
.dot.active {
  background: #fff;
  width: 20px;
  border-radius: 3px;
}

/* ===== 右侧表单面板 ===== */
.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background: #fff;
}
.form-inner {
  width: 100%;
  max-width: 380px;
}

/* 移动端 Logo */
.mobile-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 36px;
}
@media (min-width: 768px) {
  .mobile-logo { display: none; }
}
.mobile-logo .logo-icon { font-size: 24px; }
.mobile-logo .logo-text {
  font-size: 18px;
  font-weight: 900;
  color: #1a1a1a;
}

/* 表单头部 */
.form-header {
  margin-bottom: 36px;
}
.form-title {
  font-size: 28px;
  font-weight: 900;
  color: #1a1a1a;
  margin-bottom: 6px;
  letter-spacing: -0.5px;
}
.form-sub {
  font-size: 14px;
  color: #999;
}

/* ===== 浮动 label 输入框 ===== */
.auth-form {
  margin-bottom: 24px;
}
.field-wrap {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 28px;
  padding-bottom: 2px;
  border-bottom: 1.5px solid #e8e8e8;
  transition: border-color 0.2s;
}
.field-wrap.focused {
  border-color: #ff2442;
}
.field-icon {
  font-size: 16px;
  padding-top: 22px;
  flex-shrink: 0;
  opacity: 0.5;
}
.field-body {
  flex: 1;
  position: relative;
  padding-top: 18px;
}
.float-label {
  position: absolute;
  top: 0;
  left: 0;
  font-size: 11px;
  color: #bbb;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.8px;
  pointer-events: none;
  transition: color 0.2s;
}
.field-wrap.focused .float-label,
.field-wrap.has-val .float-label {
  color: #ff2442;
}
.line-input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  padding: 4px 0 8px;
  font-family: inherit;
}
.line-input::placeholder {
  color: transparent;
}
.pwd-row {
  display: flex;
  align-items: center;
}
.pwd-row .line-input {
  flex: 1;
}
.eye-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 16px;
  padding: 4px;
  opacity: 0.5;
  transition: opacity 0.2s;
  line-height: 1;
}
.eye-btn:hover { opacity: 1; }

/* ===== 错误条 ===== */
.error-bar {
  display: flex;
  align-items: center;
  gap: 7px;
  background: #fff0f2;
  border: 1px solid #ffd0d8;
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 13px;
  color: #cc1133;
  margin-bottom: 20px;
}
.error-icon { font-size: 14px; }

/* ===== 提交按钮 ===== */
.submit-btn {
  width: 100%;
  height: 52px;
  background: linear-gradient(135deg, #ff2442, #ff5e75);
  color: #fff;
  border: none;
  border-radius: 26px;
  font-size: 16px;
  font-weight: 800;
  cursor: pointer;
  letter-spacing: 3px;
  box-shadow: 0 8px 24px rgba(255, 36, 66, 0.35);
  transition: transform 0.15s, box-shadow 0.15s, opacity 0.15s;
  margin-top: 8px;
}
.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(255, 36, 66, 0.4);
}
.submit-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 6px 16px rgba(255, 36, 66, 0.3);
}
.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.loading-dots i {
  display: inline-block;
  width: 4px;
  height: 4px;
  background: #fff;
  border-radius: 50%;
  margin: 0 2px;
  vertical-align: 2px;
  animation: bounce 1.2s infinite;
}
.loading-dots i:nth-child(2) { animation-delay: 0.2s; }
.loading-dots i:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
  40% { transform: scale(1.2); opacity: 1; }
}

/* ===== 分割线 / 注册 / 协议 ===== */
.divider {
  text-align: center;
  position: relative;
  margin: 28px 0 16px;
}
.divider::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: #f0f0f0;
}
.divider span {
  position: relative;
  background: #fff;
  padding: 0 14px;
  font-size: 13px;
  color: #bbb;
}
.switch-btn {
  display: block;
  text-align: center;
  color: #ff2442;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  padding: 12px;
  border: 1.5px solid #ff2442;
  border-radius: 26px;
  transition: background 0.2s;
}
.switch-btn:hover {
  background: #fff0f2;
}
.agreement {
  text-align: center;
  font-size: 11.5px;
  color: #bbb;
  margin-top: 24px;
  line-height: 1.6;
}
.agreement a {
  color: #999;
  text-decoration: none;
}
.agreement a:hover {
  color: #ff2442;
}
</style>
