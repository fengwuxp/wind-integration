package com.wind.integration.im.lifecycle;

import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.wind.common.util.IpAddressUtils;
import com.wind.integration.im.WindImConstants;
import com.wind.integration.im.connection.LocalSocketClientConnection;
import com.wind.websocket.WindWebSocketMetadataNames;
import com.wind.websocket.core.WindSocketClientClientConnection;
import com.wind.websocket.core.WindSocketConnectionListener;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.wind.integration.im.WindImConstants.SOCKETIO_CONNECTION_SUCCESS_EVENT;

/**
 * 默认的 SocketIO 连接监听器实现
 *
 * @author wuxp
 * @date 2025-12-12 14:10
 **/
@Slf4j
public record DefaultSocketioConnectListener(String nodeIpAddress, WindSocketConnectionListener socketConnectionListener) implements ConnectListener {

    public DefaultSocketioConnectListener(WindSocketConnectionListener socketConnectionListener) {
        // 当前节点地址的 ip 通过 IpAddressUtils.getLocalIpv4WithCache() 获取
        this(IpAddressUtils.getLocalIpv4WithCache(), socketConnectionListener);
    }

    @Override
    public void onConnect(SocketIOClient client) {
        try {
            HandshakeData handshakeData = client.getHandshakeData();
            // 1. 解析连接参数
            String sessionId = handshakeData.getSingleUrlParam(WindImConstants.SESSION_ID_VARIABLE_NAME);
            String clientId = handshakeData.getSingleUrlParam(WindWebSocketMetadataNames.CLIENT_ID_NAME);
            String deviceType = handshakeData.getSingleUrlParam(WindWebSocketMetadataNames.CLIENT_DEVICE_TYPE_NAME);
            String locale = handshakeData.getSingleUrlParam(WindImConstants.LOCALE_VARIABLE_NAME);
            String namespace = client.getNamespace().getName();

            // 2. 填充客户端元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(WindWebSocketMetadataNames.CLIENT_ID_NAME, clientId);
            metadata.put(WindWebSocketMetadataNames.CLIENT_DEVICE_TYPE_NAME, deviceType);
            metadata.put(WindWebSocketMetadataNames.GMT_CONNECTED_NAME, LocalDateTime.now());
            metadata.put(WindImConstants.SESSION_ID_VARIABLE_NAME, sessionId);
            metadata.put(WindImConstants.LOCALE_VARIABLE_NAME, locale);
            metadata.put(WindImConstants.NAMESPACE_VARIABLE_NAME, namespace);

            // 3. 生成并设置连接 ID
            String connectionId = UUID.randomUUID().toString();
            metadata.put(WindImConstants.CONNECTION_ID_VARIABLE_NAME, connectionId);
            client.set(WindImConstants.CONNECTION_ID_VARIABLE_NAME, connectionId);
            client.set(WindImConstants.SESSION_ID_VARIABLE_NAME, sessionId);
            client.set(WindWebSocketMetadataNames.CLIENT_DEVICE_TYPE_NAME, deviceType);
            client.set(WindImConstants.LOCALE_VARIABLE_NAME, locale);
            log.info("客户端连接 namespace = {}, sessionId = {}, clientId = {}, deviceType = {}, locale = {}", namespace, sessionId, clientId, deviceType, locale);
            // 4. 填充节点地址
            metadata.put(WindImConstants.NODE_IP_ADDRESS_VARIABLE_NAME, nodeIpAddress);
            // 5. 注册连接对象
            WindSocketClientClientConnection connection = new LocalSocketClientConnection(client, connectionId, sessionId, metadata);
            socketConnectionListener.onConnect(connection);
            client.sendEvent(SOCKETIO_CONNECTION_SUCCESS_EVENT, "success");
        } catch (Exception e) {
            log.error("连接异常 message = {} ", e.getMessage(), e);
            client.disconnect();
        }
    }
}
