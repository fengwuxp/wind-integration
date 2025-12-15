package com.wind.integration.im.connection;

import com.wind.common.exception.AssertUtils;
import com.wind.websocket.core.WindSocketClientClientConnection;
import com.wind.websocket.core.WindSocketConnectionListener;
import com.wind.websocket.core.WindSocketSession;
import com.wind.websocket.core.WindSocketSessionRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认的 WebSocket 客户端连接监听器实现。
 * <p>
 * 该监听器用于处理客户端连接、断开连接和异常事件，并将连接管理委托给 {@link WindSocketSessionRegistry} 和 {@link WindSocketSession}。
 * <p>
 * 主要职责包括：
 * <ul>
 *   <li>建立连接时创建或获取对应的会话，并将用户连接加入到会话中</li>
 *   <li>连接断开时从会话中移除对应连接</li>
 *   <li>异常发生时记录详细错误日志</li>
 * </ul>
 *
 * @author wuxp
 * @date 2025-12-12 11:28
 **/
@Slf4j
public record DefaultWindSocketConnectionListener(WindSocketSessionRegistry socketSessionRegistry) implements WindSocketConnectionListener {

    @Override
    public void onConnect(WindSocketClientClientConnection connection) {
        String sessionId = connection.getSessionId();
        String userId = connection.getUserId();
        WindSocketSession session = socketSessionRegistry.getSession(sessionId);
        // 检查用户是否在会话内
        AssertUtils.isTrue(session.containsUser(userId), "用户 {} 在会话 {} 中不存在", userId, sessionId);
        // 加入会话
        session.joinUser(userId, connection);
        log.info("Socket 连接加入会话成功: sessionId = {}, userId = {}, clientDeviceType = {} connectionId = {}", sessionId, userId, connection.getClientDeviceType(), connection.getId());
    }

    @Override
    public void onDisconnect(WindSocketClientClientConnection connection) {
        leveConnection(connection);
    }

    @Override
    public void onError(WindSocketClientClientConnection connection, Throwable throwable) {
        log.error("Socket 连接异常: sessionId = {}, userId = {}, connectionId = {}, message = {}", connection.getSessionId(), connection.getUserId(), connection.getId(),
                throwable.getMessage(), throwable);
        leveConnection(connection);
    }

    private void leveConnection(WindSocketClientClientConnection connection) {
        String sessionId = connection.getSessionId();
        if (sessionId != null) {
            boolean exists = socketSessionRegistry.exists(sessionId);
            AssertUtils.isTrue(exists, "用户 {} 在会话 {} 中不存在", connection.getUserId(), sessionId);
            WindSocketSession session = socketSessionRegistry.getSession(sessionId);
            // 断开连接
            session.leaveConnection(connection.getId());
            log.info("Socket 断开连接, 将连接移除会话: sessionId = {}, userId = {}, connectionId = {}", sessionId, connection.getUserId(), connection.getId());
        }
    }

}
