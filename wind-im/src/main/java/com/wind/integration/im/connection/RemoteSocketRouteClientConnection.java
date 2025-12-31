package com.wind.integration.im.connection;

import com.wind.client.rest.HttpTraceRequestInterceptor;
import com.wind.common.exception.BaseException;
import com.wind.common.util.ExecutorServiceUtils;
import com.wind.integration.im.WindImConstants;
import com.wind.integration.im.model.request.RouteMessageRequest;
import com.wind.websocket.WindWebSocketMetadataNames;
import com.wind.websocket.chat.ImmutableChatMessage;
import com.wind.websocket.command.ImmutableMessageRevokeCommand;
import com.wind.websocket.command.ImmutableSessionStatusChangedCommand;
import com.wind.websocket.core.WindSocketRouteClientConnection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 远程 Socket 端点连接实现类
 *
 * @author wuxp
 * @date 2025-12-12 11:00
 **/
@Slf4j
public class RemoteSocketRouteClientConnection implements WindSocketRouteClientConnection {

    private static final ExecutorService EXECUTOR = ExecutorServiceUtils.custom("route-message-sent-", 1, 4, 128);

    // TODO 支持端口号配置
    private static final String REMOTE_NODE_URL_PATTERN = "http://%s:8080%s";

    private final String remoteNodeAddress;

    private final String sessionId;

    private final Map<String, Object> metadata;

    private final RestClient restClient;

    public RemoteSocketRouteClientConnection(@NotBlank String remoteNodeAddress, @NotBlank String sessionId, Map<String, Object> metadata) {
        this.remoteNodeAddress = remoteNodeAddress;
        this.sessionId = sessionId;
        this.metadata = metadata;
        this.restClient = createRestClient();
    }

    private RestClient createRestClient() {
        // 创建请求工厂并设置超时时间
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setConnectionRequestTimeout(1000);
        requestFactory.setReadTimeout(3000);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new HttpTraceRequestInterceptor())
                .build();
    }

    @Override
    public @NotNull String getRemoteNodeAddress() {
        return remoteNodeAddress;
    }

    @Override
    public CompletableFuture<Void> send(Object payload) {
        return CompletableFuture.runAsync(() -> {
            try {
                String url;
                String receiveUserId = (String) metadata.get(WindWebSocketMetadataNames.USER_ID_NAME);
                String receiveClientDeviceType = (String) metadata.get(WindWebSocketMetadataNames.CLIENT_DEVICE_TYPE_NAME);
                RouteMessageRequest<?> request;
                switch (payload) {
                    case ImmutableChatMessage chatMessage -> {
                        url = REMOTE_NODE_URL_PATTERN.formatted(remoteNodeAddress, WindImConstants.ROUTE_CHAT_MESSAGE_PATH);
                        log.debug("发送聊天消息到远程节点 url = {} messageId = {}, sessionId = {}", url, chatMessage.getId(), chatMessage.getSessionId());
                        request = new RouteMessageRequest<>(this.sessionId, chatMessage, receiveUserId, receiveClientDeviceType, this.metadata);
                    }
                    case ImmutableMessageRevokeCommand revoke -> {
                        url = REMOTE_NODE_URL_PATTERN.formatted(remoteNodeAddress, WindImConstants.ROUTE_REVOKE_MESSAGE_PATH);
                        log.debug("发送撤回消息到远程节点 url = {} messageId = {}, sessionId = {}", url, revoke.messageId(), revoke.sessionId());
                        request = new RouteMessageRequest<>(this.sessionId, revoke, receiveUserId, receiveClientDeviceType, this.metadata);
                    }

                    case ImmutableSessionStatusChangedCommand command -> {
                        url = REMOTE_NODE_URL_PATTERN.formatted(remoteNodeAddress, WindImConstants.ROUTE_SESSION_STATUS_MESSAGE_PATH);
                        log.debug("发送会话状态变更消息到远程节点 url = {} messageId = {}, sessionId = {}", url, command.getId(), command.getId());
                        request = new RouteMessageRequest<>(this.sessionId, command, receiveUserId, receiveClientDeviceType, this.metadata);
                    }
                    default -> {
                        log.warn("不支持的消息类型: {}", payload.getClass());
                        throw BaseException.common("不支持的消息类型");
                    }
                }
                restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ex) {
                log.error("发送消息到远程节点失败，remoteNodeAddress = {}, payloadType= {}, message= {}",
                        remoteNodeAddress, payload != null ? payload.getClass().getSimpleName() : "null", ex.getMessage(), ex);
            }
        }, EXECUTOR);
    }

    @Override
    public void close() {
        // 对于远程连接不需要关闭
        log.debug("远程连接关闭 = {}", sessionId);
    }

    @Override
    public boolean isAlive() {
        // 假设总是存活，也可以添加健康检查逻辑
        return true;
    }

    @Override
    public @NotBlank String getId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
