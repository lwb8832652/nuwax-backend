package com.xspaceagi.im.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xspaceagi.im.application.QqMessageArchiveApplicationService;
import com.xspaceagi.im.domain.repository.ImQqMessageRepository;
import com.xspaceagi.im.infra.dao.enitity.ImQqMessage;
import com.xspaceagi.system.spec.common.RequestContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;

@Slf4j
@Service
public class QqMessageArchiveApplicationServiceImpl implements QqMessageArchiveApplicationService {

    @Resource
    private ImQqMessageRepository imQqMessageRepository;

    @Override
    public boolean archiveGroupMessage(QqMessageArchiveCommand command) {
        if (command == null || isBlank(command.getQqMsgId()) || isBlank(command.getGroupOpenid())) {
            return true;
        }
        boolean contextInstalled = false;
        if (RequestContext.get() == null && command.getTenantId() != null) {
            RequestContext<Object> requestContext = new RequestContext<>();
            requestContext.setTenantId(command.getTenantId());
            requestContext.setUserId(command.getUserId());
            RequestContext.set(requestContext);
            contextInstalled = true;
        }
        try {
            LambdaQueryWrapper<ImQqMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ImQqMessage::getQqMsgId, command.getQqMsgId());
            ImQqMessage existing = imQqMessageRepository.getOne(wrapper, false);
            if (existing != null) {
                return false;
            }

            ImQqMessage message = ImQqMessage.builder()
                    .tenantId(command.getTenantId())
                    .spaceId(command.getSpaceId())
                    .userId(command.getUserId())
                    .agentId(command.getAgentId())
                    .botAppId(command.getBotAppId())
                    .groupOpenid(command.getGroupOpenid())
                    .userOpenid(command.getUserOpenid())
                    .memberOpenid(command.getMemberOpenid())
                    .qqMsgId(command.getQqMsgId())
                    .eventType(command.getEventType())
                    .content(command.getContent())
                    .messageType(command.getMessageType())
                    .attachmentsJson(command.getAttachmentsJson())
                    .msgElementsJson(command.getMsgElementsJson())
                    .rawPayload(command.getRawPayload())
                    .messageTime(parseTimestamp(command.getTimestamp()))
                    .build();
            try {
                imQqMessageRepository.save(message);
                return true;
            } catch (DuplicateKeyException e) {
                return false;
            }
        } finally {
            if (contextInstalled) {
                RequestContext.remove();
            }
        }
    }

    private Date parseTimestamp(String timestamp) {
        if (isBlank(timestamp)) {
            return null;
        }
        try {
            return Date.from(OffsetDateTime.parse(timestamp).toInstant());
        } catch (DateTimeParseException e) {
            log.debug("解析 QQ 消息时间失败 timestamp={}", timestamp);
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
