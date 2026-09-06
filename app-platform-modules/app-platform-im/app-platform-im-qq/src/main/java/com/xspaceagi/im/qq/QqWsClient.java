package com.xspaceagi.im.qq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xspaceagi.im.application.ImChannelConfigApplicationService;
import com.xspaceagi.im.application.QqAgentApplicationService;
import com.xspaceagi.im.application.QqMessageArchiveApplicationService;
import com.xspaceagi.im.application.dto.ImChannelConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 腾讯官方 QQ 机器人 WebSocket 客户端（v2 协议）。
 *
 * <p>连接流程：换取 appAccessToken → 连网关 → 收到 Hello(op=10) 后启动心跳并按 heartbeat_interval 周期发送
 * Heartbeat(op=1) → 发送 Identify(op=2) 鉴权 → 收到 Dispatch(op=0) 事件。断线后指数退避自动重连。</p>
 *
 * <p>本协议实现仅覆盖「接收端 + 群@事件打印」，发消息与 Agent 分发在后续方案①阶段补齐。</p>
 */
public class QqWsClient {

    private static final Logger log = LoggerFactory.getLogger(QqWsClient.class);

    private static final int OP_DISPATCH = 0;
    private static final int OP_HEARTBEAT = 1;
    private static final int OP_IDENTIFY = 2;
    private static final int OP_RESUME = 6;
    private static final int OP_RECONNECT = 7;
    private static final int OP_INVALID_SESSION = 9;
    private static final int OP_HELLO = 10;
    private static final int OP_HEARTBEAT_ACK = 11;

    private final QqConfig config;
    private final QqAgentApplicationService qqAgentApplicationService;
    private final ImChannelConfigApplicationService imChannelConfigApplicationService;
    private final QqMessageArchiveApplicationService qqMessageArchiveApplicationService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile boolean running = false;
    private WebSocket webSocket;
    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledExecutorService connectScheduler;
    private long lastSeq = 0;
    private String sessionId;

    private String appAccessToken;
    private long appAccessTokenExpireAt = 0;
    private String activeBotAppId;
    private String activeBotToken;
    private final Map<String, Long> handledGroupAtMsgIds = new ConcurrentHashMap<>();

    private CompletableFuture<Void> closeFuture;

    public QqWsClient(QqConfig config, QqAgentApplicationService qqAgentApplicationService,
                      ImChannelConfigApplicationService imChannelConfigApplicationService,
                      QqMessageArchiveApplicationService qqMessageArchiveApplicationService) {
        this.config = config;
        this.qqAgentApplicationService = qqAgentApplicationService;
        this.imChannelConfigApplicationService = imChannelConfigApplicationService;
        this.qqMessageArchiveApplicationService = qqMessageArchiveApplicationService;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        connectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "qq-ws-connect");
            t.setDaemon(true);
            return t;
        });
        connectScheduler.execute(this::connectLoop);
        log.info("[QQ] QQ 渠道已启动，准备连接网关 {}", config.getWsGatewayUrl());
    }

    public void stop() {
        running = false;
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception ignored) {
                // ignore
            }
            webSocket = null;
        }
        shutdownScheduler(heartbeatScheduler);
        shutdownScheduler(connectScheduler);
        log.info("[QQ] QQ 渠道已停止");
    }

    private void connectLoop() {
        int attempt = 0;
        while (running) {
            try {
                connectOnce();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("[QQ] WebSocket 连接异常：{}", e.getMessage());
            }
            if (!running) {
                break;
            }
            long delayMs = Math.min(30_000L, 1_000L * (1L << Math.min(attempt, 5)));
            attempt++;
            log.info("[QQ] {}ms 后尝试重连（第 {} 次）", delayMs, attempt);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void connectOnce() throws Exception {
        ensureAppToken();
        CompletableFuture<Void> closed = new CompletableFuture<>();
        this.closeFuture = closed;
        WsListener listener = new WsListener();
        WebSocket ws = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(config.getWsGatewayUrl()), listener)
                .join();
        this.webSocket = ws;
        // 阻塞直到连接被服务端关闭或 stop() 触发 onClose，随后 connectLoop 视情况重连
        closed.join();
    }

    private void ensureAppToken() {
        ActiveQqBotConfig botConfig = resolveActiveQqBotConfig();
        if (botConfig == null) {
            throw new IllegalStateException("未找到已启用的 QQ 机器人配置，请先在页面配置 AppID/AppSecret");
        }
        if (!botConfig.botAppId.equals(activeBotAppId) || !botConfig.botToken.equals(activeBotToken)) {
            activeBotAppId = botConfig.botAppId;
            activeBotToken = botConfig.botToken;
            appAccessToken = null;
            appAccessTokenExpireAt = 0;
            log.info("[QQ] 已切换到后台配置的 QQ 机器人 appId={}", activeBotAppId);
        }
        if (appAccessToken != null && System.currentTimeMillis() < appAccessTokenExpireAt) {
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.getTokenEndpoint()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JSON.toJSONString(Map.of("appId", activeBotAppId, "clientSecret", activeBotToken))))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JSONObject json = JSON.parseObject(resp.body());
            if (json == null || json.getString("access_token") == null) {
                throw new IllegalStateException("换取 appAccessToken 失败，响应：" + resp.body());
            }
            appAccessToken = json.getString("access_token");
            long expiresIn = json.getLongValue("expires_in");
            // 提前 5 分钟刷新，避免中途过期
            appAccessTokenExpireAt = System.currentTimeMillis() + Math.max(0, expiresIn - 300) * 1000L;
            log.info("[QQ] 已换取 appAccessToken，有效期 {}s", expiresIn);
        } catch (Exception e) {
            throw new IllegalStateException("调用 " + config.getTokenEndpoint() + " 失败：" + e.getMessage(), e);
        }
    }

    private void sendIdentify() {
        if (webSocket == null) {
            return;
        }
        JSONObject d = new JSONObject();
        d.put("token", "QQBot " + appAccessToken);
        d.put("intents", config.getIntents());
        JSONArray shard = new JSONArray();
        shard.add(0);
        shard.add(1);
        d.put("shard", shard);
        JSONObject properties = new JSONObject();
        properties.put("$os", "linux");
        properties.put("$browser", "nuwax");
        properties.put("$device", "nuwax");
        d.put("properties", properties);

        JSONObject identify = new JSONObject();
        identify.put("op", OP_IDENTIFY);
        identify.put("d", d);
        webSocket.sendText(identify.toJSONString(), true);
        log.info("[QQ] 已发送 Identify（intents={}）", config.getIntents());
    }

    private void startHeartbeat(long intervalMs) {
        shutdownScheduler(heartbeatScheduler);
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "qq-ws-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (webSocket == null) {
                return;
            }
            JSONObject hb = new JSONObject();
            hb.put("op", OP_HEARTBEAT);
            hb.put("d", lastSeq == 0 ? null : lastSeq);
            webSocket.sendText(hb.toJSONString(), true);
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void handleEvent(String type, JSONObject data) {
        if (data == null) {
            return;
        }
        if ("READY".equals(type)) {
            sessionId = data.getString("session_id");
            log.info("[QQ] 鉴权成功，session_id={}", sessionId);
            return;
        }
        if ("RESUMED".equals(type)) {
            log.info("[QQ] 连接已恢复（RESUMED）");
            return;
        }
        if ("GROUP_MESSAGE_CREATE".equals(type) || "GROUP_AT_MESSAGE_CREATE".equals(type)) {
            String groupOpenId = data.getString("group_openid");
            JSONObject author = data.getJSONObject("author");
            String userOpenId = author != null ? author.getString("user_openid") : null;
            String memberOpenId = author != null ? author.getString("member_openid") : null;
            String content = data.getString("content");
            String msgId = data.getString("id");
            boolean newMessage = archiveGroupMessage(type, data);
            if ("GROUP_AT_MESSAGE_CREATE".equals(type)) {
                if (!newMessage || !markGroupAtMessageHandling(msgId)) {
                    log.info("[QQ][群@] 重复事件跳过处理 msg_id={}", msgId);
                    return;
                }
                log.info("[QQ][群@] group_openid={}, user_openid={}, member_openid={}, msg_id={}, content={}",
                        groupOpenId, userOpenId, memberOpenId, msgId, content);
                handleGroupAtMessage(groupOpenId, userOpenId, msgId, content);
            } else {
                log.info("[QQ][群消息] group_openid={}, user_openid={}, member_openid={}, msg_id={}",
                        groupOpenId, userOpenId, memberOpenId, msgId);
            }
            return;
        }
        log.info("[QQ] 收到事件 type={}", type);
    }

    private boolean archiveGroupMessage(String eventType, JSONObject data) {
        try {
            ImChannelConfigDto channelConfig = resolveQqConfig();
            JSONObject author = data.getJSONObject("author");
            QqMessageArchiveApplicationService.QqMessageArchiveCommand command = QqMessageArchiveApplicationService.QqMessageArchiveCommand.builder()
                    .tenantId(channelConfig != null ? channelConfig.getTenantId() : config.getTenantId())
                    .spaceId(null)
                    .userId(channelConfig != null ? channelConfig.getUserId() : config.getUserId())
                    .agentId(channelConfig != null ? channelConfig.getAgentId() : config.getAgentId())
                    .botAppId(activeBotAppId)
                    .groupOpenid(data.getString("group_openid"))
                    .userOpenid(author != null ? author.getString("user_openid") : null)
                    .memberOpenid(author != null ? author.getString("member_openid") : null)
                    .qqMsgId(data.getString("id"))
                    .eventType(eventType)
                    .content(data.getString("content"))
                    .messageType(data.getInteger("message_type"))
                    .attachmentsJson(toJsonString(data.get("attachments")))
                    .msgElementsJson(toJsonString(data.get("msg_elements")))
                    .rawPayload(data.toJSONString())
                    .timestamp(data.getString("timestamp"))
                    .build();
            return qqMessageArchiveApplicationService.archiveGroupMessage(command);
        } catch (Exception e) {
            log.warn("[QQ] 归档群消息失败 eventType={}, msg_id={}, error={}", eventType, data.getString("id"), e.getMessage());
            return true;
        }
    }

    private void handleGroupAtMessage(String groupOpenId, String userOpenId, String msgId, String content) {
        if (isBlank(groupOpenId) || isBlank(msgId)) {
            log.warn("[QQ] 群@事件缺少 group_openid 或 msg_id，跳过处理");
            return;
        }
        ImChannelConfigDto channelConfig = resolveQqConfig();
        Long tenantId = channelConfig != null ? channelConfig.getTenantId() : config.getTenantId();
        Long userId = channelConfig != null ? channelConfig.getUserId() : config.getUserId();
        Long agentId = channelConfig != null ? channelConfig.getAgentId() : config.getAgentId();
        if (tenantId == null || userId == null || agentId == null) {
            sendGroupReply(groupOpenId, msgId, "QQ 智能体未配置，请联系管理员配置 QQ 渠道智能体");
            return;
        }
        try {
            QqAgentApplicationService.AgentExecuteResultWithConv result = qqAgentApplicationService.executeAgentWithConv(
                    groupOpenId,
                    content == null ? "" : content.trim(),
                    null,
                    tenantId,
                    userId,
                    agentId,
                    groupOpenId,
                    userOpenId
            );
            String reply = result != null ? result.getText() : null;
            sendGroupReply(groupOpenId, msgId, isBlank(reply) ? "模型未返回内容" : reply);
        } catch (Exception e) {
            log.error("[QQ] 群@消息处理失败 group_openid={}, user_openid={}", groupOpenId, userOpenId, e);
            sendGroupReply(groupOpenId, msgId, "模型执行异常，请稍后再试");
        }
    }

    private ActiveQqBotConfig resolveActiveQqBotConfig() {
        ImChannelConfigDto channelConfig = resolveQqConfig();
        if (channelConfig != null && channelConfig.getQq() != null
                && !isBlank(channelConfig.getQq().getBotAppId())
                && !isBlank(channelConfig.getQq().getBotToken())) {
            return new ActiveQqBotConfig(channelConfig.getQq().getBotAppId(), channelConfig.getQq().getBotToken());
        }
        if (config.isEnabled() && !isBlank(config.getBotAppId()) && !isBlank(config.getBotToken())) {
            return new ActiveQqBotConfig(config.getBotAppId(), config.getBotToken());
        }
        return null;
    }

    private ImChannelConfigDto resolveQqConfig() {
        try {
            List<ImChannelConfigDto> configs = imChannelConfigApplicationService.listQqEnabledByPage(0, 50);
            if (configs == null || configs.isEmpty()) {
                return null;
            }
            if (!isBlank(activeBotAppId)) {
                ImChannelConfigDto matched = configs.stream()
                        .filter(cfg -> cfg.getQq() != null && activeBotAppId.equals(cfg.getQq().getBotAppId()))
                        .findFirst()
                        .orElse(null);
                if (matched != null) {
                    return matched;
                }
            }
            ImChannelConfigDto first = configs.get(0);
            log.info("[QQ] 使用后台 QQ 渠道配置 id={}, agentId={}, userId={}", first.getId(), first.getAgentId(), first.getUserId());
            return first;
        } catch (Exception e) {
            log.warn("[QQ] 读取后台 QQ 渠道配置失败，降级使用本地配置：{}", e.getMessage());
            return null;
        }
    }

    private void sendGroupReply(String groupOpenId, String msgId, String content) {
        try {
            ensureAppToken();
            String url = "https://api.bot.qq.com/v2/groups/" + groupOpenId + "/messages";
            for (int msgSeq = 1; msgSeq <= 5; msgSeq++) {
                JSONObject body = new JSONObject();
                body.put("msg_id", msgId);
                body.put("msg_seq", msgSeq);
                body.put("content", content);
                body.put("msg_type", 0);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "QQBot " + appAccessToken)
                        .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                log.info("[QQ] 发送群回复 msg_seq={}, status={}, body={}", msgSeq, resp.statusCode(), resp.body());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    return;
                }
                if (!isDuplicateMessageResponse(resp.body())) {
                    return;
                }
            }
            log.warn("[QQ] 发送群回复失败，msg_seq=1..5 均被判重 msg_id={}", msgId);
        } catch (Exception e) {
            log.warn("[QQ] 发送群回复失败：{}", e.getMessage());
        }
    }

    private void shutdownScheduler(ScheduledExecutorService scheduler) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private boolean isDuplicateMessageResponse(String body) {
        if (body == null) {
            return false;
        }
        return body.contains("40054005") || body.contains("消息被去重") || body.contains("msgseq");
    }

    private boolean markGroupAtMessageHandling(String msgId) {
        if (isBlank(msgId)) {
            return true;
        }
        long now = System.currentTimeMillis();
        handledGroupAtMsgIds.entrySet().removeIf(entry -> now - entry.getValue() > TimeUnit.HOURS.toMillis(6));
        return handledGroupAtMsgIds.putIfAbsent(msgId, now) == null;
    }

    private String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        return JSON.toJSONString(value);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static final class ActiveQqBotConfig {
        private final String botAppId;
        private final String botToken;

        private ActiveQqBotConfig(String botAppId, String botToken) {
            this.botAppId = botAppId;
            this.botToken = botToken;
        }
    }

    private final class WsListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            QqWsClient.this.webSocket = webSocket;
            log.info("[QQ] WebSocket 已打开，等待 Hello");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);
            try {
                JSONObject msg = JSON.parseObject(data.toString());
                if (msg == null) {
                    return CompletableFuture.completedFuture(null);
                }
                int op = msg.getIntValue("op");
                switch (op) {
                    case OP_HELLO: {
                        long interval = msg.getJSONObject("d").getLongValue("heartbeat_interval");
                        startHeartbeat(interval > 0 ? interval : config.getHeartbeatIntervalMs());
                        sendIdentify();
                        break;
                    }
                    case OP_HEARTBEAT_ACK:
                        break;
                    case OP_DISPATCH: {
                        lastSeq = msg.getLongValue("s");
                        log.debug("[QQ] Dispatch event type={}, seq={}, payload={}", msg.getString("t"), lastSeq, data);
                        handleEvent(msg.getString("t"), msg.getJSONObject("d"));
                        break;
                    }
                    case OP_RECONNECT:
                        log.warn("[QQ] 收到 Reconnect 指令，主动断开重连");
                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "reconnect");
                        break;
                    case OP_INVALID_SESSION:
                        log.warn("[QQ] 收到 InvalidSession，重新鉴权");
                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "invalid-session");
                        break;
                    default:
                        log.debug("[QQ] 未处理 op={}", op);
                }
            } catch (Exception e) {
                log.warn("[QQ] 解析消息失败：{}", e.getMessage());
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("[QQ] WebSocket 错误：{}", error.getMessage());
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("[QQ] WebSocket 已关闭 status={}, reason={}", statusCode, reason);
            if (closeFuture != null && !closeFuture.isDone()) {
                closeFuture.complete(null);
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
