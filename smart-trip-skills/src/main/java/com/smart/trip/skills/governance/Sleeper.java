package com.smart.trip.skills.governance;

/**
 * 退避等待抽象（C-005 AC-4）。
 *
 * <p>把"如何等待退避时长"与重试编排解耦，便于单测注入记录式/空实现而不真实阻塞
 * （编码规范 §8.2 可测试性）。生产默认实现 {@link #real()} 使用 {@link Thread#sleep(long)}。</p>
 */
@FunctionalInterface
public interface Sleeper {

    /**
     * 等待指定时长。
     *
     * @param millis 等待毫秒数
     */
    void sleep(long millis);

    /**
     * 生产默认实现：基于 {@link Thread#sleep(long)}，被中断时复位中断标志并提前返回。
     *
     * @return 真实等待的 Sleeper
     */
    static Sleeper real() {
        return millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
