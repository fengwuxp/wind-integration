package com.wind.integration.im.configuration;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.wind.integration.im.DefaultSessionMessageSender;
import com.wind.integration.im.WindSocketIOServerRunner;
import com.wind.integration.im.connection.DefaultSocketClientClientConnectionFactory;
import com.wind.integration.im.connection.DefaultWindSocketConnectionListener;
import com.wind.integration.im.handler.DefaultChatMessageHandler;
import com.wind.integration.im.handler.DefaultRevokeMessageCommandHandler;
import com.wind.integration.im.lifecycle.DefaultSocketioConnectListener;
import com.wind.integration.im.lifecycle.DefaultSocketioDisconnectListener;
import com.wind.integration.im.session.DefaultWindSocketSessionRegistry;
import com.wind.integration.im.spi.WindChatMessageRepository;
import com.wind.integration.im.spi.WindChatMessageRevokeCommandHandler;
import com.wind.integration.im.spi.WindChatSessionService;
import com.wind.websocket.core.CompositeSessionMessageSender;
import com.wind.websocket.core.WindSessionMessageSender;
import com.wind.websocket.core.WindSocketClientClientConnectionFactory;
import com.wind.websocket.core.WindSocketConnectionListener;
import com.wind.websocket.core.WindSocketSessionRegistry;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * WebSocket 配置类
 *
 * @author wuxp
 * @date 2025-12-12 14:49
 **/
@Configuration
public class WindWebSocketConfiguration {


    /**
     * 创建 SocketClientClientConnectionFactory Bean
     *
     * @param restClient RestClient 实例
     * @return SocketClientClientConnectionFactory 实例
     */
    @Bean
    @ConditionalOnBean(RestClient.class)
    @ConditionalOnMissingBean(WindSocketClientClientConnectionFactory.class)
    public WindSocketClientClientConnectionFactory defaultSocketClientClientConnectionFactory(RestClient restClient) {
        return new DefaultSocketClientClientConnectionFactory(restClient);
    }

    /**
     *
     * 创建 WindSocketSessionRegistry Bean
     *
     * @return WindSocketSessionManager 实例
     */
    @Bean
    @ConditionalOnBean(value = {RedissonClient.class, WindChatSessionService.class, WindSocketClientClientConnectionFactory.class})
    public WindSocketSessionRegistry windSocketSessionRegistry(RedissonClient redissonClient, WindChatSessionService windChatSessionService,
                                                               WindSocketClientClientConnectionFactory factory) {
        return new DefaultWindSocketSessionRegistry(redissonClient, windChatSessionService, factory);
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
    @ConditionalOnBean({WindSocketConnectionListener.class, WindSocketClientClientConnectionFactory.class})
    @ConditionalOnMissingBean(ConnectListener.class)
    public DefaultSocketioConnectListener defaultSocketioConnectListener(WindSocketConnectionListener socketConnectionListener, WindSocketClientClientConnectionFactory factory) {
        return new DefaultSocketioConnectListener(socketConnectionListener, factory);
    }

    @Bean
    @ConditionalOnBean({WindSocketConnectionListener.class, WindSocketClientClientConnectionFactory.class})
    @ConditionalOnMissingBean(DisconnectListener.class)
    public DefaultSocketioDisconnectListener defaultSocketioDisconnectListener(WindSocketConnectionListener socketConnectionListener,
                                                                               WindSocketClientClientConnectionFactory factory) {
        return new DefaultSocketioDisconnectListener(socketConnectionListener, factory);
    }

    @Bean
    @ConditionalOnBean(value = {WindSocketSessionRegistry.class, WindChatMessageRepository.class, WindChatMessageRevokeCommandHandler.class})
    public DefaultSessionMessageSender defaultSessionMessageSender(WindSocketSessionRegistry sessionRegistry, WindChatMessageRepository chatMessageRepository,
                                                                   WindChatMessageRevokeCommandHandler handler) {
        return new DefaultSessionMessageSender(sessionRegistry, chatMessageRepository, handler);
    }

    @Bean
    @Primary
    @ConditionalOnBean(WindSessionMessageSender.class)
    public CompositeSessionMessageSender compositeSessionMessageSender(List<WindSessionMessageSender> senders) {
        return new CompositeSessionMessageSender(senders);
    }

    @Bean
    @ConditionalOnBean(WindSessionMessageSender.class)
    public DefaultChatMessageHandler defaultChatMessageHandler(WindSessionMessageSender chatMessageRepository) {
        return new DefaultChatMessageHandler(chatMessageRepository);
    }

    @Bean
    @ConditionalOnBean({WindSessionMessageSender.class})
    public DefaultRevokeMessageCommandHandler defaultRevokeMessageHandler(WindSessionMessageSender sessionMessageSender) {
        return new DefaultRevokeMessageCommandHandler(sessionMessageSender);
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
