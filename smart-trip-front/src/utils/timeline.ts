import type { Route, TripDay } from '@/models/trip-plan'

export interface TimelineEntry {
  time: string
  name: string
  kind: 'spot' | 'meal'
  rating?: number
  durationH?: number
  ticket?: number
  mealMeta?: string
  fallback: boolean
  transit?: Route
}

/**
 * 将总分钟数转换为 "HH:mm" 格式
 */
export function formatMinutesToTime(totalMinutes: number): string {
  const normalized = Math.max(0, Math.min(24 * 60 - 1, Math.round(totalMinutes)))
  const hours = Math.floor(normalized / 60)
  const mins = normalized % 60
  return `${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}`
}

/**
 * 连续时间轴推演算法：
 * 1. 默认上午 09:00 开始游玩。如果有早餐，早餐安排在 08:00。
 * 2. 依次遍历景点与午餐、晚餐：
 *    - 景点游玩耗时按 a.durationMinutes 计算。
 *    - 到达下一站的耗时按路线 transit.durationMinutes 计算。
 *    - 当时间临近或超过 12:00 时插入午餐（通常游玩到中午接午餐，用餐 60 分钟）。
 *    - 当时间临近或超过 18:30 时插入晚餐（用餐 60 分钟）。
 * 3. 生成严格单调递增、物理连贯的真实时间线。
 */
export function calculateDynamicTimeline(day: TripDay | undefined): TimelineEntry[] {
  if (!day) return []

  const routes = day.routes ?? []
  const outgoing = (name: string) => routes.find((r) => r.fromName === name)
  const incomingFallback = (name: string) => routes.some((r) => r.toName === name && r.fallback)

  const breakfast = day.meals?.find((m) => m.mealType === '早')
  const lunch = day.meals?.find((m) => m.mealType === '午')
  const dinner = day.meals?.find((m) => m.mealType === '晚')

  const entries: TimelineEntry[] = []

  // 1. 早餐
  if (breakfast) {
    entries.push({
      time: '08:00',
      name: breakfast.name,
      kind: 'meal',
      mealMeta: `🍴 ${breakfast.recommend ?? '特色早餐'} · ¥${breakfast.perCapita}/人`,
      fallback: false,
      transit: outgoing(breakfast.name),
    })
  }

  // 2. 景点与正餐的连续流推演
  // 默认从 09:00（540分钟）开始上午行程
  let currentClockMinutes = 9 * 60
  let lunchInserted = false
  let dinnerInserted = false

  const attractions = day.attractions ?? []

  for (let i = 0; i < attractions.length; i++) {
    const spot = attractions[i]

    // 检查是否在进当前景点前插入午餐：
    // 如果还没吃午餐，且当前时间已经到达或超过 11:45，并且不是今天刚开始的第1个景点
    if (lunch && !lunchInserted && currentClockMinutes >= 11 * 60 + 45 && i > 0) {
      entries.push({
        time: formatMinutesToTime(currentClockMinutes),
        name: lunch.name,
        kind: 'meal',
        mealMeta: `🍴 ${lunch.recommend ?? '特色午餐'} · ¥${lunch.perCapita}/人`,
        fallback: false,
      })
      currentClockMinutes += 60 // 午餐 1 小时
      lunchInserted = true
    }

    // 记录到达当前景点的时间
    const spotArrivalMinutes = currentClockMinutes
    const durationMinutes = spot.durationMinutes || 90
    const transit = outgoing(spot.name)

    entries.push({
      time: formatMinutesToTime(spotArrivalMinutes),
      name: spot.name,
      kind: 'spot',
      rating: spot.rating,
      durationH: Math.round((durationMinutes / 60) * 10) / 10,
      ticket: spot.ticket,
      fallback: incomingFallback(spot.name),
      transit: transit,
    })

    // 游玩后时间累加
    currentClockMinutes += durationMinutes

    // 游玩结束后，如果刚好在 11:30 ~ 13:30 之间且未吃午餐，插入午餐
    if (lunch && !lunchInserted && currentClockMinutes >= 11 * 60 + 30) {
      entries.push({
        time: formatMinutesToTime(currentClockMinutes),
        name: lunch.name,
        kind: 'meal',
        mealMeta: `🍴 ${lunch.recommend ?? '特色午餐'} · ¥${lunch.perCapita}/人`,
        fallback: false,
      })
      currentClockMinutes += 60 // 午餐 1 小时
      lunchInserted = true
    }

    // 加上去往下一站的交通时间
    if (transit && transit.durationMinutes) {
      currentClockMinutes += transit.durationMinutes
    }
  }

  // 如果景点全部结束还未插入午餐（如景点极少），在最后插入
  if (lunch && !lunchInserted) {
    const lunchTime = Math.max(12 * 60, currentClockMinutes)
    entries.push({
      time: formatMinutesToTime(lunchTime),
      name: lunch.name,
      kind: 'meal',
      mealMeta: `🍴 ${lunch.recommend ?? '特色午餐'} · ¥${lunch.perCapita}/人`,
      fallback: false,
    })
    currentClockMinutes = lunchTime + 60
    lunchInserted = true
  }

  // 3. 晚餐（在所有游玩或下午景点结束后插入）
  if (dinner && !dinnerInserted) {
    const dinnerTime = Math.max(18 * 60, currentClockMinutes)
    entries.push({
      time: formatMinutesToTime(dinnerTime),
      name: dinner.name,
      kind: 'meal',
      mealMeta: `🍴 ${dinner.recommend ?? '特色晚餐'} · ¥${dinner.perCapita}/人`,
      fallback: false,
      transit: outgoing(dinner.name),
    })
    dinnerInserted = true
  }

  return entries
}
