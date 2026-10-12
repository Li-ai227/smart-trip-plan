import { apiPrefix, useMock } from '@/config'

// 请求超时时间（毫秒），对齐外部调用 30s 约束的客户端侧上限
const TIME_OUT = 30000

// Auth 路由无需注入 token，也不触发 refresh 重试（防死循环）
const AUTH_PATHS = ['/auth/login', '/auth/register', '/auth/logout', '/auth/refresh']

export type FetchOptionType = Omit<RequestInit, 'body'> & {
  params?: Record<string, string | number | boolean | null | undefined>
  body?: Record<string, unknown> | null
  _retry?: boolean
}

async function getAuthStore() {
  const { useAuthStore } = await import('@/stores/auth')
  return useAuthStore()
}

/**
 * 确保请求携带有效 token：委托 auth store 的 ensureSession（与路由守卫共享 refresh 锁）。
 * 401 重试时传 force=true 强制刷新。
 */
async function ensureFreshToken(force = false): Promise<string | null> {
  const store = await getAuthStore()
  if (!store.accessToken && !store.refreshToken) return null
  return store.ensureSession(force)
}

// 通用请求方法：拼前缀、处理 GET 查询参数、JSON 序列化、超时控制
const baseFetch = async <T>(url: string, fetchOptions: FetchOptionType): Promise<T> => {
  const options: FetchOptionType = { method: 'GET', mode: 'cors', ...fetchOptions }
  const headers = new Headers(options.headers)

  const path = url.startsWith('/') ? url : `/${url}`
  let fullUrl = `${apiPrefix}/api/v1${path}`
  const { method, params, body } = options

  if (method === 'GET' && params) {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v != null) search.append(k, String(v))
    })
    const qs = search.toString()
    if (qs) fullUrl += (fullUrl.includes('?') ? '&' : '?') + qs
    delete options.params
  }

  const isAuthPath = AUTH_PATHS.some((p) => path.startsWith(p))

  // Mock 模式或 auth 路由下跳过 token 注入（AC-3 设计约束）
  if (!useMock && !isAuthPath) {
    const token = await ensureFreshToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
  }

  const init: RequestInit = { method: options.method, mode: options.mode, headers }
  if (body && typeof body === 'object') {
    headers.set('Content-Type', 'application/json')
    init.body = JSON.stringify(body)
  }

  const doFetch = globalThis.fetch(fullUrl, init).then(async (res) => {
    const payload = (await res.json()) as T & { code?: string; message?: string; traceId?: string }

    // 只对非 auth 路由做 401 → refresh → retry（防止 auth 路由死循环）
    if (res.status === 401 && !options._retry && !useMock && !isAuthPath) {
      try {
        const newToken = await ensureFreshToken(true)
        if (!newToken) {
          const { useAuthStore } = await import('@/stores/auth')
          useAuthStore().clearTokens()
          if (typeof window !== 'undefined') window.location.href = '/login'
          throw new Error('登录已过期，请重新登录')
        }
        return baseFetch<T>(url, {
          ...fetchOptions,
          _retry: true,
          headers: { ...Object.fromEntries(headers.entries()), Authorization: `Bearer ${newToken}` },
        })
      } catch (e) {
        if ((e as Error).message === '登录已过期，请重新登录') throw e
        const { useAuthStore } = await import('@/stores/auth')
        useAuthStore().clearTokens()
        if (typeof window !== 'undefined') window.location.href = '/login'
        throw new Error('登录已过期，请重新登录')
      }
    }

    if (!res.ok) {
      const msg = payload.message || `HTTP ${res.status}`
      const err = Object.assign(new Error(msg), { traceId: payload.traceId, status: res.status })
      throw err
    }
    return payload as T
  })

  return Promise.race([
    new Promise<T>((_, reject) => {
      setTimeout(() => reject(new Error('接口请求超时')), TIME_OUT)
    }),
    doFetch,
  ])
}

export const get = <T>(url: string, options: FetchOptionType = {}) =>
  baseFetch<T>(url, { ...options, method: 'GET' })

export const post = <T>(url: string, options: FetchOptionType = {}) =>
  baseFetch<T>(url, { ...options, method: 'POST' })

export const del = <T>(url: string, options: FetchOptionType = {}) =>
  baseFetch<T>(url, { ...options, method: 'DELETE' })

