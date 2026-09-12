package com.xspaceagi.custompage.domain.threadpool;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网页开发(custom-page)线程池参数。
 * <p>
 * 不同规格的部署机器并发承载能力差异很大，这里把容量参数外置到配置中，
 * 便于按机器规格(CPU/内存)现场调整，无需改代码重新构建。
 * <p>
 * 默认值按小规格部署(2C4G)取值。若部署在 4C8G 及以上，可适当上调 max-size。
 */
@Data
@ConfigurationProperties(prefix = "custom-page.thread-pool")
public class CustomPageThreadPoolProperties {

    /**
     * 短 IO：Flux 推送、/chat HTTP 调用等。
     * 一次 AI 对话会独占一个线程直到本轮结束，max-size 即为可并行进行的对话数。
     */
    private PoolConfig chat = new PoolConfig(8, 20, 30, 60L);

    /**
     * 长 IO：Agent 进度 SSE 订阅。
     * 一个订阅独占一个线程直到沙箱事件流结束，max-size 即为可并行的会话数。
     */
    private PoolConfig progress = new PoolConfig(8, 20, 10, 60L);

    /**
     * progress 池饱和时，排队等待入队的最长时间(秒)。
     * 用于吸收瞬时高峰；超过该时间仍无法入队才拒绝，避免调用方线程被无限阻塞。
     */
    private long progressEnqueueWaitSeconds = 5L;

    /**
     * DB 轮询回放的调度线程数。
     */
    private int schedulerSize = 2;

    /**
     * 单个线程池的容量配置。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PoolConfig {
        /** 核心线程数 */
        private int coreSize;
        /** 最大线程数：并发上限 */
        private int maxSize;
        /** 队列容量：仅用于吸收瞬时抖动，不建议配置过大 */
        private int queueCapacity;
        /** 空闲线程存活时间(秒) */
        private long keepAliveSeconds;
    }
}
