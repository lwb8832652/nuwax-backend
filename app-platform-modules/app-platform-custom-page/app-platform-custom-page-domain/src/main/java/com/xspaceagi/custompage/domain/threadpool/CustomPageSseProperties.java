package com.xspaceagi.custompage.domain.threadpool;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 网页开发(custom-page) 沙箱 SSE 订阅的超时与回收参数。
 * <p>
 * 订阅任务会独占一个线程直到沙箱事件流结束。若对端僵死或前端已关闭页面而订阅仍在继续，
 * 线程将被长期占用并最终耗尽线程池，因此这里提供两组兜底：
 * <ul>
 *   <li>读超时/总时长上限：防止对端僵死导致线程永久占用；</li>
 *   <li>前端断开宽限期：最后一个前端断开后等待重连，超时则主动结束订阅释放线程。</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "custom-page.sse")
public class CustomPageSseProperties {

    /**
     * 沙箱 SSE 无数据读超时(秒)。
     * <p>
     * 原实现为 0(无限等待)，对端僵死时线程永不释放。设为有限值后，
     * 超过该时间未收到任何数据即判定流已失效并结束订阅。
     * 取值需大于 AI 生成过程中可能出现的最长静默期。
     */
    private long readTimeoutSeconds = 600L;

    /**
     * 单个订阅的最长持续时间(秒)，防止任务异常时长期占用线程。
     */
    private long maxDurationSeconds = 3600L;

    /**
     * 最后一个前端 SSE 断开后，等待其重连的宽限期(秒)。
     * <p>
     * 宽限期内有新前端接入则继续采集；超时仍无连接则主动结束 Agent 订阅、释放线程。
     * 用于解决"用户关闭页面后后端订阅仍长期挂着"的问题。
     */
    private long detachGracePeriodSeconds = 60L;
}
