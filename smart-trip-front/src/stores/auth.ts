/**
 * useAuthStore —— token 生命周期管理（AC-2/AC-3/AC-5）
 * 持久化: localStorage keys = auth_access / auth_refresh / auth_expires_at
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AuthTokens, AuthUser } from '@/models/auth'

const LS_ACCESS = 'auth_access'
const LS_REFRESH = 'auth_refresh'
const LS_EXPIRES = 'auth_expires_at'

/** refresh 阈值：距过期不足 5 分钟时提前刷新（对齐 request.ts REFRESH_THRESHOLD_MS） */
const REFRESH_THRESHOLD_MS = 5 * 60 * 1000

function readLS(): Partial<AuthUser> {
  try {
    const accessToken = localStorage.getItem(LS_ACCESS) ?? ''
    const refreshToken = localStorage.getItem(LS_REFRESH) ?? ''
    const expiresAt = Number(localStorage.getItem(LS_EXPIRES) ?? 0)
    return { accessToken, refreshToken, expiresAt }
  } catch {
    return {}
  }
}

export const useAuthStore = defineStore('auth', () => {
  const saved = readLS()
  const accessToken = ref<string>(saved.accessToken ?? '')
  const refreshToken = ref<string>(saved.refreshToken ?? '')
  const expiresAt = ref<number>(saved.expiresAt ?? 0)

  /** 并发 refresh 锁：避免路由守卫与请求拦截器同时触发 refresh */
  let _sessionPromise: Promise<string | null> | null = null

  // isAuthenticated: 有 accessToken 且未过期（或 expiresAt 未知则信任 token 存在）
  const isAuthenticated = computed<boolean>(
    () => !!accessToken.value && (expiresAt.value === 0 || Date.now() < expiresAt.value),
  )

  // 写入 token（登录成功 / refresh 成功后调用）
  function setTokens(tokens: AuthTokens | { accessToken: string; expiresIn: number }) {
    accessToken.value = tokens.accessToken
    expiresAt.value = Date.now() + tokens.expiresIn * 1000
    if ('refreshToken' in tokens) {
      refreshToken.value = tokens.refreshToken
      try { localStorage.setItem(LS_REFRESH, tokens.refreshToken) } catch { /* ignore */ }
    }
    try {
      localStorage.setItem(LS_ACCESS, tokens.accessToken)
      localStorage.setItem(LS_EXPIRES, String(expiresAt.value))
    } catch { /* ignore */ }
  }

  // 清除所有 token（登出 / refresh 失败）
  function clearTokens() {
    accessToken.value = ''
    refreshToken.value = ''
    expiresAt.value = 0
    try {
      localStorage.removeItem(LS_ACCESS)
      localStorage.removeItem(LS_REFRESH)
      localStorage.removeItem(LS_EXPIRES)
    } catch { /* ignore */ }
  }

  /** 执行一次 refresh 调用并更新 token */
  async function doRefreshCall(): Promise<string | null> {
    if (!refreshToken.value) return null
    try {
      const { refresh } = await import('@/services/auth')
      const resp = await refresh(refreshToken.value)
      setTokens({ accessToken: resp.accessToken, expiresIn: resp.expiresIn })
      return resp.accessToken
    } catch {
      clearTokens()
      return null
    }
  }

  /**
   * 确保当前会话持有有效 access token。
   * - token 有效 → 直接返回 accessToken
   * - token 临近/已过期但有 refreshToken → 刷新后返回新 token
   * - refresh 失败 / 无 refreshToken → 清除状态并返回 null
   *
   * @param force 为 true 时跳过有效性检查，强制刷新（供 401 重试用）
   */
  async function ensureSession(force = false): Promise<string | null> {
    if (!force && accessToken.value) {
      // expiresAt === 0 时信任 token（与 isAuthenticated 一致，防止无谓 refresh）
      if (expiresAt.value === 0) return accessToken.value
      // 未临近过期 → 直接返回
      if (Date.now() < expiresAt.value - REFRESH_THRESHOLD_MS) return accessToken.value
    }
    // 已有在途 refresh → 复用
    if (_sessionPromise) return _sessionPromise
    // 在 refresh 前快照当前 token（doRefreshCall 内 clearTokens 会清空 ref）
    const snapshotToken = accessToken.value
    const snapshotExpiry = expiresAt.value
    // 发起 refresh
    _sessionPromise = doRefreshCall().finally(() => { _sessionPromise = null })
    const refreshed = await _sessionPromise
    // refresh 失败但 token 尚未过期 → 恢复快照、降级使用，防止因 refresh 抖动误踢用户
    if (!refreshed && !force && snapshotToken
        && snapshotExpiry > 0 && Date.now() < snapshotExpiry) {
      accessToken.value = snapshotToken
      expiresAt.value = snapshotExpiry
      try {
        localStorage.setItem(LS_ACCESS, snapshotToken)
        localStorage.setItem(LS_EXPIRES, String(snapshotExpiry))
      } catch { /* ignore */ }
      return snapshotToken
    }
    return refreshed
  }

  // 登出 action（AC-5）：先主动吊销服务端 token，再清本地状态
  async function logoutAction() {
    try {
      const { logout } = await import('@/services/auth')
      await logout()
    } catch { /* token 可能已过期，服务端调用失败不影响本地清除 */ }
    clearTokens()
  }

  return {
    accessToken,
    refreshToken,
    expiresAt,
    isAuthenticated,
    setTokens,
    clearTokens,
    ensureSession,
    logoutAction,
  }
})
