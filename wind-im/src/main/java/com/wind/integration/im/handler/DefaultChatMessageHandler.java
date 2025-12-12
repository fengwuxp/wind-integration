package com.wind.integration.im.handler;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.DataListener;
import com.wind.integration.im.spi.WindImChatMessageRepository;
import com.wind.websocket.chat.ImmutableChatMessage;
import com.wind.websocket.core.WindSocketSession;
import com.wind.websocket.core.WindSocketSessionManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

/**
 * 聊天消息监听器
 *
 * @author wuxp
 * @date 2025-12-12 13:01
 **/
@Slf4j
@AllArgsConstructor
public class DefaultChatMessageHandler implements DataListener<ImmutableChatMessage> {

    private final WindSocketSessionManager sessionManager;

    private final WindImChatMessageRepository chatMessageRepository;

    @Override
    public void onData(SocketIOClient client, ImmutableChatMessage message, AckRequest ackSender) {
        String namespace = client.getNamespace().getName();
        log.debug("Received chat message from namespace = {}, sessionId = {}, formUserId = {}, messageId = {}", namespace, message.getSessionId(), message.getFromUserId(),
                message.getId());
        try {
            // 保存会话消息
            chatMessageRepository.save(namespace, message);
            // 发送消息投递成功 ACK
            ackSender.sendAckData(message.getId());
            WindSocketSession session = sessionManager.getSession(message.getSessionId());
            // 广播消息
            session.broadcast(message, Collections.singleton(message.getFromUserId()));
        } catch (Exception exception) {
            log.error("聊天消息发送一次: namespace = {}, sessionId = {}, sender = {}, error = {}", namespace, message.getSessionId(), message.getFromUserId(),
                    exception.getMessage(), exception);
            client.disconnect();
        }
    }
}
