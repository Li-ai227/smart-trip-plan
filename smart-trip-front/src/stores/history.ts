import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useMock } from '@/config'
import * as api from '@/services/history'
import type { PlanSummary } from '@/models/trip-plan'
import { mockHistoryItems } from '@/constants/mock'

export const useHistoryStore = defineStore('history', () => {
  const items = ref<PlanSummary[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const keyword = ref('')
  const loading = ref(false)
  const error = ref('')

  async function fetchHistory(p = 1, kw = '') {
    page.value = p
    keyword.value = kw
    error.value = ''

    if (useMock) {
      const filtered = kw
        ? mockHistoryItems.filter((i) => i.destination.includes(kw))
        : mockHistoryItems
      const start = (p - 1) * pageSize.value
      items.value = filtered.slice(start, start + pageSize.value)
      total.value = filtered.length
      return
    }

    loading.value = true
    try {
      const res = await api.getPlanHistory(p, pageSize.value, kw || undefined)
      items.value = res.items
      total.value = res.total
      page.value = res.page
    } catch (e) {
      error.value = (e as Error).message
    } finally {
      loading.value = false
    }
  }

  return { items, total, page, pageSize, keyword, loading, error, fetchHistory }
})
