package com.wind.integration.im.spi;


import com.wind.websocket.command.ImmutableMessageRevokeCommand;

import java.util.function.Consumer;

/**
 * 消息撤回处理
 *
 * @author wuxp
 * @date 2025-12-12 15:01
 **/
public interface WindMessageRevokeConsumer extends Consumer<ImmutableMessageRevokeCommand> {
}
