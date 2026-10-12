/**
 * 认证领域模型 —— 字段对齐 .harness/wiki/接口协议.md §1.6
 */

// POST /api/v1/auth/register 请求体
export interface RegisterReq {
  username: string
  password: string
  nickname?: string
}

// POST /api/v1/auth/register 响应（201 Created）
export interface RegisterResp {
  userId: number
  username: string
}

// POST /api/v1/auth/login 请求体
export interface LoginReq {
  username: string
  password: string
}

// POST /api/v1/auth/login 响应（200 OK）
export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

// POST /api/v1/auth/refresh 响应（200 OK）
export interface RefreshResp {
  accessToken: string
  expiresIn: number
}

// 前端认证用户状态（派生自 login 响应 + 本地持久化）
export interface AuthUser {
  accessToken: string
  refreshToken: string
  expiresAt: number // Unix ms, computed from Date.now() + expiresIn * 1000
}
