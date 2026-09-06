package com.xspaceagi.im.qq;

import com.xspaceagi.im.application.ImChannelConfigApplicationService;
import com.xspaceagi.im.application.dto.ImChannelConfigDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * QQ 渠道连接控制服务：对外提供启停与状态查询，供前端开关使用。
 *
 * <p>是否允许自动连接取决于后台是否存在「已启用」的 QQ 渠道配置；
 * 手动开关与配置的 enabled 解耦：enabled 只决定应用重启后是否自动拉起连接。</p>
 */
@Slf4j
@Service
public class QqChannelService {

    private static final String NO_ENABLED_CONFIG_ERROR = "未找到已启用的 QQ 渠道配置，请先在页面配置 AppID/AppSecret 并启用";

    private final QqWsClient qqWsClient;
    private final ImChannelConfigApplicationService imChannelConfigApplicationService;

    public QqChannelService(QqWsClient qqWsClient,
                            ImChannelConfigApplicationService imChannelConfigApplicationService) {
        this.qqWsClient = qqWsClient;
        this.imChannelConfigApplicationService = imChannelConfigApplicationService;
    }

    /**
     * 查询当前连接状态。
     */
    public QqChannelStatus getStatus() {
        QqChannelStatus status = qqWsClient.getStatus();
        status.setHasEnabledConfig(hasEnabledConfig());
        return status;
    }

    /**
     * 是否存在已启用的 QQ 渠道配置。
     */
    public boolean hasEnabledConfig() {
        try {
            List<ImChannelConfigDto> configs = imChannelConfigApplicationService.listQqEnabledByPage(0, 50);
            return configs != null && !configs.isEmpty();
        } catch (Exception e) {
            log.warn("[QQ] 读取 QQ 渠道配置失败，按无可用配置处理：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 启动连接。无可用配置时不启动，并在状态里返回原因。
     */
    public QqChannelStatus start() {
        if (!hasEnabledConfig()) {
            QqChannelStatus status = qqWsClient.getStatus();
            status.setHasEnabledConfig(false);
            status.setLastError(NO_ENABLED_CONFIG_ERROR);
            log.info("[QQ] 启动连接被拒绝：{}", NO_ENABLED_CONFIG_ERROR);
            return status;
        }
        qqWsClient.start();
        return getStatus();
    }

    /**
     * 停止连接，并停止指数退避重连。
     */
    public QqChannelStatus stop() {
        qqWsClient.stop();
        return getStatus();
    }

    /**
     * 配置变更后的重连：仅在连接处于运行态时重启，让新配置立即生效。
     */
    public void reloadIfRunning() {
        if (!qqWsClient.isRunning()) {
            log.info("[QQ] 连接未运行，配置变更无需重连");
            return;
        }
        log.info("[QQ] 检测到 QQ 渠道配置变更，重启连接以生效");
        restart();
    }

    /**
     * 渠道配置新增/修改/删除后的统一处理：
     * 仍有可用配置则在运行态下重连让新密钥生效；已无可用配置则直接停止连接。
     */
    public void onConfigChanged() {
        if (hasEnabledConfig()) {
            reloadIfRunning();
            return;
        }
        if (qqWsClient.isRunning()) {
            log.info("[QQ] QQ 渠道配置已禁用或删除，停止连接");
            qqWsClient.stop();
        }
    }

    private void restart() {
        qqWsClient.stop();
        try {
            // 等待旧连接线程与心跳线程退出，避免与新连接抢占资源
            Thread.sleep(500L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        if (!hasEnabledConfig()) {
            log.info("[QQ] 无已启用的 QQ 渠道配置，不再重连");
            return;
        }
        qqWsClient.start();
    }
}
