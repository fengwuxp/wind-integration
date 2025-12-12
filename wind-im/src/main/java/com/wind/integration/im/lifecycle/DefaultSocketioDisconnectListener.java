package com.wind.integration.im.lifecycle;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.wind.integration.im.WindImConstants;
import com.wind.integration.im.connection.LocalSocketClientConnection;
import com.wind.websocket.WindWebSocketMetadataNames;
import com.wind.websocket.core.WindSocketConnectionListener;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * Socket 客户端断开连接监听器。
 * 用于监听客户端断开连接事件，并触发业务层的连接断开处理逻辑。
 * 本类依赖 {@link WindSocketConnectionListener}，在断开连接时通知连接管理组件做相应清理。
 *
 * @author wuxp
 * @date 2025-12-12 13:49
 **/
@Slf4j
public record DefaultSocketioDisconnectListener(WindSocketConnectionListener socketConnectionListener) implements DisconnectListener {

    @Override
    public void onDisconnect(SocketIOClient client) {
        try {
            // 从 client 中提取会话ID、连接ID、用户ID、设备类型等元数据
            String sessionId = client.get(WindImConstants.SESSION_ID_VARIABLE_NAME);
            String connectionId = client.get(WindImConstants.CONNECTION_ID_VARIABLE_NAME);
            String uid = client.get(WindWebSocketMetadataNames.USER_ID_NAME);
            String deviceType = client.get(WindWebSocketMetadataNames.CLIENT_DEVICE_TYPE_NAME);

            log.info("客户端断开连接 sessionId = {} connectionId = {}, userId = {} deviceType = {}", sessionId, connectionId, uid, deviceType);

            // 构建连接元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(WindWebSocketMetadataNames.USER_ID_NAME, uid);
            metadata.put(WindWebSocketMetadataNames.CLIENT_DEVICE_TYPE_NAME, deviceType);

            // 构建断开连接上下文并回调监听器
            socketConnectionListener.onDisconnect(new LocalSocketClientConnection(client, connectionId, sessionId, metadata));
        } catch (Exception exception) {
            log.error("Socket 连接断开异常, message = {}", exception.getMessage(), exception);
        }
    }
}
