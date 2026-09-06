package com.xspaceagi.im.web.controller;

import com.xspaceagi.im.qq.QqChannelService;
import com.xspaceagi.im.qq.QqChannelStatus;
import com.xspaceagi.system.spec.annotation.RequireResource;
import com.xspaceagi.system.spec.dto.ReqResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.xspaceagi.system.spec.enums.ResourceEnum.IM_CONFIG_MODIFY;
import static com.xspaceagi.system.spec.enums.ResourceEnum.IM_CONFIG_QUERY_LIST;

/**
 * QQ 渠道 WebSocket 连接控制：前端开关与连接状态展示。
 */
@RestController
@RequestMapping("/api/im-config/qq")
@Slf4j
@Tag(name = "QQ 渠道连接")
public class QqChannelController {

    @Resource
    private QqChannelService qqChannelService;

    @RequireResource(IM_CONFIG_QUERY_LIST)
    @GetMapping("/status")
    @Operation(summary = "查询 QQ 渠道连接状态")
    public ReqResult<QqChannelStatus> status() {
        return ReqResult.success(qqChannelService.getStatus());
    }

    @RequireResource(IM_CONFIG_MODIFY)
    @PostMapping("/start")
    @Operation(summary = "启动 QQ 渠道连接")
    public ReqResult<QqChannelStatus> start() {
        QqChannelStatus status = qqChannelService.start();
        if (!status.isHasEnabledConfig()) {
            return ReqResult.error(status.getLastError());
        }
        return ReqResult.success(status);
    }

    @RequireResource(IM_CONFIG_MODIFY)
    @PostMapping("/stop")
    @Operation(summary = "停止 QQ 渠道连接")
    public ReqResult<QqChannelStatus> stop() {
        return ReqResult.success(qqChannelService.stop());
    }
}
