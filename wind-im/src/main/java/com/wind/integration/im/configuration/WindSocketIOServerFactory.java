package com.wind.integration.im.configuration;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import org.jspecify.annotations.NonNull;

/**
 * SocketIO 服务器工厂
 *
 * @author wuxp
 * @date 2025-12-12 14:57
 **/
public interface WindSocketIOServerFactory {

    /**
     * 创建 SocketIO 服务器
     *
     * @return SocketIO 服务器
     */
    @NonNull
    SocketIOServer factory(@NonNull Configuration config);
}
