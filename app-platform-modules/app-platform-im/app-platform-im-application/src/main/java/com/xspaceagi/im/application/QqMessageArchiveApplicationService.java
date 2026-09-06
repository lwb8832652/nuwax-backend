package com.xspaceagi.im.application;

/**
 * QQ 群消息归档服务。
 */
public interface QqMessageArchiveApplicationService {

    boolean archiveGroupMessage(QqMessageArchiveCommand command);

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class QqMessageArchiveCommand {
        private Long tenantId;
        private Long spaceId;
        private Long userId;
        private Long agentId;
        private String botAppId;
        private String groupOpenid;
        private String userOpenid;
        private String memberOpenid;
        private String qqMsgId;
        private String eventType;
        private String content;
        private Integer messageType;
        private String attachmentsJson;
        private String msgElementsJson;
        private String rawPayload;
        private String timestamp;
    }
}
