<script setup lang="ts">
/** 页面：注册 —— 调用 POST /api/v1/auth/register，成功后自动登录并跳转 /intent（AC-1） */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { register, login } from '@/services/auth'
import { useMock } from '@/config'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ username: '', password: '', nickname: '' })
const error = ref('')
const loading = ref(false)
const showPwd = ref(false)
const step = ref<'form' | 'success'>('form')

const validate = (): string => {
  if (!form.username.trim()) return '用户名不能为空'
  if (form.password.length < 8) return '密码长度不能少于 8 位'
  return ''
}

const onSubmit = async () => {
  error.value = ''
  const validErr = validate()
  if (validErr) { error.value = validErr; return }

  if (useMock) {
    authStore.setTokens({ accessToken: 'mock-token', refreshToken: 'mock-refresh', expiresIn: 7200 })
    router.push('/intent')
    return
  }

  loading.value = true
  try {
    await register({ username: form.username, password: form.password, nickname: form.nickname || undefined })
    const tokens = await login({ username: form.username, password: form.password })
    authStore.setTokens(tokens)
    router.push('/intent')
  } catch (e: unknown) {
    const status = (e as { status?: number }).status
    if (status === 409) {
      error.value = '用户名已存在，请更换'
    } else if (status === 400) {
      error.value = '密码格式错误（至少 8 位）'
    } else {
      error.value = (e as Error).message || '注册失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

const strengthLevel = (pwd: string): number => {
  if (!pwd) return 0
  let score = 0
  if (pwd.length >= 8) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/[0-9]/.test(pwd)) score++
  if (/[^A-Za-z0-9]/.test(pwd)) score++
  return score
}
const strength = () => strengthLevel(form.password)
const strengthLabel = () => ['', '弱', '中', '强', '很强'][strength()]
const strengthColor = () => ['', '#ff5e5e', '#ffaa00', '#4cd964', '#16c784'][strength()]
</script>

<template>
  <div class="register-page">
    <!-- 左侧品牌面板 -->
    <div class="brand-panel">
      <div class="brand-inner">
        <div class="brand-logo">
          <span class="logo-icon">✈️</span>
          <span class="logo-text">智能旅游规划</span>
        </div>
        <h2 class="brand-slogan">开启属于你的<br/>旅行故事</h2>
        <p class="brand-desc">注册即可体验 AI 全程规划服务</p>

        <div class="steps">
          <div class="step">
            <div class="step-num">01</div>
            <div class="step-info">
              <div class="step-title">填写需求</div>
              <div class="step-sub">告诉 AI 你的目的地、预算、人数</div>
            </div>
          </div>
          <div class="step-line"></div>
          <div class="step">
            <div class="step-num">02</div>
            <div class="step-info">
              <div class="step-title">AI 规划中</div>
              <div class="step-sub">多 Agent 协同生成逐日行程</div>
            </div>
          </div>
          <div class="step-line"></div>
          <div class="step">
            <div class="step-num">03</div>
            <div class="step-info">
              <div class="step-title">确认出发</div>
              <div class="step-sub">人工确认，方案按你的想法调整</div>
            </div>
          </div>
        </div>

        <div class="brand-tag">
          <span class="tag">🤖 AI 驱动</span>
          <span class="tag">📍 精准规划</span>
          <span class="tag">💡 HITL 介入</span>
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
          <h1 class="form-title">创建账号</h1>
          <p class="form-sub">开启智能规划之旅</p>
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

          <!-- 昵称（可选） -->
          <div class="field-wrap" :class="{ 'has-val': form.nickname }">
            <span class="field-icon">😊</span>
            <div class="field-body">
              <label class="float-label">昵称（可选）</label>
              <input
                v-model="form.nickname"
                class="line-input"
                autocomplete="nickname"
                data-testid="nickname"
                @focus="($event.target as HTMLElement).parentElement!.parentElement!.classList.add('focused')"
                @blur="($event.target as HTMLElement).parentElement!.parentElement!.classList.remove('focused')"
              />
            </div>
          </div>

          <!-- 密码 -->
          <div class="field-wrap" :class="{ 'has-val': form.password }">
            <span class="field-icon">🔒</span>
            <div class="field-body">
              <label class="float-label">密码（至少 8 位）</label>
              <div class="pwd-row">
                <input
                  v-model="form.password"
                  :type="showPwd ? 'text' : 'password'"
                  class="line-input"
                  autocomplete="new-password"
                  data-testid="password"
                  @focus="($event.target as HTMLElement).parentElement!.parentElement!.parentElement!.classList.add('focused')"
                  @blur="($event.target as HTMLElement).parentElement!.parentElement!.parentElement!.classList.remove('focused')"
                />
                <button type="button" class="eye-btn" @click="showPwd = !showPwd" tabindex="-1">
                  {{ showPwd ? '🙈' : '👁️' }}
                </button>
              </div>
              <!-- 密码强度条 -->
              <div v-if="form.password" class="strength-row">
                <div class="strength-bar">
                  <div
                    class="strength-fill"
                    :style="{ width: strength() * 25 + '%', background: strengthColor() }"
                  ></div>
                </div>
                <span class="strength-label" :style="{ color: strengthColor() }">
                  {{ strengthLabel() }}
                </span>
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
            <span v-if="!loading">立即注册</span>
            <span v-else class="loading-dots">注册中<i></i><i></i><i></i></span>
          </button>
        </form>

        <!-- 分割线 -->
        <div class="divider"><span>已有账号？</span></div>

        <!-- 登录跳转 -->
        <router-link to="/login" class="switch-btn">返回登录</router-link>

        <!-- 协议 -->
        <p class="agreement">
          注册即代表同意
          <a href="#" @click.prevent>《用户协议》</a>和
          <a href="#" @click.prevent>《隐私政策》</a>
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 整体布局 ===== */
.register-page {
  min-height: 100vh;
  display: flex;
  background: #fff;
}

/* ===== 左侧品牌面板 ===== */
.brand-panel {
  display: none;
  width: 46%;
  background: linear-gradient(145deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  position: relative;
  overflow: hidden;
}
.brand-panel::before {
  content: '';
  position: absolute;
  top: -60px;
  right: -60px;
  width: 300px;
  height: 300px;
  background: rgba(255, 36, 66, 0.12);
  border-radius: 50%;
}
.brand-panel::after {
  content: '';
  position: absolute;
  bottom: -80px;
  left: -40px;
  width: 260px;
  height: 260px;
  background: rgba(255, 36, 66, 0.07);
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
.logo-icon { font-size: 28px; }
.logo-text {
  font-size: 22px;
  font-weight: 900;
  color: #fff;
  letter-spacing: 1px;
}
.brand-slogan {
  font-size: 32px;
  font-weight: 900;
  color: #fff;
  line-height: 1.38;
  margin-bottom: 12px;
}
.brand-slogan em {
  color: #ff2442;
  font-style: normal;
}
.brand-desc {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 44px;
}

/* 步骤列表 */
.steps {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-bottom: 36px;
}
.step {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}
.step-line {
  width: 2px;
  height: 20px;
  background: rgba(255, 36, 66, 0.35);
  margin-left: 19px;
}
.step-num {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(255, 36, 66, 0.25);
  border: 1.5px solid rgba(255, 36, 66, 0.5);
  color: #ff7080;
  font-size: 13px;
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.step-info {
  padding-top: 8px;
}
.step-title {
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 2px;
}
.step-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.5;
}
.brand-tag {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.tag {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 20px;
  padding: 6px 13px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.75);
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

.mobile-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 32px;
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

.form-header {
  margin-bottom: 32px;
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
  opacity: 0.45;
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
.line-input::placeholder { color: transparent; }
.pwd-row {
  display: flex;
  align-items: center;
}
.pwd-row .line-input { flex: 1; }
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

/* 密码强度 */
.strength-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.strength-bar {
  flex: 1;
  height: 3px;
  background: #f0f0f0;
  border-radius: 2px;
  overflow: hidden;
}
.strength-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s, background 0.3s;
}
.strength-label {
  font-size: 11px;
  font-weight: 700;
  min-width: 24px;
  text-align: right;
  transition: color 0.3s;
}

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
  letter-spacing: 2px;
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

/* ===== 分割线 / 登录 / 协议 ===== */
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
  color: #666;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  padding: 12px;
  border: 1.5px solid #e8e8e8;
  border-radius: 26px;
  transition: border-color 0.2s, color 0.2s;
}
.switch-btn:hover {
  border-color: #ff2442;
  color: #ff2442;
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
