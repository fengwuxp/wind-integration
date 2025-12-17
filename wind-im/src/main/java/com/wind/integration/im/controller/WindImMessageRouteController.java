package com.wind.integration.im.controller;


import com.wind.common.exception.BaseException;
import com.wind.integration.im.WindImConstants;
import com.wind.integration.im.connection.LocalSocketClientConnection;
import com.wind.integration.im.model.request.RouteMessageRequest;
import com.wind.server.web.restful.RestfulApiRespFactory;
import com.wind.server.web.supports.ApiResp;
import com.wind.websocket.chat.ImmutableChatMessage;
import com.wind.websocket.command.ImmutableMessageRevokeCommand;
import com.wind.websocket.command.ImmutableSessionStatusChangedCommand;
import com.wind.websocket.core.WindSocketClientClientConnection;
import com.wind.websocket.core.WindSocketSessionRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天消息路由 (内部服务，不要暴露到公网)
 *
 * @author wuxp
 */
@Slf4j
@RestController
@AllArgsConstructor
@Hidden
@Tag(name = "Im Message Route", description = "远程会话消息服务")
public class WindImMessageRouteController {

    private final WindSocketSessionRegistry socketSessionRegistry;

    @PostMapping(WindImConstants.ROUTE_CHAT_MESSAGE_PATH)
    @Operation(summary = "路由聊天消息")
    public ApiResp<Void> routeChatMessage(@RequestBody RouteMessageRequest<ImmutableChatMessage> request) {
        log.debug("route chat message = {}", request);
        route(request);
        return RestfulApiRespFactory.ok();
    }

    @PostMapping(WindImConstants.ROUTE_REVOKE_MESSAGE_PATH)
    @Operation(summary = "路由消息撤回指令")
    public ApiResp<Void> routeRevoked(@RequestBody RouteMessageRequest<ImmutableMessageRevokeCommand> request) {
        log.debug("route revoke message command = {}", request);
        route(request);
        return RestfulApiRespFactory.ok();
    }

    @PostMapping(WindImConstants.ROUTE_SESSION_STATUS_MESSAGE_PATH)
    @Operation(summary = "路由会话状态变更指令")
    public ApiResp<Void> routeSessionStatusChanged(@RequestBody RouteMessageRequest<ImmutableSessionStatusChangedCommand> request) {
        log.debug("route session status changed command = {}", request);
        route(request);
        return RestfulApiRespFactory.ok();
    }

    private void route(RouteMessageRequest<?> request) {
        String userId = request.receiveUserId();
        String clientDeviceType = request.receiveClientDeviceType();
        WindSocketClientClientConnection connection = socketSessionRegistry.getSession(request.sessionId()).getUserConnectionWithDeviceType(userId, clientDeviceType);
        if (connection instanceof LocalSocketClientConnection) {
            connection.send(request.payload());
        } else {
            throw BaseException.common(String.format("route message failure, user connection is not local, userId = %s, clientDeviceType = %s, sessionId = %s",
                    request.receiveUserId(), request.receiveClientDeviceType(), request.sessionId()));
        }
    }
}
