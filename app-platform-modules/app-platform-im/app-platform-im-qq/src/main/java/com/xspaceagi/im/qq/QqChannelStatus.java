package com.xspaceagi.im.qq;

import lombok.Builder;
import lombok.Data;

/**
 * QQ 渠道 WebSocket 连接状态（供前端开关与状态展示使用）。
 */
@Data
@Builder
public class QqChannelStatus {

    /** 客户端是否已启动（start() 已调用且未 stop） */
    private boolean running;

    /** WebSocket 是否已打开 */
    private boolean connected;

    /** 是否已鉴权成功（收到 READY） */
    private boolean authenticated;

    /** 是否存在已启用的 QQ 渠道配置 */
    private boolean hasEnabledConfig;

    /** 当前生效的机器人 AppID */
    private String botAppId;

    /** QQ 网关下发的会话 ID */
    private String sessionId;

    /** 最近一次收到网关消息的时间戳（毫秒） */
    private Long lastEventAt;

    /** 最近一次 WebSocket 打开的时间戳（毫秒） */
    private Long lastConnectedAt;

    /** 自启动以来的重连次数 */
    private Integer reconnectCount;

    /** 最近一次错误信息 */
    private String lastError;

    /** 当前订阅的 intent 位掩码 */
    private Long intents;
}
