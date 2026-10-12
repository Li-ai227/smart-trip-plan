import { get } from '@/utils/request'
import type { PlanHistoryResponse } from '@/models/trip-plan'

/**
 * 历史方案 API（GET /api/v1/trip-plan/history）
 */
export const getPlanHistory = (page = 1, pageSize = 10, keyword?: string) =>
  get<PlanHistoryResponse>('/trip-plan/history', {
    params: { page, pageSize, ...(keyword ? { keyword } : {}) },
  })
