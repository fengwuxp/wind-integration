package com.wind.integration.im.lifecycle;

import com.corundumstudio.socketio.AuthTokenListener;
import com.corundumstudio.socketio.AuthTokenResult;
import com.corundumstudio.socketio.SocketIOClient;
import com.wind.integration.im.WindImConstants;
import com.wind.websocket.WindWebSocketMetadataNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 匿名认证 Token 处理监听器实现
 *
 * @author wuxp
 * @date 2025-12-17 14:36
 **/
@Slf4j
public class AnonymousAuthTokenListener implements AuthTokenListener {

    @Override
    public AuthTokenResult getAuthTokenResult(Object authToken, SocketIOClient client) {
        //  匿名模式
        String clientId = client.getHandshakeData().getSingleUrlParam(WindWebSocketMetadataNames.CLIENT_ID_NAME);
        if (!StringUtils.hasText(clientId)) {
            log.error("匿名模式连接失败，未提供 clientId");
            client.disconnect();
            return new AuthTokenResult(false, "Anonymity requires a unique client identifier.");
        }
        client.set(WindWebSocketMetadataNames.USER_ID_NAME, clientId);
        client.set(WindImConstants.USERNAME_VARIABLE_NAME, clientId);
        log.info("客户端连接成功，使用匿名模式 clientId = {}", clientId);
        return AuthTokenResult.AUTH_TOKEN_RESULT_SUCCESS;
    }
}
