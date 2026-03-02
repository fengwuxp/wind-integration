package com.wind.integration.im.connection;

import com.corundumstudio.socketio.SocketIOClient;
import com.wind.client.rest.HttpTraceRequestInterceptor;
import com.wind.websocket.core.WindSocketClientClientConnection;
import com.wind.websocket.core.WindSocketClientClientConnectionFactory;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 默认的 SocketIO 客户端连接工厂实现
 *
 * @author wuxp
 * @date 2026-03-02 13:02
 **/
@AllArgsConstructor
public class DefaultSocketClientClientConnectionFactory implements WindSocketClientClientConnectionFactory {

    private final RestClient restClient;

    public DefaultSocketClientClientConnectionFactory() {
        this(defaultRestClient());
    }

    @Override
    public @NonNull WindSocketClientClientConnection create(@NonNull Object connectionInstance, @NonNull String sessionId, @NonNull Map<String, Object> metadata) {
        if (connectionInstance instanceof SocketIOClient client) {
            return new LocalSocketClientConnection(client, sessionId, metadata);
        } else if (connectionInstance instanceof String remoteNodeAddress) {
            return new RemoteSocketRouteClientConnection(remoteNodeAddress, sessionId, metadata, restClient);
        } else {
            throw new IllegalArgumentException("connectionInstance must be instance of SocketIOClient or String");
        }
    }


    private static RestClient defaultRestClient() {
        // 创建请求工厂并设置超时时间
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setConnectionRequestTimeout(1000);
        requestFactory.setReadTimeout(3000);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new HttpTraceRequestInterceptor())
                .build();
    }
}
