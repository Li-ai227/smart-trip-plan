import type { Route, TripDay } from '@/models/trip-plan'

export interface TimelineEntry {
  /** 景点 ID（如 SH027），用于支持人工删除/微调 */
  id?: string
  /** 格式如 "09:00" 或 "09:00 - 10:30" */
  time: string
  /** 抵达时间 "09:00" */
  arriveTime: string
  /** 离开时间 "10:30" */
  leaveTime?: string
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
 * 每一个卡片清晰显示：【抵达时间 - 离开时间】以及【前往下一站的乘车时长】
 * 1. 09:00 抵达景点 1（游玩 duration） -> 计算出离开时间
 * 2. 坐车 (transit.durationMinutes) -> 抵达下一站
 * 3. 严格遵循：下一站抵达时间 = 上一站离开时间 + 乘车/步行路程时间
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

  // 1. 早餐：08:00 - 08:45
  if (breakfast) {
    const transit = outgoing(breakfast.name)
    entries.push({
      time: '08:00 - 08:45',
      arriveTime: '08:00',
      leaveTime: '08:45',
      name: breakfast.name,
      kind: 'meal',
      mealMeta: `🍴 ${breakfast.recommend ?? '特色早餐'} · ¥${breakfast.perCapita}/人`,
      fallback: false,
      transit: transit,
    })
  }

  // 2. 景点与正餐的连续流推演（默认 09:00 开始抵达第1个景点）
  let currentClockMinutes = 9 * 60
  let lunchInserted = false
  let dinnerInserted = false

  const attractions = day.attractions ?? []

  for (let i = 0; i < attractions.length; i++) {
    const spot = attractions[i]

    // 检查是否在进当前景点前插入午餐：
    // 若当前时间已达或超过 11:45 且未吃午餐（且不是当天刚起步的第1站）
    if (lunch && !lunchInserted && currentClockMinutes >= 11 * 60 + 45 && i > 0) {
      const lunchArrive = currentClockMinutes
      const lunchLeave = lunchArrive + 60
      entries.push({
        time: `${formatMinutesToTime(lunchArrive)} - ${formatMinutesToTime(lunchLeave)}`,
        arriveTime: formatMinutesToTime(lunchArrive),
        leaveTime: formatMinutesToTime(lunchLeave),
        name: lunch.name,
        kind: 'meal',
        mealMeta: `🍴 ${lunch.recommend ?? '特色午餐'} · ¥${lunch.perCapita}/人`,
        fallback: false,
      })
      currentClockMinutes = lunchLeave // 午餐 60 分钟
      lunchInserted = true
    }

    // 记录当前景点：抵达时间 -> 游玩时长 -> 离开时间
    const spotArriveMinutes = currentClockMinutes
    const durationMinutes = spot.durationMinutes || 90
    const spotLeaveMinutes = spotArriveMinutes + durationMinutes
    const transit = outgoing(spot.name)

    entries.push({
      id: spot.id,
      time: `${formatMinutesToTime(spotArriveMinutes)} - ${formatMinutesToTime(spotLeaveMinutes)}`,
      arriveTime: formatMinutesToTime(spotArriveMinutes),
      leaveTime: formatMinutesToTime(spotLeaveMinutes),
      name: spot.name,
      kind: 'spot',
      rating: spot.rating,
      durationH: Math.round((durationMinutes / 60) * 10) / 10,
      ticket: spot.ticket,
      fallback: incomingFallback(spot.name),
      transit: transit,
    })

    // 游玩结束，离开景点
    currentClockMinutes = spotLeaveMinutes

    // 游玩结束后，若正好在 11:30 ~ 13:30 之间且未吃午餐，插入午餐
    if (lunch && !lunchInserted && currentClockMinutes >= 11 * 60 + 30) {
      const lunchArrive = currentClockMinutes
      const lunchLeave = lunchArrive + 60
      entries.push({
        time: `${formatMinutesToTime(lunchArrive)} - ${formatMinutesToTime(lunchLeave)}`,
        arriveTime: formatMinutesToTime(lunchArrive),
        leaveTime: formatMinutesToTime(lunchLeave),
        name: lunch.name,
        kind: 'meal',
        mealMeta: `🍴 ${lunch.recommend ?? '特色午餐'} · ¥${lunch.perCapita}/人`,
        fallback: false,
      })
      currentClockMinutes = lunchLeave // 午餐 60 分钟
      lunchInserted = true
    }

    // 加上坐车/乘车去下一站的时间（路程行驶中）
    if (transit && transit.durationMinutes) {
      currentClockMinutes += transit.durationMinutes
    }
  }

  // 若景点结束后仍未吃午餐
  if (lunch && !lunchInserted) {
    const lunchArrive = Math.max(12 * 60, currentClockMinutes)
    const lunchLeave = lunchArrive + 60
    entries.push({
      time: `${formatMinutesToTime(lunchArrive)} - ${formatMinutesToTime(lunchLeave)}`,
      arriveTime: formatMinutesToTime(lunchArrive),
      leaveTime: formatMinutesToTime(lunchLeave),
      name: lunch.name,
      kind: 'meal',
      mealMeta: `🍴 ${lunch.recommend ?? '特色午餐'} · ¥${lunch.perCapita}/人`,
      fallback: false,
    })
    currentClockMinutes = lunchLeave
    lunchInserted = true
  }

  // 3. 晚餐
  if (dinner && !dinnerInserted) {
    const dinnerArrive = Math.max(18 * 60, currentClockMinutes)
    const dinnerLeave = dinnerArrive + 60
    entries.push({
      time: `${formatMinutesToTime(dinnerArrive)} - ${formatMinutesToTime(dinnerLeave)}`,
      arriveTime: formatMinutesToTime(dinnerArrive),
      leaveTime: formatMinutesToTime(dinnerLeave),
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
