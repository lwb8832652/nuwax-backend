package com.xspaceagi.im.application;

import com.xspaceagi.agent.core.adapter.dto.AttachmentDto;
import com.xspaceagi.im.application.dto.StreamChunk;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * QQ 群机器人智能体执行服务。
 */
public interface QqAgentApplicationService {

    AgentExecuteResultWithConv executeAgentWithConv(String groupOpenId, String message, List<AttachmentDto> attachments,
                                                    Long tenantId, Long userId, Long agentId,
                                                    String sessionName, String imUserName);

    Flux<StreamChunk> executeAgentStream(String groupOpenId, String message, List<AttachmentDto> attachments,
                                         Long tenantId, Long userId, Long agentId,
                                         String sessionName, String imUserName);

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class AgentExecuteResultWithConv {
        private String text;
        private Long conversationId;
        private Long agentId;
    }
}
