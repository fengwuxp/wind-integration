package com.wind.integration.im.spi;


import com.wind.websocket.command.ImmutableMessageRevokeCommand;

import java.util.function.Consumer;

/**
 * 聊天消息撤回指令处理
 *
 * @author wuxp
 * @date 2025-12-12 15:01
 **/
public interface WindChatMessageRevokeCommandHandler extends Consumer<ImmutableMessageRevokeCommand> {
}
