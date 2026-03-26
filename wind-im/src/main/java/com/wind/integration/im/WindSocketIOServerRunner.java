package com.wind.integration.im;

import com.corundumstudio.socketio.SocketIOServer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;

/**
 * Netty SocketIO 服务 Runner
 *
 * @author wuxp
 * @date 2025-12-12 14:46
 **/
@Slf4j
@AllArgsConstructor
public class WindSocketIOServerRunner implements CommandLineRunner, DisposableBean {

    private final SocketIOServer ioServer;

    @Override
    public void run(String @NonNull ... args) throws Exception {
        log.info("Start Netty SocketIO Server, Hostname = {}, Port = {}", ioServer.getConfiguration().getHostname(), ioServer.getConfiguration().getPort());
        ioServer.start();
    }

    @Override
    public void destroy() {
        log.info("Stop Netty SocketIO Server");
        ioServer.stop();
    }
}
