package com.wind.integration.im.connection;

import com.corundumstudio.socketio.SocketIOClient;
import com.wind.common.exception.BaseException;
import com.wind.common.util.ExecutorServiceUtils;
import com.wind.integration.im.WindImConstants;
import com.wind.integration.im.model.ImmutableMessageRevokeCommand;
import com.wind.websocket.chat.ImmutableChatMessage;
import com.wind.websocket.core.WindSocketClientClientConnection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 本地 Socket 端点连接实现类
 *
 * @author wuxp
 * @date 2025-12-12 10:36
 **/
@Slf4j
public class LocalSocketClientConnection implements WindSocketClientClientConnection {

    private static final ExecutorService EXECUTOR = ExecutorServiceUtils.custom("message-sent-", 1, 4, 128);

    /**
     * 当前连接所使用的 netty-socketio SocketIOClient
     */
    private final SocketIOClient client;

    /**
     * 当前连接的 ID
     */
    private final String id;

    /**
     * 连接所属的会话 ID
     */
    private final String sessionId;
    /**
     * 连接携带的元数据信息
     */
    private final Map<String, Object> metadata;

    /**
     * 构造函数，初始化连接实例。
     *
     * @param client    netty-socketio 的 SocketIOClient 对象
     * @param sessionId 当前连接所属的会话 ID
     * @param metadata  连接元数据（可选）
     */
    public LocalSocketClientConnection(SocketIOClient client, String id, String sessionId, Map<String, Object> metadata) {
        this.client = client;
        this.id = id;
        this.sessionId = sessionId;
        this.metadata = metadata;
    }

    /**
     * 向客户端发送消息。
     * 当前仅支持发送文本类型的 {@link com.wind.websocket.chat.ChatMessageContentType#TEXT} 消息。
     *
     * @param payload 发送的消息对象（预期为 {@link com.wind.websocket.chat.ImmutableChatMessage}）
     */
    @Override
    public CompletableFuture<Void> send(@NotNull Object payload) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (client == null || !isAlive()) {
                    throw new BaseException("连接已关闭，无法发送消息");
                }
                if (payload instanceof ImmutableChatMessage message) {
                    // 聊天消息发送事件
                    client.sendEvent(WindImConstants.CHAT_MESSAGE_SENT_EVENT, message);
                    // 这里的发送是异步的，没有回调监听发送结果，需自己实现确认机制（如果需要）
                    log.debug("聊天消息发送成功，messageId = {}", message.getId());
                } else if (payload instanceof ImmutableMessageRevokeCommand message) {
                    // 聊天消息发送撤回
                    client.sendEvent(WindImConstants.CHAT_MESSAGE_REVOKE_EVENT, message);
                    // 这里的发送是异步的，没有回调监听发送结果，需自己实现确认机制（如果需要）
                    log.debug("消息撤回请求发送成功, messageId = {}", message.messageId());
                }
            } catch (BaseException e) {
                log.error("发送消息失败，message = {}", e.getMessage(), e);
            }
        }, EXECUTOR);
    }

    @Override
    public void close() {
        if (client != null) {
            client.disconnect();
        }
    }

    /**
     * 判断连接是否仍然活跃。
     *
     * @return true 表示连接存活，false 表示已关闭或异常
     */
    @Override
    public boolean isAlive() {
        return client != null && client.isChannelOpen();
    }

    /**
     * 获取连接的唯一标识
     *
     * @return 连接 ID
     */
    @Override
    public @NotBlank String getId() {
        return id;
    }

    /**
     * 获取该连接所属的会话 ID。
     *
     * @return 会话 ID
     */
    @Override
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取该连接的元数据（如用户信息、设备信息等）。
     *
     * @return 元数据 Map
     */
    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
