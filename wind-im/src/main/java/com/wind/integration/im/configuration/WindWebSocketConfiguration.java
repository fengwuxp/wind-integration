package com.wind.integration.im.configuration;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.wind.integration.im.WindSocketIOServerRunner;
import com.wind.integration.im.connection.DefaultWindSocketConnectionListener;
import com.wind.integration.im.handler.DefaultChatMessageHandler;
import com.wind.integration.im.handler.DefaultRevokeMessageHandler;
import com.wind.integration.im.lifecycle.DefaultSocketioConnectListener;
import com.wind.integration.im.lifecycle.DefaultSocketioDisconnectListener;
import com.wind.integration.im.session.DefaultWindSocketSessionRegistry;
import com.wind.integration.im.spi.WindImChatMessageRepository;
import com.wind.integration.im.spi.WindImSessionService;
import com.wind.integration.im.spi.WindMessageRevokeConsumer;
import com.wind.websocket.core.WindSocketConnectionListener;
import com.wind.websocket.core.WindSocketSessionRegistry;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebSocket 配置类
 *
 * @author wuxp
 * @date 2025-12-12 14:49
 **/
@Configuration
public class WindWebSocketConfiguration {

    /**
     *
     * 创建 WindSocketSessionRegistry Bean
     *
     * @return WindSocketSessionManager 实例
     */
    @Bean
    @ConditionalOnBean(value = {RedissonClient.class, WindImSessionService.class})
    public WindSocketSessionRegistry windSocketSessionRegistry(RedissonClient redissonClient, WindImSessionService windImSessionService) {
        return new DefaultWindSocketSessionRegistry(redissonClient, windImSessionService);
    }

    /**
     * 创建连接事件监听器 Bean。
     *
     * @param socketSessionRegistry 会话管理器
     * @return WindSocketConnectionListener 实例
     */
    @Bean
    @ConditionalOnBean(value = {WindSocketSessionRegistry.class})
    public WindSocketConnectionListener windSocketConnectionListener(WindSocketSessionRegistry socketSessionRegistry) {
        return new DefaultWindSocketConnectionListener(socketSessionRegistry);
    }

    @Bean
    @ConditionalOnBean(WindSocketConnectionListener.class)
    @ConditionalOnMissingBean(ConnectListener.class)
    public DefaultSocketioConnectListener defaultSocketioConnectListener(WindSocketConnectionListener socketConnectionListener) {
        return new DefaultSocketioConnectListener(socketConnectionListener);
    }

    @Bean
    @ConditionalOnBean(WindSocketConnectionListener.class)
    @ConditionalOnMissingBean(DisconnectListener.class)
    public DefaultSocketioDisconnectListener defaultSocketioDisconnectListener(WindSocketConnectionListener socketConnectionListener) {
        return new DefaultSocketioDisconnectListener(socketConnectionListener);
    }

    @Bean
    @ConditionalOnBean(value = {WindSocketSessionRegistry.class, WindImChatMessageRepository.class})
    public DefaultChatMessageHandler defaultChatMessageHandler(WindSocketSessionRegistry sessionRegistry, WindImChatMessageRepository chatMessageRepository) {
        return new DefaultChatMessageHandler(sessionRegistry, chatMessageRepository);
    }

    @Bean
    @ConditionalOnBean(value = {WindSocketSessionRegistry.class, WindImSessionService.class, WindMessageRevokeConsumer.class})
    public DefaultRevokeMessageHandler defaultRevokeMessageHandler(WindSocketSessionRegistry sessionRegistry, WindImSessionService sessionService,
                                                                   WindMessageRevokeConsumer revokeConsumer) {
        return new DefaultRevokeMessageHandler(sessionRegistry, sessionService, revokeConsumer);
    }

    @Bean
    @ConditionalOnBean(value = {WindSocketIOServerFactory.class})
    @ConditionalOnMissingBean(SocketIOServer.class)
    public SocketIOServer socketIOServer(WindSocketIOServerFactory serverFactory) {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setHostname("0.0.0.0");
        config.setPort(8081);
        config.setBossThreads(1);
        config.setWorkerThreads(Runtime.getRuntime().availableProcessors() * 2);
        return serverFactory.factory(config);
    }

    @Bean
    @ConditionalOnBean(value = {SocketIOServer.class})
    public WindSocketIOServerRunner windSocketIOServerRunner(SocketIOServer server) {
        return new WindSocketIOServerRunner(server);
    }
}
