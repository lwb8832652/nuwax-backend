package com.xspaceagi.im.qq;

import com.xspaceagi.im.application.ImChannelConfigApplicationService;
import com.xspaceagi.im.application.QqAgentApplicationService;
import com.xspaceagi.im.application.QqMessageArchiveApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QQ 官方机器人自动装配（方案②占位版）。
 *
 * <p>配置项前缀 {@code nuwax.im.qq}，示例：
 * <pre>
 * nuwax:
 *   im:
 *     qq:
 *       enabled: true
 *       bot-app-id: 你的AppID
 *       bot-token: 你的AppSecret
 *       intents: 1174405121 # 包含 GROUP_AND_C2C_EVENT(1<<25)，支持群@和全量群消息
 * </pre>
 * 后续方案①阶段会把密钥迁移到 ImChannelConfig(channel="qq").configData，由库表驱动而非 yml。
 * </p>
 */
@Configuration
public class QqBotAutoConfiguration {

    @Value("${nuwax.im.qq.enabled:false}")
    private boolean enabled;

    @Value("${nuwax.im.qq.bot-app-id:}")
    private String botAppId;

    @Value("${nuwax.im.qq.bot-token:}")
    private String botToken;

    @Value("${nuwax.im.qq.token-endpoint:https://api.bot.qq.com/app/getAppAccessToken}")
    private String tokenEndpoint;

    @Value("${nuwax.im.qq.ws-gateway-url:wss://api.bot.qq.com/websocket/}")
    private String wsGatewayUrl;

    @Value("${nuwax.im.qq.intents:1174405121}")
    private long intents;

    @Value("${nuwax.im.qq.heartbeat-interval-ms:30000}")
    private long heartbeatIntervalMs;

    @Value("${nuwax.im.qq.tenant-id:}")
    private Long tenantId;

    @Value("${nuwax.im.qq.user-id:}")
    private Long userId;

    @Value("${nuwax.im.qq.agent-id:}")
    private Long agentId;

    @Bean
    public QqConfig qqConfig() {
        QqConfig config = new QqConfig();
        config.setEnabled(enabled);
        config.setBotAppId(botAppId);
        config.setBotToken(botToken);
        config.setTokenEndpoint(tokenEndpoint);
        config.setWsGatewayUrl(wsGatewayUrl);
        config.setIntents(intents);
        config.setHeartbeatIntervalMs(heartbeatIntervalMs);
        config.setTenantId(tenantId);
        config.setUserId(userId);
        config.setAgentId(agentId);
        return config;
    }

    @Bean
    public QqWsClient qqWsClient(QqConfig qqConfig, QqAgentApplicationService qqAgentApplicationService,
                                 ImChannelConfigApplicationService imChannelConfigApplicationService,
                                 QqMessageArchiveApplicationService qqMessageArchiveApplicationService) {
        return new QqWsClient(qqConfig, qqAgentApplicationService, imChannelConfigApplicationService,
                qqMessageArchiveApplicationService);
    }
}
