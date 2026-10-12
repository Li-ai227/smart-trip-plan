package com.smart.trip.server.plan.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * planId 每日序列号服务（C-017 AC-9 / ADR-012）。
 *
 * <p>「Redis 优先 + 内存兜底」策略：每次调用 Redis {@code INCR} 获取序列号；
 * Redis 不可用时退化为进程内 {@link AtomicInteger} 自增，WARN 日志但不阻塞。
 * key {@code trip:plan:seq:{yyyyMMdd}} TTL 48h 自动过期实现跨日重置。</p>
 */
public class PlanIdSequenceService {

    private static final System.Logger LOG = System.getLogger(PlanIdSequenceService.class.getName());

    private static final String KEY_PREFIX = "trip:plan:seq:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration KEY_TTL = Duration.ofHours(48);

    private final StringRedisTemplate redisTemplate;
    private final AtomicInteger memorySequence = new AtomicInteger(0);
    private final AtomicReference<LocalDate> lastDate = new AtomicReference<>(null);

    /**
     * @param redisTemplate Redis 模板
     */
    public PlanIdSequenceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取下一个序列号。
     *
     * @param date 当日日期
     * @return 当日序列号（从 1 开始）
     */
    public int nextSequence(LocalDate date) {
        resetIfDateChanged(date);
        try {
            String key = KEY_PREFIX + DATE_FMT.format(date);
            Long seq = redisTemplate.opsForValue().increment(key);
            if (seq != null) {
                if (seq == 1L) {
                    redisTemplate.expire(key, KEY_TTL);
                }
                memorySequence.set(seq.intValue());
                return seq.intValue();
            }
        } catch (Exception ex) {
            LOG.log(System.Logger.Level.WARNING,
                    "Redis 序列号获取失败，退化内存自增: date={0}, error={1}",
                    date, ex.getMessage());
        }
        return memorySequence.incrementAndGet();
    }

    private void resetIfDateChanged(LocalDate date) {
        LocalDate prev = lastDate.getAndSet(date);
        if (prev != null && !prev.equals(date)) {
            memorySequence.set(0);
        }
    }
}
