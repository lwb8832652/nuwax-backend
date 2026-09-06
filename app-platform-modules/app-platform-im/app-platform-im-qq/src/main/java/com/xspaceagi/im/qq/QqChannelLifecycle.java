package com.xspaceagi.im.qq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * QQ 渠道生命周期：随后端刷新完成后决定是否建立 WebSocket 连接（phase 设为最大，确保其他 Bean 就绪），
 * 随后端关闭释放。
 *
 * <p>启动行为：仅当后台存在「已启用」的 QQ 渠道配置时自动连接；否则只打日志，
 * 由前端开关手动启动。配置的 enabled 决定重启后是否自动拉起，手动开关与之解耦。</p>
 */
@Slf4j
@Component
public class QqChannelLifecycle implements SmartLifecycle {

    private final QqWsClient qqWsClient;
    private final QqChannelService qqChannelService;
    private volatile boolean running = false;

    public QqChannelLifecycle(QqWsClient qqWsClient, QqChannelService qqChannelService) {
        this.qqWsClient = qqWsClient;
        this.qqChannelService = qqChannelService;
    }

    @Override
    public void start() {
        running = true;
        if (qqChannelService.hasEnabledConfig()) {
            qqWsClient.start();
        } else {
            log.info("[QQ] 未发现已启用的 QQ 渠道配置，跳过自动连接，可在 IM 渠道页面手动开启");
        }
    }

    @Override
    public void stop() {
        running = false;
        qqWsClient.stop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 最后启动，确保应用其他组件已就绪
        return Integer.MAX_VALUE;
    }
}
