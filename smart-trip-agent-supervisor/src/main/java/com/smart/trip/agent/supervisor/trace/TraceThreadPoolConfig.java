package com.smart.trip.agent.supervisor.trace;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 审计追踪异步写入线程池配置（C-010 trace 方案）。
 *
 * <p>有界线程池：核心 2，最大 4，keep-alive 60s，队列 1000，CallerRunsPolicy。
 * 队列满时由调用线程执行写入，确保不丢数据。</p>
 */
public final class TraceThreadPoolConfig {

    /** 核心线程数。 */
    public static final int CORE_POOL_SIZE = 2;

    /** 最大线程数。 */
    public static final int MAX_POOL_SIZE = 4;

    /** 非核心线程空闲存活时间（秒）。 */
    public static final long KEEP_ALIVE_SECONDS = 60;

    /** 有界队列容量。 */
    public static final int QUEUE_CAPACITY = 1000;

    /** 线程名前缀。 */
    public static final String THREAD_NAME_PREFIX = "trace-persistence-";

    private TraceThreadPoolConfig() {
    }

    /**
     * 创建审计追踪专用的有界线程池。
     *
     * @return ExecutorService 实例
     */
    public static ExecutorService createTraceExecutor() {
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        ThreadFactory threadFactory = new TraceThreadFactory(THREAD_NAME_PREFIX);
        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                handler);

        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * 追踪线程工厂：统一命名 + daemon 线程。
     */
    private static final class TraceThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);
        private final String prefix;

        TraceThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + counter.getAndIncrement());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, ex) ->
                    System.getLogger(TraceThreadPoolConfig.class.getName())
                            .log(System.Logger.Level.WARNING,
                                    "审计追踪线程未捕获异常: thread=" + thread.getName(), ex));
            return t;
        }
    }
}
