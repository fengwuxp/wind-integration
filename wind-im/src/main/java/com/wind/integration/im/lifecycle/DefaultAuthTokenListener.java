package com.wind.integration.im.lifecycle;

import com.corundumstudio.socketio.AuthTokenListener;
import com.corundumstudio.socketio.AuthTokenResult;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIONamespace;
import com.wind.common.exception.AssertUtils;
import com.wind.integration.im.WindImConstants;
import com.wind.security.authentication.AuthenticationTokenCodecService;
import com.wind.security.authentication.WindAuthenticationToken;
import com.wind.security.authentication.WindAuthenticationUser;
import com.wind.websocket.WindWebSocketMetadataNames;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Netty-SocketIO 的认证 Token 处理监听器实现
 * <p>
 * 功能说明：
 * - 当客户端使用 `auth` 字段传入 token 时，此监听器会被调用。
 * - 负责解析并验证 token，并将认证后的用户信息绑定到连接的上下文中（client）。
 * <p>
 * 客户端示例（推荐使用 socket.io v4）：
 * <code>
 * ```
 * const socket = io('http://{serverAddress}/{namespace}', {
 * auth: {
 * token: "Bearer xxxxx.yyyyy.zzzzz" // 注意：此字段会被传递到 AuthTokenListener
 * },
 * query: {
 * sessionId: 'your-session-id',
 * userId: 'optional-user-id',
 * clientDeviceType: 'WEB',
 * clientId: 'client123'
 * },
 * transports: ['websocket']
 * });
 * ```
 *
 * @param requireAuthNamespaces 需要进行 token 验证的命名空间
 * @author wuxp
 * @date 2025-12-12 14:23
 */
@Slf4j
public record DefaultAuthTokenListener(AuthenticationTokenCodecService authenticationTokenCodecService, @NonNull Set<String> requireAuthNamespaces) implements AuthTokenListener {

    private static final AuthTokenResult INVALID_TOKEN = new AuthTokenResult(false, "Invalid token");

    public DefaultAuthTokenListener {
        AssertUtils.notEmpty(requireAuthNamespaces, "argument requireAuthNamespaces must not empty");
    }

    @Override
    public AuthTokenResult getAuthTokenResult(Object authToken, SocketIOClient client) {
        SocketIONamespace namespace = client.getNamespace();
        if (requireAuthNamespaces.contains(namespace.getName())) {
            try {
                // 1. 从连接包中解析传入的 token（需为字符串类型）
                String token = (String) authToken;
                if (StringUtils.hasText(token)) {
                    return INVALID_TOKEN;
                }

                // 2. 调用业务逻辑解析并验证 Token（例如 JWT）
                WindAuthenticationToken parsedToken = authenticationTokenCodecService.parseAndValidateToken(token);
                WindAuthenticationUser user = parsedToken.user();
                AssertUtils.notNull(user, "token is invalid");
                // 3. 验证通过后，将用户信息绑定到 client 对象中，供后续连接和消息逻辑使用
                client.set(WindWebSocketMetadataNames.USER_ID_NAME, user.id());
                client.set(WindImConstants.USERNAME_VARIABLE_NAME, user.username());

                // 4. 返回认证成功
                return AuthTokenResult.AUTH_TOKEN_RESULT_SUCCESS;
            } catch (Exception exception) {
                log.error("验证 Token 失败, message = {}", exception.getMessage(), exception);
                // 5. 验证失败，断开连接
                client.disconnect();
            }
            return INVALID_TOKEN;
        } else {
            //  游客模式
            String userId = client.getHandshakeData().getSingleUrlParam(WindWebSocketMetadataNames.USER_ID_NAME);
            if (!StringUtils.hasText(userId)) {
                log.error("游客模式连接失败，未提供 userId");
                client.disconnect();
                return INVALID_TOKEN;
            }
            client.set(WindWebSocketMetadataNames.USER_ID_NAME, userId);
            client.set(WindImConstants.USERNAME_VARIABLE_NAME, userId);
            log.info("客户端连接成功，使用游客模式 userId = {}", userId);
            return AuthTokenResult.AUTH_TOKEN_RESULT_SUCCESS;
        }
    }
}
