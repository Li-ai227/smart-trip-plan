import { apiPrefix, useMock } from '@/config'
import { get, post } from '@/utils/request'
import type {
  FeedbackRating,
  InterveneAction,
  TripPlanCreated,
  TripPlanDetail,
  TripPlanRequest,
} from '@/models/trip-plan'

/**
 * 行程规划对外 REST API（对齐 .harness/wiki/接口协议.md §1）
 * 真实后端为 smart-trip-server；未实现时由 store 走 Mock（见 config.useMock）。
 */

// ① 提交规划请求 —— POST /api/v1/trip-plan → 202 { planId, status }
export const submitTripPlan = (payload: TripPlanRequest) =>
  post<TripPlanCreated>('/trip-plan', { body: payload as unknown as Record<string, unknown> })

// ② 查询规划进度/结果 —— GET /api/v1/trip-plan/{planId}
export const getTripPlan = (planId: string) => get<TripPlanDetail>(`/trip-plan/${planId}`)

// ③ 人工介入（HITL）—— POST /api/v1/trip-plan/{planId}/intervene
export const intervenePlan = (
  planId: string,
  action: InterveneAction,
  changes?: Record<string, unknown>,
) => post<TripPlanDetail>(`/trip-plan/${planId}/intervene`, { body: { action, ...changes } })

// ④ 用户反馈 —— POST /api/v1/trip-plan/{planId}/feedback
export const submitFeedback = (
  planId: string,
  rating: FeedbackRating,
  modifiedItems: string[] = [],
  comment = '',
) =>
  post<{ ok: boolean }>(`/trip-plan/${planId}/feedback`, {
    body: { rating, modifiedItems, comment },
  })

// ⑤ PDF 导出 —— GET /api/v1/trip-plan/{planId}/export?format=pdf → Blob 下载
// 使用原生 fetch 以避免 request.ts 的 JSON 序列化处理二进制响应。
export async function exportPdf(planId: string): Promise<void> {
  if (useMock) {
    // 非功能环境 mock：模拟延迟后触发浏览器打印/下载
    await new Promise((r) => setTimeout(r, 800))
    window.print()
    return
  }

  const base = apiPrefix
  const url = `${base}/api/v1/trip-plan/${encodeURIComponent(planId)}/export?format=pdf`

  // 从 auth store 获取 token
  const { useAuthStore } = await import('@/stores/auth')
  const authStore = useAuthStore()
  let token: string | null = null
  if (authStore.accessToken) {
    token = await authStore.ensureSession()
  }

  const headers: Record<string, string> = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const res = await fetch(url, { headers })
  if (!res.ok) {
    let msg = `导出失败 (${res.status})`
    try {
      const errBody = await res.json()
      msg = errBody.message || msg
    } catch { /* ignore parse error */ }
    throw new Error(msg)
  }

  const blob = await res.blob()
  triggerDownload(blob, `${planId}.pdf`)
}

/** 触发浏览器文件下载。 */
function triggerDownload(blob: Blob, filename: string) {
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}
