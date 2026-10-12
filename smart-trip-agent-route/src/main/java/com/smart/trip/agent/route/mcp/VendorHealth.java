package com.smart.trip.agent.route.mcp;

import java.util.function.LongSupplier;

/**
 * 单厂商熔断状态机（C-007 增量 B / AC-9c）。
 *
 * <p>状态转移：{@code CLOSED → OPEN}（连续失败≥阈值）→ 冷却后 {@code HALF_OPEN}
 * → 探测成功 {@code CLOSED} / 失败 {@code OPEN}。可注入时钟以便测试。</p>
 */
public final class VendorHealth {

    /** 默认连续失败阈值。 */
    static final int DEFAULT_FAILURE_THRESHOLD = 3;

    /** 默认冷却时间（毫秒）。 */
    static final long DEFAULT_COOLDOWN_MILLIS = 30_000L;

    /**
     * 熔断状态。
     */
    public enum State {
        /** 正常，可接受请求。 */
        CLOSED,
        /** 不健康，拒绝请求，等待冷却。 */
        OPEN,
        /** 冷却结束，允许一次探测请求。 */
        HALF_OPEN
    }

    private final VendorId vendor;
    private final int failureThreshold;
    private final long cooldownMillis;
    private final LongSupplier clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openTimestamp;

    /**
     * @param vendor           厂商标识
     * @param failureThreshold 触发 OPEN 的连续失败次数
     * @param cooldownMillis   OPEN → HALF_OPEN 的冷却时间（毫秒）
     * @param clock            时间源
     */
    public VendorHealth(VendorId vendor, int failureThreshold, long cooldownMillis,
                        LongSupplier clock) {
        this.vendor = vendor;
        this.failureThreshold = failureThreshold;
        this.cooldownMillis = cooldownMillis;
        this.clock = clock;
    }

    /**
     * 使用默认参数。
     *
     * @param vendor 厂商标识
     */
    public VendorHealth(VendorId vendor) {
        this(vendor, DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN_MILLIS, System::currentTimeMillis);
    }

    /**
     * 当前状态是否允许发送请求。
     *
     * @return true = CLOSED 或已冷却的 HALF_OPEN
     */
    public synchronized boolean isAvailable() {
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.OPEN && clock.getAsLong() - openTimestamp >= cooldownMillis) {
            state = State.HALF_OPEN;
            return true;
        }
        return state == State.HALF_OPEN;
    }

    /**
     * 记录成功调用：重置失败计数，HALF_OPEN → CLOSED。
     */
    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        state = State.CLOSED;
    }

    /**
     * 记录失败调用：递增连续失败，达阈值时 → OPEN。
     */
    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openTimestamp = clock.getAsLong();
        }
    }

    /**
     * @return 当前熔断状态
     */
    public synchronized State state() {
        return state;
    }

    /**
     * @return 厂商标识
     */
    public VendorId vendor() {
        return vendor;
    }
}
