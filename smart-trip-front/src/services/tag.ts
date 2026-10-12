/**
 * 标签 API 服务（C-014）
 * @description 提供标签查询接口调用
 */

import { mockTags } from '@/constants/mock'
import { useMock } from '@/config'

/** 内存缓存：防止多次组件挂载时重复请求 API */
let cachedTags: string[] | null = null
let fetchPromise: Promise<string[]> | null = null

/**
 * 获取所有可用标签列表（Top 30，按频次降序）。
 * 内置缓存 + 请求去重：同一时刻多次调用只发一次网络请求。
 *
 * @returns Promise<string[]> 标签列表
 */
export async function fetchTags(): Promise<string[]> {
  // 已有缓存直接返回
  if (cachedTags !== null) {
    return cachedTags
  }
  // 正在请求中去重
  if (fetchPromise !== null) {
    return fetchPromise
  }

  if (useMock) {
    fetchPromise = new Promise((resolve) => {
      setTimeout(() => {
        cachedTags = mockTags
        resolve(mockTags)
      }, 200)
    })
    return fetchPromise
  }

  // 真实 API 模式
  fetchPromise = (async () => {
    try {
      const res = await fetch('/api/v1/trip-plan/tags')
      if (!res.ok) {
        throw new Error(`HTTP error! status: ${res.status}`)
      }
      const data = await res.json()
      cachedTags = data.tags || []
      return cachedTags
    } catch (error) {
      console.error('Failed to fetch tags:', error)
      return []
    } finally {
      fetchPromise = null
    }
  })()

  return fetchPromise
}
