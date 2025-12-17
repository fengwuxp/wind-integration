package com.wind.integration.im.handler;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.DataListener;
import com.wind.websocket.chat.ImmutableChatMessage;
import com.wind.websocket.core.WindSessionMessageSender;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天消息监听器
 *
 * @author wuxp
 * @date 2025-12-12 13:01
 **/
@Slf4j
@AllArgsConstructor
public class DefaultChatMessageHandler implements DataListener<ImmutableChatMessage> {

    private final WindSessionMessageSender sessionMessageSender;

    @Override
    public void onData(SocketIOClient client, ImmutableChatMessage message, AckRequest ackSender) {
        try {
            // 保存会话消息
            sessionMessageSender.sendMessage(message);
            // 发送消息投递成功 ACK
            ackSender.sendAckData(message.getId());
        } catch (Exception exception) {
            log.error("聊天消息发送异常: namespace = {}, sessionId = {}, sender = {}, error = {}", client.getNamespace().getName(),
                    message.getSessionId(), message.getSenderId(), exception.getMessage(), exception);
            client.disconnect();
        }
    }
}
