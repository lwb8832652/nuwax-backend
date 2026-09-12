package com.xspaceagi.custompage.domain.threadpool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xspaceagi.custompage.domain.threadpool.CustomPageThreadPoolProperties.PoolConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * 用户页面项目的线程池配置
 * <p>
 * 容量参数全部外置到 {@link CustomPageThreadPoolProperties}，可通过
 * {@code custom-page.thread-pool.*} 按部署机器规格调整，无需改代码重新构建。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({CustomPageThreadPoolProperties.class, CustomPageSseProperties.class})
public class CustomPageAsyncConfig {

    private final CustomPageThreadPoolProperties threadPoolProperties;

    /**
     * 短 IO：Flux 推送、/chat HTTP 调用等，避免被长连接占满。
     * <p>
     * 一次 AI 对话会独占一个线程直到本轮结束，max-size 即为可并行进行的对话数。
     * 队列仅用于吸收瞬时抖动，不做深度堆积，避免内存与上下文切换开销。
     */
    @Bean("aiChatExecutor")
    public Executor aiChatExecutor() {
        PoolConfig cfg = threadPoolProperties.getChat();
        log.info("[ThreadPool] init aiChatExecutor, core={}, max={}, queue={}",
                cfg.getCoreSize(), cfg.getMaxSize(), cfg.getQueueCapacity());
        return new ThreadPoolExecutor(
                cfg.getCoreSize(),
                cfg.getMaxSize(),
                cfg.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(cfg.getQueueCapacity()),
                namedThreadFactory("ai-chat-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 长 IO：Agent 进度 SSE 订阅（阻塞读至流结束）。
     * <p>
     * 注意：这里<b>不能</b>使用 {@link ThreadPoolExecutor.CallerRunsPolicy}。该池执行的是对沙箱 SSE 的
     * 阻塞读（readTimeout=0，可能长时间不返回），CallerRuns 会把任务回退到调用方(Tomcat 工作线程)执行，
     * 一旦饱和将逐个占死 Web 容器线程，最终导致整个服务无响应。这里改为"有限等待 + 快速失败"：
     * 瞬时高峰被短暂排队吸收，真·过载时快速失败，保证服务主体可用。
     */
    @Bean("aiAgentProgressExecutor")
    public Executor aiAgentProgressExecutor() {
        PoolConfig cfg = threadPoolProperties.getProgress();
        log.info("[ThreadPool] init aiAgentProgressExecutor, core={}, max={}, queue={}, enqueue Wait={}s",
                cfg.getCoreSize(), cfg.getMaxSize(), cfg.getQueueCapacity(),
                threadPoolProperties.getProgressEnqueueWaitSeconds());
        return new ThreadPoolExecutor(
                cfg.getCoreSize(),
                cfg.getMaxSize(),
                cfg.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(cfg.getQueueCapacity()),
                namedThreadFactory("ai-agent-progress-"),
                boundedWaitRejectedExecutionHandler("ai-agent-progress",
                        threadPoolProperties.getProgressEnqueueWaitSeconds()));
    }

    /**
     * 饱和时先在队列外有限等待，等待超时后才拒绝。
     * <p>
     * 相比 CallerRunsPolicy：不会把长阻塞任务回退到调用方线程；
     * 相比 AbortPolicy：瞬时高峰可被短暂等待吸收，减少无谓失败。
     */
    private static RejectedExecutionHandler boundedWaitRejectedExecutionHandler(String poolName, long waitSeconds) {
        return (Runnable task, ThreadPoolExecutor executor) -> {
            if (!executor.isShutdown()) {
                BlockingQueue<Runnable> queue = executor.getQueue();
                try {
                    if (queue.offer(task, waitSeconds, TimeUnit.SECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.warn("[ThreadPool] task rejected, pool={}, pool Size={}, active={}, queued={}",
                    poolName, executor.getPoolSize(), executor.getActiveCount(), executor.getQueue().size());
            throw new RejectedExecutionException(poolName + " is busy, please retry later");
        };
    }

    /**
     * DB 轮询回放：定时任务，不占用 aiChatExecutor 工作线程 sleep。
     */
    @Bean(name = "aiAgentProgressScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService aiAgentProgressScheduler() {
        return Executors.newScheduledThreadPool(threadPoolProperties.getSchedulerSize(),
                namedThreadFactory("ai-agent-progress-sched-"));
    }

    private static ThreadFactory namedThreadFactory(String namePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
    }
}
