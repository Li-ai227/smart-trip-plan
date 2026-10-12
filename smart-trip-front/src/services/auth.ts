/**
 * 认证服务层 —— 对齐 .harness/wiki/接口协议.md §1.6
 * 真实 HTTP 调用；Mock 模式下由 useAuthStore 短路。
 */
import { post } from '@/utils/request'
import type { AuthTokens, LoginReq, RefreshResp, RegisterReq, RegisterResp } from '@/models/auth'

// POST /api/v1/auth/register → 201 { userId, username }
export const register = (payload: RegisterReq) =>
  post<RegisterResp>('/auth/register', { body: payload as unknown as Record<string, unknown> })

// POST /api/v1/auth/login → 200 { accessToken, refreshToken, expiresIn }
export const login = (payload: LoginReq) =>
  post<AuthTokens>('/auth/login', { body: payload as unknown as Record<string, unknown> })

// POST /api/v1/auth/logout（携带有效 accessToken，服务端吊销）
export const logout = () => post<{ message: string }>('/auth/logout', {})

// POST /api/v1/auth/refresh → 200 { accessToken, expiresIn }
export const refresh = (refreshToken: string) =>
  post<RefreshResp>('/auth/refresh', { body: { refreshToken } as Record<string, unknown> })
