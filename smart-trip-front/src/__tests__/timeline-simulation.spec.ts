import { describe, it, expect } from 'vitest'
import { calculateDynamicTimeline, formatMinutesToTime } from '@/utils/timeline'
import type { TripDay } from '@/models/trip-plan'

describe('calculateDynamicTimeline 真实时间链推演算法', () => {
  it('formatMinutesToTime 能够正确格式化时间字符串', () => {
    expect(formatMinutesToTime(540)).toBe('09:00')
    expect(formatMinutesToTime(720)).toBe('12:00')
    expect(formatMinutesToTime(1020)).toBe('17:00')
    expect(formatMinutesToTime(1140)).toBe('19:00')
  })

  it('多景点与交通时间应该严格连续累加，彻底杜绝时间倒流与重叠', () => {
    // 模拟用户截图中的经典真实场景：
    // 四行仓库（90分钟） -> 驾车84分钟 -> 朱家角（180分钟） -> 驾车80分钟 -> 豫园（90分钟）
    const testDay: TripDay = {
      date: '2026-10-17',
      index: 4,
      cityLabel: 'Day4 上海',
      dailyCost: 1310,
      attractions: [
        { id: 'SH027', name: '四行仓库纪念馆', rating: 4.5, durationMinutes: 90, ticket: 0 },
        { id: 'SH013', name: '朱家角古镇', rating: 4.5, durationMinutes: 180, ticket: 60 },
        { id: 'SH003', name: '豫园', rating: 4.5, durationMinutes: 90, ticket: 40 },
        { id: 'SH030', name: '上海当代艺术博物馆', rating: 4.4, durationMinutes: 120, ticket: 100 },
      ],
      meals: [
        { name: '上海·午餐·特色餐厅', mealType: '午', perCapita: 60, recommend: '小笼包' },
        { name: '上海·晚餐·特色餐厅', mealType: '晚', perCapita: 60, recommend: '本帮菜' },
      ],
      routes: [
        { fromName: '四行仓库纪念馆', toName: '朱家角古镇', distanceKm: 66.8, durationMinutes: 84, mode: '驾车', fallback: false },
        { fromName: '朱家角古镇', toName: '豫园', distanceKm: 72.8, durationMinutes: 80, mode: '驾车', fallback: false },
        { fromName: '豫园', toName: '上海当代艺术博物馆', distanceKm: 10.8, durationMinutes: 19, mode: '驾车', fallback: false },
      ],
    }

    const entries = calculateDynamicTimeline(testDay)

    expect(entries.length).toBe(6) // 4景点 + 2正餐

    // 1. 第1站：09:00 - 10:30 开始
    expect(entries[0].name).toBe('四行仓库纪念馆')
    expect(entries[0].arriveTime).toBe('09:00')
    expect(entries[0].leaveTime).toBe('10:30')
    expect(entries[0].time).toBe('09:00 - 10:30')

    // 四行仓库玩90分钟到 10:30，路上开车84分钟到 11:54
    // 恰逢中午，插入午餐：11:54 - 12:54
    const lunchEntry = entries.find((e) => e.kind === 'meal' && e.name.includes('午餐'))
    expect(lunchEntry).toBeDefined()
    expect(lunchEntry!.arriveTime).toBe('11:54')
    expect(lunchEntry!.leaveTime).toBe('12:54')

    // 午餐 60 分钟后（12:54）到达朱家角，玩 180 分钟（3小时）到 15:54
    const zhujiajiao = entries.find((e) => e.name === '朱家角古镇')
    expect(zhujiajiao).toBeDefined()
    expect(zhujiajiao!.arriveTime).toBe('12:54')
    expect(zhujiajiao!.leaveTime).toBe('15:54')

    // 朱家角玩 180 分钟（3小时）到 15:54，开车 80 分钟（1小时20分）到 17:14
    // 豫园到达时间绝对不能是 16:00，必须顺延到 17:14 之后！
    const yuyuan = entries.find((e) => e.name === '豫园')
    expect(yuyuan).toBeDefined()
    expect(yuyuan!.arriveTime).toBe('17:14')
    expect(yuyuan!.leaveTime).toBe('18:44')

    // 检查所有节点时间单调非递减
    for (let i = 1; i < entries.length; i++) {
      expect(entries[i].arriveTime >= entries[i - 1].leaveTime!).toBe(true)
    }
  })
})
