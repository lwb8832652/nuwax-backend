package com.xspaceagi.im.qq;

/**
 * QQ 官方机器人渠道配置（方案②占位版，从 application.yml 读取，后续可迁移到 ImChannelConfig.configData）。
 *
 * <p>腾讯官方机器人走 WebSocket 网关，凭证为开放平台后台的 AppID + AppSecret：
 * AppSecret 用于换取 appAccessToken，WS 鉴权 token 格式为 {@code QQBot <appAccessToken>}。</p>
 */
public class QqConfig {

    /** 是否启用 QQ 渠道（默认关闭，未配置密钥时不影响其他功能） */
    private boolean enabled = false;

    /** QQ 开放平台机器人 AppID */
    private String botAppId;

    /** QQ 开放平台机器人 AppSecret（仅用于换取 appAccessToken，不直接用于 WS 鉴权） */
    private String botToken;

    /** 换取 appAccessToken 的 HTTP 接口基址（v2） */
    private String tokenEndpoint = "https://api.bot.qq.com/app/getAppAccessToken";

    /** WebSocket 网关地址（v2 默认） */
    private String wsGatewayUrl = "wss://api.bot.qq.com/websocket/";

    /**
     * 订阅的 intent 位掩码。GROUP_AND_C2C_EVENT = 1 << 25，覆盖 GROUP_AT_MESSAGE_CREATE 与全量群消息 GROUP_MESSAGE_CREATE。
     * 默认叠加频道@、按钮交互、群管理事件，便于联调期定位。
     */
    private long intents = 1174405121L;

    /** 心跳兜底周期（毫秒），实际以 Hello 包返回的 heartbeat_interval 为准 */
    private long heartbeatIntervalMs = 30000;

    /** QQ 消息临时绑定的租户 ID，后续迁移到 im_channel_config */
    private Long tenantId;

    /** QQ 消息临时绑定的平台用户 ID，后续迁移到 im_channel_config */
    private Long userId;

    /** QQ 消息临时绑定的智能体 ID，后续迁移到 im_channel_config */
    private Long agentId;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBotAppId() {
        return botAppId;
    }

    public void setBotAppId(String botAppId) {
        this.botAppId = botAppId;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getWsGatewayUrl() {
        return wsGatewayUrl;
    }

    public void setWsGatewayUrl(String wsGatewayUrl) {
        this.wsGatewayUrl = wsGatewayUrl;
    }

    public long getIntents() {
        return intents;
    }

    public void setIntents(long intents) {
        this.intents = intents;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }
}
