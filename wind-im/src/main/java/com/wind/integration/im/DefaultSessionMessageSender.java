package com.wind.integration.im;

import com.wind.integration.im.spi.WindChatMessageRepository;
import com.wind.integration.im.spi.WindChatMessageRevokeCommandHandler;
import com.wind.websocket.chat.WindChatMessage;
import com.wind.websocket.command.ImmutableMessageRevokeCommand;
import com.wind.websocket.core.WindSessionMessage;
import com.wind.websocket.core.WindSessionMessageActor;
import com.wind.websocket.core.WindSessionMessageSender;
import com.wind.websocket.core.WindSocketSession;
import com.wind.websocket.core.WindSocketSessionRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.Collections;

/**
 * 默认会话消息发送者实现类
 *
 * @author wuxp
 * @date 2025-12-17 13:02
 **/
@Slf4j
@AllArgsConstructor
public class DefaultSessionMessageSender implements WindSessionMessageSender {

    private final WindSocketSessionRegistry socketSessionRegistry;

    private final WindChatMessageRepository chatMessageRepository;

    private final WindChatMessageRevokeCommandHandler messageRevokeHandler;

    @Override
    public void sendMessage(@NonNull WindSessionMessage message) {
        if (message instanceof WindChatMessage chatMessage) {
            chatMessageRepository.save(chatMessage);
        }
        if (message instanceof ImmutableMessageRevokeCommand command){
            messageRevokeHandler.accept(command);
        }

        log.debug("Send chat message from  sessionId = {}, formUserId = {}, messageId = {}", message.getSessionId(), message.getSenderId(), message.getId());
        WindSocketSession session = socketSessionRegistry.getSession(message.getSessionId());
        // 广播消息
        session.broadcast(message, Collections.singleton(message.getSenderId()));
    }

    @Override
    public boolean support(@NonNull WindSessionMessageActor sender) {
        return sender.isUser() || sender.isRobot();
    }
}
