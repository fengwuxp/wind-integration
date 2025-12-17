package com.wind.integration.im.handler;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.DataListener;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.common.exception.ExceptionCode;
import com.wind.integration.im.spi.WindChatMessageRevokeHandler;
import com.wind.websocket.command.ImmutableMessageRevokeCommand;
import com.wind.websocket.core.WindSessionMessageSender;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.wind.integration.im.WindImConstants.CHAT_MESSAGE_REVOKE_FAILURE_EVENT;

/**
 * 默认消息撤回处理器
 *
 * @author wuxp
 * @date 2025-12-12 13:56
 **/
@Slf4j
@AllArgsConstructor
public class DefaultRevokeMessageHandler implements DataListener<ImmutableMessageRevokeCommand> {

    private final WindSessionMessageSender sessionMessageSender;

    private final WindChatMessageRevokeHandler messageRevokeHandler;

    @Override
    public void onData(SocketIOClient client, ImmutableMessageRevokeCommand command, AckRequest ackSender) {
        log.debug("Received revoke command from sessionId = {}, messageId = {}, revokeUserId = {} ", command.sessionId(), command.messageId(), command.revokeUserId());
        try {
            // 业务处理
            messageRevokeHandler.accept(command);
            // 发送撤回事件
            sessionMessageSender.sendMessage(command);
        } catch (Exception exception) {
            log.error("消息撤回处理异常: sessionId = {}, messageId = {}, revokeUserId = {}, message = {}", command.sessionId(), command.messageId(), command.revokeUserId(),
                    exception.getMessage(), exception);
            if (exception instanceof BaseException baseException) {
                sendRevokeFailEvent(client, baseException.getCode());
            } else {
                sendRevokeFailEvent(client, DefaultExceptionCode.COMMON_ERROR);
            }
        }
    }

    private static void sendRevokeFailEvent(@NotNull SocketIOClient client, @NotNull ExceptionCode errorCode) {
        client.sendEvent(CHAT_MESSAGE_REVOKE_FAILURE_EVENT, errorCode);
    }

}
