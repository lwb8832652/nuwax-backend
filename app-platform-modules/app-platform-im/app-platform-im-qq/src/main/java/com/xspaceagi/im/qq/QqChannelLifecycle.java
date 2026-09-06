package com.xspaceagi.im.qq;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * QQ 渠道生命周期：随后端刷新完成建立 WebSocket 连接（phase 设为最大，确保其他 Bean 就绪），
 * 随后端关闭释放。仅在配置启用且已填密钥时才会真正连网关，否则仅打日志、不影响其他功能。
 */
@Component
public class QqChannelLifecycle implements SmartLifecycle {

    private final QqWsClient qqWsClient;
    private volatile boolean running = false;

    public QqChannelLifecycle(QqWsClient qqWsClient) {
        this.qqWsClient = qqWsClient;
    }

    @Override
    public void start() {
        running = true;
        qqWsClient.start();
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
