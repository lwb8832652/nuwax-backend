package com.xspaceagi.im.infra.dao.enitity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * QQ 群消息归档实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("im_qq_message")
public class ImQqMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("_tenant_id")
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
    private Date messageTime;
    private Date created;
    private Date modified;
}
