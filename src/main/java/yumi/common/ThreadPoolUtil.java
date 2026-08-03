package yumi.common;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

@Slf4j
public class ThreadPoolUtil {


    public static ThreadPoolExecutor initThreadPool(int corePoolSize) {
        // 1. 核心参数定义
        int maximumPoolSize = corePoolSize * 2;    // 最大线程数
        long keepAliveTime = 1L;    // 非核心线程空闲存活时间
        TimeUnit unit = TimeUnit.SECONDS;
        // 阻塞队列：存放等待任务，这里用有界队列，防止无限堆积OOM
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(corePoolSize * 2);
        // 线程工厂：自定义线程名称，方便日志排查
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        // 拒绝策略：队列+最大线程满了之后如何处理新任务
        RejectedExecutionHandler handler = new YCallerRunsPolicy();

        // 2. 初始化线程池
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                threadFactory,
                handler
        );
        return threadPool;
    }

    public static class YCallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            log.info("YCallerRunsPolicy rejectedExecution: {}", r);
            super.rejectedExecution(r, e);
        }
    }
}
