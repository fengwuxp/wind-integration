package com.wind.integration.im.controller;


import com.wind.common.exception.BaseException;
import com.wind.integration.im.WindImConstants;
import com.wind.integration.im.connection.LocalSocketClientConnection;
import com.wind.integration.im.model.ImmutableMessageRevokeCommand;
import com.wind.integration.im.model.RouteMessageRequest;
import com.wind.server.web.restful.RestfulApiRespFactory;
import com.wind.server.web.supports.ApiResp;
import com.wind.websocket.chat.ImmutableChatMessage;
import com.wind.websocket.core.WindSocketClientClientConnection;
import com.wind.websocket.core.WindSocketSessionManager;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@AllArgsConstructor
@Hidden
@Tag(name = "Im Message Route", description = "远程会话消息服务")
public class WindImMessageRouteController {

    private final WindSocketSessionManager socketSessionManager;

    @PostMapping(WindImConstants.ROUTE_CHAT_MESSAGE_PATH)
    @Operation(summary = "路由聊天消息")
    public ApiResp<Void> chat(@RequestBody RouteMessageRequest<ImmutableChatMessage> request) {
        log.debug("route chat message = {}", request);

        String userId = request.receiveUserId();
        String clientDeviceType = request.receiveClientDeviceType();
        WindSocketClientClientConnection userConnection = socketSessionManager.getSession(request.sessionId()).getUserConnectionWithDeviceType(userId, clientDeviceType);
        if (userConnection instanceof LocalSocketClientConnection) {
            userConnection.send(request.payload());
        } else {
            throw BaseException.common(String.format("route chat message, user connection is not local, userId = %s, clientDeviceType = %s, sessionId = %s",
                    userId, clientDeviceType, request.sessionId()));
        }
        return RestfulApiRespFactory.ok();
    }

    @PostMapping(WindImConstants.ROUTE_REVOKE_MESSAGE_PATH)
    @Operation(summary = "路由消息撤回指令")
    public ApiResp<Void> revoked(@RequestBody RouteMessageRequest<ImmutableMessageRevokeCommand> request) {
        log.debug("route revoke message = {}", request);
        ImmutableMessageRevokeCommand chatMessage = request.payload();
        String userId = request.receiveUserId();
        String clientDeviceType = request.receiveClientDeviceType();

        WindSocketClientClientConnection userConnection = socketSessionManager.getSession(request.sessionId()).getUserConnectionWithDeviceType(userId, clientDeviceType);
        if (userConnection instanceof LocalSocketClientConnection) {
            userConnection.send(chatMessage);
        } else {
            throw BaseException.common(String.format("route revoke message, user connection is not local, userId = %s, clientDeviceType = %s, sessionId = %s",
                    userId, clientDeviceType, request.sessionId()));
        }
        return RestfulApiRespFactory.ok();
    }
}
